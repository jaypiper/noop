package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._

class MULIO extends Bundle{
    val a     = Input(UInt(DATA_WIDTH.W))
    val b     = Input(UInt(DATA_WIDTH.W))
    val en    = Input(Bool())
    val out   = Output(UInt(DATA_WIDTH.W))
    val valid = Output(Bool())
}

class MUL extends Module{
    val io = IO(new MULIO)

    val out_r       = RegInit(0.U(32.W))
    val valid_r     = RegInit(false.B)
    io.out := out_r
    io.valid := RegNext(io.en)
    out_r := (io.a(31, 0) * io.b(31, 0))(31, 0)
}
