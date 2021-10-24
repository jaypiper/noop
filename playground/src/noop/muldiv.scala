package noop.alu
import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._

class MULIO extends Bundle{
    val a     = Input(UInt(DATA_WIDTH.W))
    val b     = Input(UInt(DATA_WIDTH.W))
    val aluop = Input(UInt(ALUOP_WIDTH.W))
    val en    = Input(Bool())
    val ready = Output(Bool())
    val out   = Output(UInt(DATA_WIDTH.W))
    val valid = Output(Bool())
}

class MUL extends Module{
    val io = IO(new MULIO)

    val out_r       = RegInit(0.U(DATA_WIDTH.W))
    val val1        = RegInit(0.U(DATA_WIDTH.W))
    val val2        = RegInit(0.U(DATA_WIDTH.W))
    val aluop_r        = RegInit(0.U(ALUOP_WIDTH.W))
    val valid_r     = RegInit(false.B)
    val sIdle :: sBusy :: sFin :: Nil = Enum(3)
    val state = RegInit(sIdle)
    io.ready := state === sIdle
    io.out := out_r
    io.valid := valid_r
    switch(state){
        is(sIdle){
            valid_r := false.B
            when(io.en){
                state := sBusy
                val1 := io.a
                val2 := io.b
                aluop_r := io.aluop
            }
        }
        is(sBusy){
            out_r := MuxLookup(aluop_r, 0.U(DATA_WIDTH.W), Seq(
            alu_MUL     -> (val1 * val2)(63, 0),
            alu_MULH    -> ((val1.asSInt * val2.asSInt)(127,64)).asUInt,
            alu_MULHU   -> ((val1 * val2)(127, 64)),
            alu_MULHSU  -> (val1.asSInt * val2)(127, 64).asSInt.asUInt,
            ))
            valid_r := true.B
            state := sIdle
        }
    }
}

class DIVIO extends Bundle{
    val alu64 = Input(Bool())
    val a     = Input(UInt(DATA_WIDTH.W))
    val b     = Input(UInt(DATA_WIDTH.W))
    val sign = Input(Bool())
    val en    = Input(Bool())
    val ready = Output(Bool())
    val qua   = Output(UInt(DATA_WIDTH.W))
    val rem   = Output(UInt(DATA_WIDTH.W))
    val valid = Output(Bool())
}

class DIV extends Module{
    val io = IO(new DIVIO)
    val quatient = RegInit(0.U(DATA_WIDTH.W))
    val val1    = RegInit(0.U(128.W))
    val val2    = RegInit(0.U(128.W))
    val qua_sign = RegInit(false.B)
    val rem_sign = RegInit(false.B)
    val iter    = RegInit(0.U(7.W))
    val pre_alu64 = RegInit(false.B)
    val sIdle :: sBusy :: sFin :: Nil = Enum(3)
    val state = RegInit(sIdle)
    io.ready := state === sIdle
    io.qua := 0.U
    io.rem := 0.U
    io.valid := false.B
    switch(state){
        is(sIdle){
            when(io.en){
                state := sBusy
                val1 := Cat(Fill(64, 0.U(1.W)), Mux(io.sign && io.a(63) === 1.U, ~io.a+1.U, io.a))
                val2 := Cat(Mux(io.sign && io.b(63) === 1.U, ~io.b+1.U, io.b), Fill(64, 0.U(1.W)))
                qua_sign := Mux(io.sign, io.a(63) =/= io.b(63) && io.b =/= 0.U, false.B)
                rem_sign := Mux(io.sign, io.a(63) === 1.U, false.B)
                pre_alu64 := io.alu64
                quatient := 0.U
                iter := 0.U
            }
        }
        is(sBusy){
            when(iter <= DATA_WIDTH.U){
                iter := iter + 1.U
                when(val1 >= val2){
                    quatient := Cat(quatient(62, 0), 1.U(1.W))
                    val1 := val1 - val2
                    val2 := val2 >> 1.U
                }.otherwise{
                    quatient := Cat(quatient(62, 0), 0.U(1.W))
                    val2 := val2 >> 1.U
                }
            }.otherwise{
                state := sIdle
                io.valid := true.B
                val sign_qua = Mux(qua_sign, ~quatient + 1.U, quatient)
                val sign_rem = Mux(rem_sign, ~val1(63,0) + 1.U, val1(63,0))
                io.qua   := Mux(pre_alu64, sign_qua, Cat(Fill(32, sign_qua(31)), sign_qua(31,0)))
                io.rem   := Mux(pre_alu64, sign_rem, Cat(Fill(32, sign_rem(31)), sign_rem(31,0)))
            }
        }
    }
}