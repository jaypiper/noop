package sim

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import axi.axi_config._
import chisel3.util.experimental.loadMemoryFromFile
import noop.datapath._
import noop.cpu._

class SimMEMIO extends Bundle{
    val memAxi = new AxiSlave
}

class SimMEM extends Module{
    val io = IO(new SimMEMIO)
    val (sIdle :: sWdata :: sWresp :: sRdata:: Nil) = Enum(4)
    val ram = Mem(0x10000000, UInt(8.W))

    val burstLen = RegInit(0.U(8.W))
    val offset  = RegInit(0.U(8.W))

    val waReady = RegInit(false.B)
    val wdReady = RegInit(false.B)
    val waStart = RegInit(0.U(PADDR_WIDTH.W))
    // val wdata   = Cat((0 until 8).reverse.map(i => Mux(io.memAxi.wd.bits.strb(i) === 1.U, io.memAxi.wd.bits.data(8*i+7, 8*i), 0.U(8.W))))
    val waddr   = (waStart + offset * 8.U) & 0xfffffff.U

    val raReady = RegInit(false.B)
    val raStart = RegInit(0.U(PADDR_WIDTH.W))
    val rdValid = RegInit(false.B)
    val rdata   = Cat((0 until 8).reverse.map(i => ram((raStart + offset * 8.U + i.U)&0xfffffff.U)))

    val state = RegInit(sIdle)
    // val addr    = (io.memAxi.wa.bits.addr + offset) & 0xfffffff.U
    val isLast  = (offset >= burstLen)

    switch(state){
        is(sIdle){  //(接收地址信息)
            waReady := true.B
            raReady := true.B
            offset  := 0.U
            when(io.memAxi.wa.valid && waReady){
                state   := sWdata
                waStart   := io.memAxi.wa.bits.addr
                burstLen := io.memAxi.wa.bits.len
                waReady := false.B
                wdReady := true.B
            }
            when(io.memAxi.ra.valid && raReady){
                state   := sRdata
                raStart   := io.memAxi.ra.bits.addr
                burstLen := io.memAxi.ra.bits.len
                raReady := false.B
                rdValid := true.B
            }
        }
        //write
        is(sWdata){
            when(io.memAxi.wd.valid){
                for(i <- 0 until 8){
                        ram(waddr + i.U) := Mux(io.memAxi.wd.bits.strb(i) === 1.U, io.memAxi.wd.bits.data(8*i+7, 8*i), ram(waddr + i.U))
                }
                // printf("[slave-write] addr: %x data: %x\n", waddr, io.memAxi.wd.bits.data)
                offset := offset + 1.U
                when(io.memAxi.wd.bits.last){
                    wdReady := false.B
                    state   := sIdle
                }
            }
        }
        //read
        is(sRdata){
            rdValid := true.B
            when(rdValid && io.memAxi.rd.ready){
                offset  := offset + 1.U
                rdValid := false.B
                when(isLast){
                    state := sIdle
                }
                // printf("[slave-read] addr: %x data: %x\n", raStart + offset * 8.U, rdata)
            }
        }
    }
    // printf("expected: data: %x\n", Cat((0 until 8).reverse.map(i => ram("h106958".U + i.U))))
    io.memAxi.init()
    io.memAxi.wr.valid := true.B
    io.memAxi.wr.bits.resp := RESP_OKAY
    io.memAxi.wa.ready := waReady
    io.memAxi.wd.ready := wdReady
    io.memAxi.ra.ready := raReady
    io.memAxi.rd.valid := rdValid
    io.memAxi.rd.bits.data := rdata
    io.memAxi.rd.bits.last := isLast

    dontTouch(io.memAxi)
}


class SimMEMIO_DP extends Bundle{
    val memAxi = Flipped(new CPU_AXI_IO)
}

class SimMEM_DP extends Module{
    val io = IO(new SimMEMIO_DP)
    val (sIdle :: sWdata :: sWresp :: sRdata:: Nil) = Enum(4)
    val ram = Mem(0x10000000, UInt(8.W))

    val burstLen = RegInit(0.U(8.W))
    val offset  = RegInit(0.U(8.W))

    val waReady = RegInit(false.B)
    val wdReady = RegInit(false.B)
    val waStart = RegInit(0.U(PADDR_WIDTH.W))
    // val wdata   = Cat((0 until 8).reverse.map(i => Mux(io.memAxi.wd.bits.strb(i) === 1.U, io.memAxi.wd.bits.data(8*i+7, 8*i), 0.U(8.W))))
    val waddr   = (waStart + offset * 8.U) & 0xfffffff.U

    val raReady = RegInit(false.B)
    val raStart = RegInit(0.U(PADDR_WIDTH.W))
    val rdValid = RegInit(false.B)
    val rdata   = Cat((0 until 8).reverse.map(i => ram((raStart + offset * 8.U + i.U)&0xfffffff.U)))

    val state = RegInit(sIdle)
    // val addr    = (io.memAxi.wa.bits.addr + offset) & 0xfffffff.U
    val isLast  = (offset >= burstLen)

    switch(state){
        is(sIdle){  //(接收地址信息)
            waReady := true.B
            raReady := true.B
            offset  := 0.U
            when(io.memAxi.awvalid && waReady){
                state   := sWdata
                waStart   := io.memAxi.awaddr
                burstLen := io.memAxi.awlen
                waReady := false.B
                wdReady := true.B
            }
            when(io.memAxi.arvalid && raReady){
                state   := sRdata
                raStart   := io.memAxi.araddr
                burstLen := io.memAxi.arlen
                raReady := false.B
                rdValid := true.B
            }
        }
        //write
        is(sWdata){
            when(io.memAxi.wvalid){
                for(i <- 0 until 8){
                        ram(waddr + i.U) := Mux(io.memAxi.wstrb(i) === 1.U, io.memAxi.wdata(8*i+7, 8*i), ram(waddr + i.U))
                }
                // printf("[slave-write] addr: %x data: %x\n", waddr, io.memAxi.wd.bits.data)
                offset := offset + 1.U
                when(io.memAxi.wlast){
                    wdReady := false.B
                    state   := sIdle
                }
            }
        }
        //read
        is(sRdata){
            rdValid := true.B
            when(rdValid && io.memAxi.rready){
                offset  := offset + 1.U
                rdValid := false.B
                when(isLast){
                    state := sIdle
                }
                // printf("[slave-read] addr: %x data: %x\n", raStart + offset * 8.U, rdata)
            }
        }
    }
    // printf("expected: data: %x\n", Cat((0 until 8).reverse.map(i => ram("h106958".U + i.U))))
    io.memAxi.init_i()
    io.memAxi.bvalid := true.B
    io.memAxi.bresp := RESP_OKAY
    io.memAxi.awready := waReady
    io.memAxi.wready := wdReady
    io.memAxi.arready := raReady
    io.memAxi.rvalid := rdValid
    io.memAxi.rdata := rdata
    io.memAxi.rlast := isLast

    dontTouch(io.memAxi)
}
