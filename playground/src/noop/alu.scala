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
        // BitPat(alu_MULH)    -> List(true.B,  true.B,  true.B),
        // BitPat(alu_MULHU)   -> List(true.B,  false.B, false.B),
        // BitPat(alu_MULHSU)  -> List(true.B,  true.B,  false.B)
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

class ALU extends Module{
    val io = IO(new ALUIO)
    val multiplier = Module(new MUL)
    // val divider    = Module(new DIV)
    val sIdle :: sWaitMul :: sWaitDiv :: Nil = Enum(3)
    val pre_aluop = RegInit(0.U(ALUOP_WIDTH.W))
    val state = RegInit(sIdle)

    multiplier.io.a     := io.val1
    multiplier.io.b     := io.val2
    multiplier.io.en    := false.B

    io.valid := false.B
    io.out   := 0.U
    io.ready := state === sIdle
    switch(state){
        is(sIdle){
            when(io.en){
                pre_aluop := io.alu_op
                when(io.alu_op === alu_MUL){
                    multiplier.io.en := true.B
                    state := sWaitMul
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
        // is(sWaitDiv){
        //     when(divider.io.valid){
        //         io.out := Mux(pre_aluop === alu_DIV || pre_aluop === alu_DIVU, divider.io.qua, divider.io.rem)
        //         io.valid := true.B
        //         state := sIdle
        //     }
        // }
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