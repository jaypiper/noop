package noop.simtop

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.tlb._
import noop.cache._
import noop.bus._
import noop.bpu._
import noop.fetch._
import noop.decode._
import noop.execute._
import noop.memory._
import noop.writeback._
import noop.regs._
import noop.clint._
import noop.plic._
import noop.dma._
import noop.cpu._
import sim._

class UARTIO extends Bundle{
    val out_valid = Output(Bool())
    val out_ch = Output(UInt(8.W))
    val in_valid = Output(Bool())
    val in_ch   = Input(UInt(8.W))
}

class PERFINFO extends Bundle{
    val clean = Input(Bool())
    val dump = Input(Bool())
}

class LOGCTRL extends Bundle{
    val log_begin = Input(UInt(64.W))
    val log_end = Input(UInt(64.W))
    val log_level = Input(UInt(64.W))
}

class SimTop extends Module{
    val io = IO(new Bundle{
        // val master = new CPU_AXI_IO
        val logCtrl = new LOGCTRL
        val perfInfo = new PERFINFO
        val uart = new UARTIO
    })
    val cpu = Module(new riscv_cpu_top)
    val crossbar = Module(new SimCrossbar_DP)
    val mem  = Module(new SimMEM_DP)
    cpu.io.master <> crossbar.io.inAxi
    mem.io.memAxi <> crossbar.io.memAxi

    io.uart.in_valid := false.B
    io.uart.out_valid := false.B
    io.uart.out_ch := false.B

    cpu.io.interrupt := false.B

    val waready  = RegInit(false.B)
    val wdready  = RegInit(false.B)
    val waddr   = RegInit(0.U(PADDR_WIDTH.W))
    val wsize   = RegInit(0.U(3.W))
    val wdata   = RegInit(0.U(DATA_WIDTH.W))

    val raready = RegInit(false.B)
    val rdvalid = RegInit(false.B)
    val raddr   = RegInit(0.U(PADDR_WIDTH.W))
    val rdata   = RegInit(0.U(DATA_WIDTH.W))

    crossbar.io.mmioAxi.init_i()

    val (sIdle :: sWdata :: sWresp :: sRdata :: sSDread :: Nil) = Enum(5)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            waready := true.B
            raready := true.B
            when(crossbar.io.mmioAxi.awvalid && waready){
                waddr   := crossbar.io.mmioAxi.awaddr
                wsize   := crossbar.io.mmioAxi.awsize
                waready  := false.B
                state   := sWdata
            }
            when(crossbar.io.mmioAxi.arvalid && raready){
                raddr   := crossbar.io.mmioAxi.araddr
                raready := false.B
                state   := sRdata
                rdata   := 0.U
            }
        }
        //write
        is(sWdata){
            wdready := true.B
            when(crossbar.io.mmioAxi.wvalid){
                wdata   := crossbar.io.mmioAxi.wdata
                when(waddr === "h40600004".U){
                    io.uart.out_valid := true.B
                    io.uart.out_ch := wdata(39, 32)
                    // printf("addr: %x data %x\n", waddr, wdata)
                    printf("%c", wdata(39,32))
                }.otherwise{
                    printf("addr: %x data %x\n", waddr, wdata)
                }
                when(crossbar.io.mmioAxi.wlast){
                    state   := sWresp
                    wdready := false.B
                }
            }
        }
        is(sWresp){
            // io.wr.valid := true.B
            // io.wr.bits.resp := RESP_OKAY  //先不清除吧，一直有效
            state := sIdle
        }
        //read
        is(sRdata){
            rdvalid := true.B
            when(crossbar.io.mmioAxi.rready && rdvalid){
                rdvalid := false.B
                state   := sIdle
            }
        }
    }

    crossbar.io.mmioAxi.rvalid := true.B
    crossbar.io.mmioAxi.bresp := 0.U
    crossbar.io.mmioAxi.awready := waready
    crossbar.io.mmioAxi.wready := wdready
    crossbar.io.mmioAxi.arready := raready
    crossbar.io.mmioAxi.rvalid := rdvalid
    crossbar.io.mmioAxi.rdata := rdata
    crossbar.io.mmioAxi.rlast := true.B

    cpu.io.slave.awvalid   := 0.U
    cpu.io.slave.awaddr    := 0.U
    cpu.io.slave.awid      := 0.U
    cpu.io.slave.awlen     := 0.U
    cpu.io.slave.awsize    := 0.U
    cpu.io.slave.awburst   := 0.U
    cpu.io.slave.wvalid    := 0.U
    cpu.io.slave.wdata     := 0.U
    cpu.io.slave.wstrb     := 0.U
    cpu.io.slave.wlast     := 0.U
    cpu.io.slave.bready    := 0.U
    cpu.io.slave.arvalid   := 0.U
    cpu.io.slave.araddr    := 0.U
    cpu.io.slave.arid      := 0.U
    cpu.io.slave.arlen     := 0.U
    cpu.io.slave.arsize    := 0.U
    cpu.io.slave.arburst   := 0.U
    cpu.io.slave.rready    := 0.U
    cpu.io.slave.awprot    := 0.U
    cpu.io.slave.awuser    := 0.U
    cpu.io.slave.awlock    := 0.U
    cpu.io.slave.awcache   := 0.U
    cpu.io.slave.awqos     := 0.U
    cpu.io.slave.aruser    := 0.U
    cpu.io.slave.arlock    := 0.U
    cpu.io.slave.arcache   := 0.U
    cpu.io.slave.arqos     := 0.U
}
