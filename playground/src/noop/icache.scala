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
    })
    val data = SyncReadMem(ICACHE_DEPTH, UInt(ICACHE_WIDTH.W))
    // val mask = Fill(INST_WIDTH, 1.U(1.W)) << Cat(io.icPort.addr(ICACHE_OFFEST_WIDTH), 0.U(3.W))
    val valid_r = RegInit(false.B)
    val offset_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))
    valid_r := io.icPort.arvalid
    offset_r := io.icPort.addr(ICACHE_OFFEST_WIDTH-1, 0)
    io.icPort.rvalid := valid_r
    io.icPort.ready := true.B
    io.icPort.inst := data(io.icPort.addr(ICACHE_IDX_START, ICACHE_IDX_END)) >> (Cat(offset_r, 0.U(3.W)))
    // TODO: ">>"  ->  bits selection
    // TODO: if ICACHE_WIDTH < 32
    dontTouch(io.icPort)
}