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
    val out     = Output(UInt(DATA_WIDTH.W))
}

class ALU extends Module{
    val io = IO(new ALUIO)

    val alu_val = Mux1H(Seq(
        (io.alu_op === alu_NOP)     -> (0.U(DATA_WIDTH.W)),
        (io.alu_op === alu_MV1)     -> (io.val1),
        (io.alu_op === alu_MV2)     -> (io.val2),
        (io.alu_op === alu_ADD)     -> (io.val1 + io.val2), 
        (io.alu_op === alu_XOR)     -> (io.val1 ^ io.val2),
        (io.alu_op === alu_OR)      -> (io.val1 | io.val2),
        (io.alu_op === alu_AND)     -> (io.val1 & io.val2),
        (io.alu_op === alu_SLL)     -> Mux(io.alu64, io.val1 << io.val2(5,0), io.val1(31, 0).asUInt << io.val2(4,0)),
        (io.alu_op === alu_SRL)     -> Mux(io.alu64, io.val1 >> io.val2(5,0), io.val1(31, 0).asUInt >> io.val2(4,0)),
        (io.alu_op === alu_SRA)     -> Mux(io.alu64, (io.val1.asSInt >> io.val2(5,0)).asUInt, (io.val1(31, 0).asSInt >> io.val2(4,0)).asUInt),
        (io.alu_op === alu_SUB)     -> (io.val1 - io.val2),
        // (io.alu_op === alu_NAND)    -> ((~io.val1) & io.val2),
        (io.alu_op === alu_SLT)     -> Mux(io.val1.asSInt < io.val2.asSInt, 1.U, 0.U),
        (io.alu_op === alu_SLTU)    -> Mux(io.val1 < io.val2, 1.U, 0.U)
    ))
    io.out := alu_val
}
