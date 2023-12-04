package noop.bus

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import noop.cpu._

class CrossBarIO extends Bundle{
    // val icAxi       = new AxiSlave
    val flashAxi    = new AxiSlave
    // val memAxi      = new AxiSlave
    val mmioAxi     = new AxiSlave
    val outAxi      = new AxiMaster
    // val selectMem   = Input(Bool())
}

class CrossBar extends Module{
    val io = IO(new CrossBarIO)
    val (sIdle :: sFlashAddr :: sFlashData :: sMmioAddr :: sMmioData :: Nil) = Enum(5)
    val state = RegInit(sIdle)

    // val selectMem_r = RegInit(false.B)

    // io.icAxi.init()
    io.flashAxi.init()
    // io.memAxi.init()
    io.mmioAxi.init()
    io.outAxi.init()
    io.outAxi.wr.ready := true.B

    // val memTrans = (io.memAxi.ra.valid && io.memAxi.ra.ready) || (io.memAxi.wa.valid && io.memAxi.wa.ready)
    // val memDone = (io.memAxi.rd.valid && io.memAxi.rd.ready && io.memAxi.rd.bits.last) || (io.memAxi.wd.valid && io.memAxi.wd.ready && io.memAxi.wd.bits.last)
    // val instTrans = (io.icAxi.ra.valid && io.icAxi.ra.ready)
    // val instDone = (io.icAxi.rd.valid && io.icAxi.rd.ready && io.icAxi.rd.bits.last)
    val flashTrans = (io.flashAxi.ra.valid && io.flashAxi.ra.ready)
    val flashDone = (io.flashAxi.rd.valid && io.flashAxi.rd.ready && io.flashAxi.rd.bits.last)
    val mmioTrans = ((io.mmioAxi.ra.valid && io.mmioAxi.ra.ready) || (io.mmioAxi.wa.valid && io.mmioAxi.wa.ready))
    val mmioDone = ((io.mmioAxi.rd.valid && io.mmioAxi.rd.ready && io.mmioAxi.rd.bits.last) || (io.mmioAxi.wd.valid && io.mmioAxi.wd.ready && io.mmioAxi.wd.bits.last))

    switch(state){
        is(sIdle){
            when(io.mmioAxi.ra.valid || io.mmioAxi.wa.valid){
                state := sMmioAddr
            }.elsewhen(io.flashAxi.ra.valid){
                state := sFlashAddr
            // }.elsewhen(io.icAxi.ra.valid){
            //     state := sInstAddr
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

}


class CPUCrossBar extends Module{
    val io = IO(new Bundle {
        val in1 = Flipped(new CPU_AXI_IO)
        val in2 = Flipped(new CPU_AXI_IO)
        val out = new CPU_AXI_IO
    })
    val (sIdle :: s1 :: s2 :: Nil) = Enum(3)
    val state = RegInit(sIdle)

    io.in1.init1()
    io.in2.init1()
    io.out.init2()

    val transR1 = (io.in1.arvalid && io.in1.arready)
    val transW1 = (io.in1.awvalid && io.in1.awready)
    val doneR1 = (io.in1.rvalid && io.in1.rready && io.in1.rlast)
    val doneW1 = (io.in1.wvalid && io.in1.wready && io.in1.wlast)
    val doneB1 = io.in1.bready || io.in1.bvalid
    val transR2 = (io.in2.arvalid && io.in2.arready)
    val transW2 = (io.in2.awvalid && io.in2.awready)
    val doneR2 = (io.in2.rvalid && io.in2.rready && io.in2.rlast)
    val doneW2 = (io.in2.wvalid && io.in2.wready && io.in2.wlast)
    val doneB2 = io.in2.bready || io.in2.bvalid

    val dataFinish1 = RegInit(false.B)
    val dataFinish2 = RegInit(false.B)

    switch(state) {
        is(sIdle) {
            dataFinish1 := false.B
            dataFinish2 := false.B
            when(io.in1.arvalid || io.in1.awvalid){
                state := s1
            }.elsewhen(io.in2.arvalid || io.in2.awvalid){
                state := s2
            }
        }
        is(s1) {
            io.out <> io.in1
            when(doneR1 || ((doneW1 || dataFinish1) && doneB1)) {
                state := sIdle
            }
            when(doneW1) {
                dataFinish1 := true.B
            }
        }
        is(s2) {
            io.out <> io.in2
            when(doneR2 || ((doneW2 || dataFinish2) && doneB2)) {
                state := sIdle
            }
            when(doneW2) {
                dataFinish2 := true.B
            }
        }
    }


}