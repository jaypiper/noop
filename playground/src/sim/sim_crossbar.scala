package sim

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import axi.axi_config._
import noop.cpu._

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


class SimCrossbar_DP extends Module{
    val io = IO(new Bundle{
        val inAxi = Flipped(new CPU_AXI_IO)
        val memAxi = new CPU_AXI_IO
        val mmioAxi = new CPU_AXI_IO
    })
    io.inAxi.init_i()
    io.memAxi.init_o()
    io.mmioAxi.init_o()
    val sIdle :: sWaitMem :: sWaitMmio :: Nil = Enum(3)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            io.inAxi.arready := true.B
            io.inAxi.awready := true.B
            io.inAxi.bvalid := true.B
            when((io.inAxi.arvalid && (io.inAxi.araddr > "h90000000".U(PADDR_WIDTH.W) || io.inAxi.araddr < "h80000000".U(PADDR_WIDTH.W))) || (io.inAxi.awvalid && (io.inAxi.awaddr > "h90000000".U(PADDR_WIDTH.W) || io.inAxi.awaddr < "h80000000".U(PADDR_WIDTH.W)))){
                state := sWaitMmio
                io.inAxi <> io.mmioAxi
            }.elsewhen(io.inAxi.arvalid || io.inAxi.awvalid){
                state := sWaitMem
                io.inAxi <> io.memAxi
            }
        }
        is(sWaitMem){
            io.inAxi <> io.memAxi
            when((io.memAxi.rvalid && io.memAxi.rlast) || (io.memAxi.wvalid && io.memAxi.wlast)){
                state := sIdle
            }
        }
        is(sWaitMmio){
            io.inAxi <> io.mmioAxi
            when((io.mmioAxi.rvalid && io.mmioAxi.rlast) || (io.mmioAxi.wvalid && io.mmioAxi.wlast)){
                state := sIdle
            }
        }
    }
}