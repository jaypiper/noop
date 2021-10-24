package sim

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import axi.axi_config._

class SimCrossbar extends Module{
    val io = IO(new Bundle{
        val inAxi = new AxiSlave
        val memAxi = new AxiMaster
        val mmioAxi = new AxiMaster
    })
    io.inAxi.init()
    io.memAxi.init()
    io.mmioAxi.init()
    val sIdle :: sWaitMem :: sWaitMmio :: Nil = Enum(3)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            io.inAxi.ra.ready := true.B
            io.inAxi.wa.ready := true.B
            io.inAxi.wr.valid := true.B
            when((io.inAxi.ra.valid && (io.inAxi.ra.bits.addr > "h90000000".U(PADDR_WIDTH.W) || io.inAxi.ra.bits.addr < "h80000000".U(PADDR_WIDTH.W))) || (io.inAxi.wa.valid && (io.inAxi.wa.bits.addr > "h90000000".U(PADDR_WIDTH.W) || io.inAxi.wa.bits.addr < "h80000000".U(PADDR_WIDTH.W)))){
                state := sWaitMmio
                io.inAxi <> io.mmioAxi
            }.elsewhen(io.inAxi.ra.valid || io.inAxi.wa.valid){
                state := sWaitMem
                io.inAxi <> io.memAxi
            }
        }
        is(sWaitMem){
            io.inAxi <> io.memAxi
            when((io.memAxi.rd.valid && io.memAxi.rd.bits.last) || (io.memAxi.wd.valid && io.memAxi.wd.bits.last)){
                state := sIdle
            }
        }
        is(sWaitMmio){
            io.inAxi <> io.mmioAxi
            when((io.mmioAxi.rd.valid && io.mmioAxi.rd.bits.last) || (io.mmioAxi.wd.valid && io.mmioAxi.wd.bits.last)){
                state := sIdle
            }
        }
    }
}