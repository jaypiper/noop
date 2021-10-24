package noop.bus

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._

class CrossBarIO extends Bundle{
    val icAxi       = new AxiSlave
    val flashAxi    = new AxiSlave
    val memAxi      = new AxiSlave
    val mmioAxi     = new AxiSlave
    val outAxi      = new AxiMaster
}

class CrossBar extends Module{
    val io = IO(new CrossBarIO)
    val (sIdle :: sMemAddr :: sInstAddr :: sMemData :: sInstData :: sFlashAddr :: sFlashData :: sMmioAddr :: sMmioData :: Nil) = Enum(9)
    val state = RegInit(sIdle)

    io.icAxi.init()
    io.flashAxi.init()
    io.memAxi.init()
    io.mmioAxi.init()
    io.outAxi.init()
    io.outAxi.wr.ready := true.B

    val memTrans = (io.memAxi.ra.valid && io.memAxi.ra.ready) || (io.memAxi.wa.valid && io.memAxi.wa.ready)
    val memDone = (io.memAxi.rd.valid && io.memAxi.rd.ready && io.memAxi.rd.bits.last) || (io.memAxi.wd.valid && io.memAxi.wd.ready && io.memAxi.wd.bits.last)
    val instTrans = (io.icAxi.ra.valid && io.icAxi.ra.ready)
    val instDone = (io.icAxi.rd.valid && io.icAxi.rd.ready && io.icAxi.rd.bits.last)
    val flashTrans = (io.flashAxi.ra.valid && io.flashAxi.ra.ready)
    val flashDone = (io.flashAxi.rd.valid && io.flashAxi.rd.ready && io.flashAxi.rd.bits.last)
    val mmioTrans = ((io.mmioAxi.ra.valid && io.mmioAxi.ra.ready) || (io.mmioAxi.wa.valid && io.mmioAxi.wa.ready))
    val mmioDone = ((io.mmioAxi.rd.valid && io.mmioAxi.rd.ready && io.mmioAxi.rd.bits.last) || (io.mmioAxi.wd.valid && io.mmioAxi.wd.ready && io.mmioAxi.wd.bits.last))

    switch(state){
        is(sIdle){
            when(io.memAxi.ra.valid || io.memAxi.wa.valid){
                state := sMemAddr
            }.elsewhen(io.mmioAxi.ra.valid || io.mmioAxi.wa.valid){
                state := sMmioAddr
            }.elsewhen(io.flashAxi.ra.valid){
                state := sFlashAddr
            }.elsewhen(io.icAxi.ra.valid){
                state := sInstAddr
            }
        }
        is(sMemAddr){
            io.outAxi <> io.memAxi
            when(memTrans){
                state := sMemData
            }
        }
        is(sMemData){
            io.outAxi <> io.memAxi
            when(memDone){
                state := sIdle
            }
        }
        is(sInstAddr){
            io.outAxi <> io.icAxi
            when(instTrans){
                state := sInstData
            }
        }
        is(sInstData){
            io.outAxi <> io.icAxi
            when(instDone){
                state := sIdle
            }
        }
        is(sFlashAddr){
            io.outAxi <> io.flashAxi
            when(flashTrans){
                state := sFlashData
            }
        }
        is(sFlashData){
            io.outAxi <> io.flashAxi
            when(flashDone){
                state := sIdle
            }
        }
        is(sMmioAddr){
            io.outAxi <> io.mmioAxi
            when(mmioTrans){
                state := sMmioData
            }
        }
        is(sMmioData){
            io.outAxi <> io.mmioAxi
            when(mmioDone){
                state := sIdle
            }
        }
    }

    dontTouch(io.icAxi)
    dontTouch(io.flashAxi)
    dontTouch(io.memAxi)
    dontTouch(io.mmioAxi)
    dontTouch(io.outAxi)
}