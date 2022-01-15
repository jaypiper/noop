package noop.device

import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import noop.cpu._

class VgaCrossbar extends Module{
    val io = IO(new Bundle{
        val master = Flipped(new CPU_AXI_IO)
        val vga_slave = new CPU_AXI_IO
        val map_slave = new CPU_AXI_IO
    })


    io.master.init_i()
    io.vga_slave.init_o()
    io.map_slave.init_o()
    val sIdle :: sWaitVga :: sWaitMap :: Nil = Enum(3)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            // io.master.arready := true.B
            // io.master.awready := true.B
            // io.master.rwvalid := true.B
            when((io.master.arvalid && (io.master.araddr > "h10000000".U(PADDR_WIDTH.W) && io.master.araddr < "h11000000".U(PADDR_WIDTH.W))) || (io.master.awvalid && (io.master.awaddr > "h10000000".U(PADDR_WIDTH.W) && io.master.awaddr < "h11000000".U(PADDR_WIDTH.W)))){
                state := sWaitVga
                io.master <> io.vga_slave
            }.elsewhen(io.master.arvalid || io.master.awvalid){
                state := sWaitMap
                io.master <> io.map_slave
            }
        }
        is(sWaitVga){
            io.master <> io.vga_slave
            when((io.vga_slave.rvalid && io.vga_slave.rlast) || (io.vga_slave.bready && io.vga_slave.bvalid)){
                state := sIdle
            }
        }
        is(sWaitMap){
            io.master <> io.map_slave
            when((io.map_slave.rvalid && io.map_slave.rlast) || (io.map_slave.bready && io.map_slave.bvalid)){
                state := sIdle
            }
        }
    }

}