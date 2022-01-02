package sim

import sim._
import chisel3._
import chisel3.util._
import noop.datapath._
import noop.cpu._
import shared_ram._

class SimCoreIO extends Bundle{

}

class newtop extends Module{
    
    val io = IO(new SimCoreIO)

    val cpu = Module(new CPU)
    val mem = Module(new SimMEM)
    val mmio = Module(new SimMMIO)
    val dma = Module(new SimDma)
    val crossBar = Module(new SimCrossbar)
    val transAxi = Module(new TransAXI)
    val sr_ic0 = Module(new SharedRam)
    val sr_ic1 = Module(new SharedRam)
    val sr_ic2 = Module(new SharedRam)
    val sr_ic3 = Module(new SharedRam)
    val sr_dc0 = Module(new SharedRam)
    val sr_dc1 = Module(new SharedRam)
    val sr_dc2 = Module(new SharedRam)
    val sr_dc3 = Module(new SharedRam)

    val sr_sel      = Module(new RamSelector)
    sr_sel.io.id := 0.U
    sr_sel.io.srio00 <> cpu.io.sram0
    sr_sel.io.srio01 <> cpu.io.sram1
    sr_sel.io.srio02 <> cpu.io.sram2
    sr_sel.io.srio03 <> cpu.io.sram3
    sr_sel.io.srio04 <> cpu.io.sram4
    sr_sel.io.srio05 <> cpu.io.sram5
    sr_sel.io.srio06 <> cpu.io.sram6
    sr_sel.io.srio07 <> cpu.io.sram7

    sr_sel.io.select0 <> sr_ic0.io
    sr_sel.io.select1 <> sr_ic1.io
    sr_sel.io.select2 <> sr_ic2.io
    sr_sel.io.select3 <> sr_ic3.io
    sr_sel.io.select4 <> sr_dc0.io
    sr_sel.io.select5 <> sr_dc1.io
    sr_sel.io.select6 <> sr_dc2.io
    sr_sel.io.select7 <> sr_dc3.io

    cpu.io.master <> transAxi.io.raw_axi
    transAxi.io.bun_axi <> crossBar.io.inAxi
    crossBar.io.mmioAxi <> mmio.io.mmioAxi
    crossBar.io.memAxi <> mem.io.memAxi
    dma.io.dmaAxi <> cpu.io.slave

    val intr_count = RegInit(1.U(20.W))
    // intr_count := intr_count + 1.U
    cpu.io.interrupt := intr_count === 0.U

    dontTouch(cpu.io.interrupt)
    dontTouch(cpu.io.slave)

}