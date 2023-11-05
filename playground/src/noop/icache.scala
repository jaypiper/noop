package noop.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._
import ram._
import axi.axi_config._

class ICache extends Module{
    val io = IO(new Bundle{
        val icPort      = new IcacheRead
        val icMem       = new DcacheRW
    })
    val iram = Module(new IRAM)
    val mem_valid_r = RegInit(false.B)
    val ic_valid_r = RegInit(false.B)
    val offset_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))
    mem_valid_r := io.icMem.avalid
    ic_valid_r := io.icPort.arvalid
    offset_r := Mux(io.icMem.avalid, io.icMem.addr, io.icPort.addr)(ICACHE_OFFEST_WIDTH-1, 0)
    iram.io.cen := io.icPort.arvalid || io.icMem.avalid
    iram.io.wen := io.icMem.wen && io.icMem.avalid
    iram.io.addr := Mux(io.icMem.avalid, io.icMem.addr, io.icPort.addr)(ICACHE_IDX_START, ICACHE_IDX_END)

    iram.io.wdata := io.icMem.wdata << Cat(io.icMem.addr(DCACHE_OFFEST_WIDTH-1, 0), 0.U(3.W))
    iram.io.wmask := io.icMem.wmask << Cat(io.icMem.addr(DCACHE_OFFEST_WIDTH-1, 0), 0.U(3.W))

    io.icMem.rvalid := mem_valid_r
    io.icMem.ready := true.B
    io.icMem.rdata := iram.io.rdata >> Cat(offset_r, 0.U(3.W))
    io.icPort.rvalid := ic_valid_r
    io.icPort.ready := true.B
    io.icPort.inst := iram.io.rdata >> Cat(offset_r, 0.U(3.W))

}