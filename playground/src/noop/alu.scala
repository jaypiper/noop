package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._

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
    val sIdle :: sWaitMul :: Nil = Enum(2)
    val state = RegInit(sIdle)

    multiplier.io.a     := io.val1
    multiplier.io.b     := io.val2
    multiplier.io.en    := false.B

    io.valid := false.B
    io.out   := 0.U
    io.ready := state === sIdle
    val alu_val = Mux1H(Seq(
        (io.alu_op === alu_NOP)     -> (0.U(DATA_WIDTH.W)),
        (io.alu_op === alu_MV1)     -> (io.val1),
        (io.alu_op === alu_MV2)     -> (io.val2),
        (io.alu_op === alu_ADD)     -> (io.val1 + io.val2), 
        (io.alu_op === alu_XOR)     -> (io.val1 ^ io.val2),
        (io.alu_op === alu_OR)      -> (io.val1 | io.val2),
        (io.alu_op === alu_AND)     -> (io.val1 & io.val2),
        (io.alu_op === alu_SLL)     -> (io.val1 << io.val2(5,0)),
        (io.alu_op === alu_SRL)     -> Mux(io.alu64, (io.val1 >> io.val2(5,0)), (io.val1(31, 0).asUInt >> io.val2(5,0))),
        (io.alu_op === alu_SRA)     -> Mux(io.alu64, (io.val1.asSInt >> io.val2(5,0)).asUInt, ((io.val1(31, 0).asSInt >> io.val2(5,0)).asUInt)),
        (io.alu_op === alu_SUB)     -> (io.val1 - io.val2),
        // (io.alu_op === alu_NAND)    -> ((~io.val1) & io.val2),
        (io.alu_op === alu_SLT)     -> Mux(io.val1.asSInt < io.val2.asSInt, 1.U, 0.U),
        (io.alu_op === alu_SLTU)    -> Mux(io.val1 < io.val2, 1.U, 0.U)
    ))
    io.out := alu_val
    io.valid := (io.alu_op =/= alu_MUL) && io.en
    switch(state){
        is(sIdle){
            when(io.en && (io.alu_op === alu_MUL)){
                multiplier.io.en := true.B
                state := sWaitMul
            }
        }
        is(sWaitMul){
            io.out := multiplier.io.out
            io.valid := multiplier.io.valid
            when(multiplier.io.valid){
                state := sIdle
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