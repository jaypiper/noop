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
    val data = Mem(ICACHE_DEPTH, UInt(ICACHE_WIDTH.W))
    val data_r = RegInit(0.U(INST_WIDTH.W))
    val rdata_r = RegInit(0.U(DATA_WIDTH.W))

    // val data = SyncReadMem(ICACHE_DEPTH, UInt(ICACHE_WIDTH.W))
    // val mask = Fill(INST_WIDTH, 1.U(1.W)) << Cat(io.icPort.addr(ICACHE_OFFEST_WIDTH), 0.U(3.W))
    val valid_r = RegInit(false.B)
    val offset_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))
    val ic_valid_r = RegInit(false.B)
    when(io.icMem.wen && io.icMem.avalid) {
        val waddr = io.icMem.addr(ICACHE_IDX_START, ICACHE_IDX_END)
        val wmask = if (ICACHE_OFFEST_WIDTH <= DATA_BITS_WIDTH) io.icMem.wmask
                    else io.icMem.wmask << Cat(io.icMem.addr(ICACHE_OFFEST_WIDTH-1,DATA_BITS_WIDTH), 0.U(3.W))
        data(waddr) := (data(waddr) & ~wmask) | (io.icMem.wdata & wmask)
    }
    valid_r := io.icPort.arvalid
    offset_r := io.icPort.addr(ICACHE_OFFEST_WIDTH-1, 2)
    ic_valid_r := io.icMem.avalid
    data_r := data(io.icPort.addr(ICACHE_IDX_START, ICACHE_IDX_END)).asTypeOf(Vec(ICACHE_WIDTH/32, UInt(32.W)))(io.icPort.addr(ICACHE_OFFEST_WIDTH-1, 2))
    rdata_r := data(io.icMem.addr(ICACHE_IDX_START, ICACHE_IDX_END))
    io.icPort.rvalid := valid_r
    io.icPort.ready := true.B
    io.icPort.inst := data_r
    io.icMem.rdata := rdata_r
    io.icMem.rvalid := ic_valid_r
    io.icMem.ready := true.B
    // TODO: ">>"  ->  bits selection
    // TODO: if ICACHE_WIDTH < 32
    dontTouch(io.icPort)
}