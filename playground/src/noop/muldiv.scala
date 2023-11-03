package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._

class MULIO extends Bundle{
    val a     = Input(UInt(DATA_WIDTH.W))
    val b     = Input(UInt(DATA_WIDTH.W))
    val en    = Input(Bool())
    val ready = Output(Bool())
    val out   = Output(UInt(DATA_WIDTH.W))
    val valid = Output(Bool())
}

class MUL extends Module{
    val io = IO(new MULIO)

    val out_r       = RegInit(0.U(32.W))
    val val1        = RegInit(0.U(32.W))
    val val2        = RegInit(0.U(32.W))
    val valid_r     = RegInit(false.B)
    val sIdle :: sBusy :: Nil = Enum(2)
    val state = RegInit(sIdle)
    io.ready := state === sIdle
    io.out := out_r
    io.valid := valid_r
    out_r := (val1 * val2)(31, 0)
    switch(state){
        is(sIdle){
            valid_r := false.B
            when(io.en){
                state := sBusy
                val1 := io.a
                val2 := io.b
            }
        }
        is(sBusy){
            valid_r := true.B
            state := sIdle
        }
    }
}
