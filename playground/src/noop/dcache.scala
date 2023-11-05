package noop.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._
import ram._


class DCache extends Module{
    val io = IO(new Bundle{
        val dcPort        = new DcacheRW
    })
    val valid_r = RegInit(false.B)
    valid_r := io.dcPort.avalid
    val offset_r = RegInit(0.U(DCACHE_OFFEST_WIDTH.W))
    offset_r := io.dcPort.addr(DCACHE_OFFEST_WIDTH-1, 0)
    val dram = Module(new DRAM)
    dram.io.cen := io.dcPort.avalid
    dram.io.wen := io.dcPort.wen
    dram.io.addr := io.dcPort.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    dram.io.wdata := io.dcPort.wdata << Cat(io.dcPort.addr(DCACHE_OFFEST_WIDTH-1, 0), 0.U(3.W))
    dram.io.wmask := io.dcPort.wmask << Cat(io.dcPort.addr(DCACHE_OFFEST_WIDTH-1, 0), 0.U(3.W))
    io.dcPort.rvalid := valid_r
    io.dcPort.ready := true.B
    io.dcPort.rdata := dram.io.rdata >> Cat(offset_r, 0.U(3.W))

}