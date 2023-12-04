package sim

import chisel3._
import difftest.DifftestModule
import noop.bus.AXIBuffer
import noop.cpu._

class SimTop extends Module{
    val cpu = Module(new CPU)
    val mem = Module(new SimMEM)
    val mmio = Module(new SimMMIO)
    // val dma = Module(new SimDma)
    val crossBar = Module(new SimCrossbar)
    val transAxi = Module(new TransAXI)
    cpu.io.master <> transAxi.io.raw_axi
    AXIBuffer(transAxi.io.bun_axi, 50) <> crossBar.io.inAxi
    crossBar.io.mmioAxi <> mmio.io.mmioAxi
    crossBar.io.memAxi <> mem.io.memAxi
    // dma.io.dmaAxi <> cpu.io.slave

    // val intr_count = RegInit(1.U(20.W))
    // intr_count := intr_count + 1.U
    // cpu.io.interrupt := intr_count === 0.U

    val difftest = DifftestModule.finish("Piper")
    difftest.uart <> mmio.io.uart
}