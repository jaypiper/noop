package noop.device

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.cpu.CPU_AXI_IO
import axi.axi_config._

class Mapping extends Module{
    val io = IO(new Bundle{
        val map_in = Flipped(new CPU_AXI_IO)
        val offset = Input(UInt(32.W))
        val map_out = new CPU_AXI_IO
    })
    
    io.map_out <> io.map_in
    io.map_out.awaddr := io.map_in.awaddr + io.offset
    io.map_out.araddr := io.map_in.araddr + io.offset
}
