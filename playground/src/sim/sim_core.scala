package sim

import sim._
import chisel3._
import chisel3.util._
import noop.datapath._
import noop.cpu._

class SimCoreIO extends Bundle{

}

class newtop extends Module{
    
    val io = IO(new SimCoreIO)

    val cpu = Module(new CPU)
    val mem = Module(new SimMEM)
    val mmio = Module(new SimMMIO)
    val crossBar = Module(new SimCrossbar)
    val transAxi = Module(new TransAXI)
    cpu.io.master <> transAxi.io.raw_axi
    transAxi.io.bun_axi <> crossBar.io.inAxi
    crossBar.io.mmioAxi <> mmio.io.mmioAxi
    crossBar.io.memAxi <> mem.io.memAxi
    cpu.io.interrupt := false.B
    cpu.io.slave := DontCare

    dontTouch(cpu.io.interrupt)
    dontTouch(cpu.io.slave)

}