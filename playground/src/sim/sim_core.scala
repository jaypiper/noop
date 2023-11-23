package sim

import sim._
import chisel3._
import chisel3.util._
import difftest.{DifftestModule, LogCtrlIO, PerfInfoIO, UARTIO}
import noop.datapath._
import noop.cpu._

class SimCoreIO extends Bundle {
    val perfInfo = new PerfInfoIO
    val logCtrl = new LogCtrlIO
    val uart = new UARTIO
}

class SimTop extends Module{
    
    val io = IO(new SimCoreIO)

    io := DontCare

    val cpu = Module(new CPU)
    val mem = Module(new SimMEM)
    val mmio = Module(new SimMMIO)
    // val dma = Module(new SimDma)
    val crossBar = Module(new SimCrossbar)
    val transAxi = Module(new TransAXI)
    cpu.io.master <> transAxi.io.raw_axi
    transAxi.io.bun_axi <> crossBar.io.inAxi
    crossBar.io.mmioAxi <> mmio.io.mmioAxi
    crossBar.io.memAxi <> mem.io.memAxi
    // dma.io.dmaAxi <> cpu.io.slave

    // val intr_count = RegInit(1.U(20.W))
    // intr_count := intr_count + 1.U
    // cpu.io.interrupt := intr_count === 0.U

    io.uart <> mmio.io.uart

    DifftestModule.finish("Piper")
}