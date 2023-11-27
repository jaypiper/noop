package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._

class MULIO extends Bundle{
    val a     = Input(UInt(DATA_WIDTH.W))
    val b     = Input(UInt(DATA_WIDTH.W))
    val en    = Input(Bool())
    val out   = ValidIO(UInt(DATA_WIDTH.W))
}

class MUL extends Module{
    val io = IO(new MULIO)

    io.out.valid := RegNext(io.en)
    val result = (io.a(31, 0) * io.b(31, 0))(31, 0)
    io.out.bits := RegEnable(result, io.en)
}
