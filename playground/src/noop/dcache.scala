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

    val data = SyncReadMem(ICACHE_DEPTH, UInt(ICACHE_WIDTH.W))
    val valid_r = RegInit(false.B)
    val offset_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))
    val wmask = if (DCACHE_OFFEST_WIDTH <= DATA_BITS_WIDTH) io.dcPort.wmask
                else io.dcPort.wmask << Cat(io.dcPort.addr(DCACHE_OFFEST_WIDTH-1,DATA_BITS_WIDTH), 0.U(3.W))
    val addr = io.dcPort.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    valid_r := io.dcPort.avalid
    offset_r := io.dcPort.addr(DCACHE_IDX_WIDTH-1, 0)
    when(io.dcPort.wen) {
        data(addr) := (data(addr) & ~wmask) | (io.dcPort.wdata & wmask)
    }

    io.dcPort.rvalid := valid_r
    io.dcPort.ready := true.B
    io.dcPort.rdata := data(addr) >> (Cat(offset_r, 0.U(3.W)))

}