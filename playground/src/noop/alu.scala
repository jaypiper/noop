package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.alu.mulDivDecode._

object mulDivDecode{
    val mul_default = List(false.B, false.B, false.B)
    val mul_decode = Array(
                    //     is_hi    a_sign  b_sign
        BitPat(alu_MUL)     -> List(false.B, false.B, false.B),
        BitPat(alu_MULH)    -> List(true.B,  true.B,  true.B),
        BitPat(alu_MULHU)   -> List(true.B,  false.B, false.B),
        BitPat(alu_MULHSU)  -> List(true.B,  true.B,  false.B)
    )
    val div_default = List( false.B, false.B)
    val div_decode = Array(
                    //    qua/rem    sign
        BitPat(alu_DIV)     -> List(true.B,  true.B),
        BitPat(alu_DIVU)    -> List(true.B,  false.B),
        BitPat(alu_REM)     -> List(false.B, true.B),
        BitPat(alu_REMU)    -> List(false.B, false.B)
    )
}

class ALUIO extends Bundle{
    val alu_op  = Input(UInt(ALUOP_WIDTH.W))
    val val1    = Input(UInt(DATA_WIDTH.W))
    val val2    = Input(UInt(DATA_WIDTH.W))
    val alu64   = Input(Bool())
    val en      = Input(Bool())
    val ready   = Output(Bool())
    val out     = Output(UInt(DATA_WIDTH.W))
    val valid   = Output(Bool())
}

class CTZ_Compute extends Module{
    val io = IO(new Bundle{
        val in = Input(UInt(DATA_WIDTH.W))
        val en  = Input(Bool())
        val out = Output(UInt(DATA_WIDTH.W))
        val valid = Output(Bool())
    })

    val sIdle :: sCompute :: Nil = Enum(2)
    val idx = RegInit(0.U(8.W))
    val state = RegInit(sIdle)
    val out_r = RegInit(0.U(DATA_WIDTH.W))
    val valid_r = RegInit(false.B)
    val in_r = RegInit(0.U(DATA_WIDTH.W))
    switch(state){
        is(sIdle){
            valid_r := false.B
            when(io.en){
                state := sCompute
                in_r := io.in
                idx := 0.U
            }
        }
        is(sCompute){
            when(((in_r & (1.U << idx)) =/= 0.U) || idx === 64.U){
                state := sIdle
                valid_r := true.B
                out_r := idx
                // printf("inp: %x out %x\n", in_r, out_r)
            }.otherwise{
                idx := idx + 1.U
            }
        }
    }
    io.valid := valid_r
    io.out := out_r
}

class ALU extends Module{
    val io = IO(new ALUIO)
    val multiplier = Module(new MUL)
    val divider    = Module(new DIV)
    val ctz_comp   = Module(new CTZ_Compute)
    val sIdle :: sWaitMul :: sWaitDiv :: sWaitCtz :: Nil = Enum(4)
    val pre_aluop = RegInit(0.U(ALUOP_WIDTH.W))
    val state = RegInit(sIdle)

    multiplier.io.a     := io.val1
    multiplier.io.b     := io.val2
    multiplier.io.en    := false.B
    multiplier.io.aluop  := io.alu_op
    val div_type = ListLookup(io.alu_op, div_default, div_decode)
    divider.io.alu64    := io.alu64
    divider.io.a        := io.val1
    divider.io.b        := io.val2
    divider.io.sign     := div_type(1)
    divider.io.en       := false.B

    ctz_comp.io.en := false.B
    ctz_comp.io.in := io.val1

    io.valid := false.B
    io.out   := 0.U
    io.ready := state === sIdle
    switch(state){
        is(sIdle){
            when(io.en){
                pre_aluop := io.alu_op
                when(io.alu_op === alu_MUL || io.alu_op === alu_MULH || io.alu_op === alu_MULHU || io.alu_op === alu_MULHSU){
                    multiplier.io.en := true.B
                    state := sWaitMul
                }.elsewhen(io.alu_op === alu_DIV || io.alu_op === alu_DIVU || io.alu_op ===  alu_REM || io.alu_op ===  alu_REMU){
                    divider.io.en       := true.B
                    state := sWaitDiv
                }.elsewhen(io.alu_op === alu_CTZ){
                    ctz_comp.io.en := true.B
                    state := sWaitCtz
                }.otherwise{
                    // val shamt  = Mux(io.alu64, io.val2(5, 0), Cat(0.U(1.W), io.val2(4, 0)))
                    val alu_val = MuxLookup(io.alu_op, 0.U(DATA_WIDTH.W), Seq(
                        alu_NOP     -> (0.U(DATA_WIDTH.W)),
                        alu_MV1     -> (io.val1),
                        alu_MV2     -> (io.val2),
                        alu_ADD     -> (io.val1 + io.val2), 
                        alu_XOR     -> (io.val1 ^ io.val2),
                        alu_OR      -> (io.val1 | io.val2),
                        alu_AND     -> (io.val1 & io.val2),
                        alu_SLL     -> (io.val1 << io.val2(5,0)),
                        alu_SRL     -> Mux(io.alu64, (io.val1 >> io.val2(5,0)), (io.val1(31, 0).asUInt >> io.val2(5,0))),
                        alu_SRA     -> Mux(io.alu64, (io.val1.asSInt >> io.val2(5,0)).asUInt, ((io.val1(31, 0).asSInt >> io.val2(5,0)).asUInt)),
                        alu_SUB     -> (io.val1 - io.val2),
                        alu_SLT     -> Mux(io.val1.asSInt < io.val2.asSInt, 1.U, 0.U),
                        alu_SLTU    -> Mux(io.val1 < io.val2, 1.U, 0.U),
                        alu_NAND    -> ((~io.val1) & io.val2)
                    ))
                    io.out := alu_val
                    io.valid := true.B
                }
            }
        }
        is(sWaitMul){
            when(multiplier.io.valid){
                io.out := multiplier.io.out
                io.valid := true.B
                state := sIdle
            }
        }
        is(sWaitDiv){
            when(divider.io.valid){
                io.out := Mux(pre_aluop === alu_DIV || pre_aluop === alu_DIVU, divider.io.qua, divider.io.rem)
                io.valid := true.B
                state := sIdle
            }
        }
        is(sWaitCtz){
            when(ctz_comp.io.valid){
                io.out := ctz_comp.io.out
                state := sIdle
                io.valid := true.B
            }
        }
    }
}

class BranchALUIO extends Bundle{
    val val1    = Input(UInt(DATA_WIDTH.W))
    val val2    = Input(UInt(DATA_WIDTH.W))
    val brType  = Input(UInt(3.W))
    val is_jmp  = Output(Bool())
}

class BranchALU extends Module{
    val io = IO(new BranchALUIO)
    io.is_jmp := MuxLookup(io.brType, false.B, Seq(
        bEQ     -> (io.val1 === io.val2),
        bNE     -> (io.val1 =/= io.val2),
        bLT     -> (io.val1.asSInt < io.val2.asSInt),
        bGE     -> (io.val1.asSInt >= io.val2.asSInt),
        bLTU    -> (io.val1 < io.val2),
        bGEU    -> (io.val1 >= io.val2)
    ))
}