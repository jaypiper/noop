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
    val dram = Module(new DRAM)
    dram.io.cen := io.dcPort.avalid
    dram.io.wen := io.dcPort.wen
    dram.io.addr := io.dcPort.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    dram.io.wdata := io.dcPort.wdata
    dram.io.wmask := io.dcPort.wmask
    io.dcPort.rvalid := valid_r
    io.dcPort.ready := true.B
    io.dcPort.rdata := dram.io.rdata

    // val data = Mem(DCACHE_DEPTH, UInt(DCACHE_WIDTH.W))
    // val data_r = RegInit(0.U(DCACHE_WIDTH.W))

    // // val data = SyncReadMem(ICACHE_DEPTH, UInt(ICACHE_WIDTH.W))
    // // val data = SyncReadMem(ICACHE_DEPTH, Vec(ICACHE_WIDTH, Bool()))
    // val valid_r = RegInit(false.B)
    // val offset_r = RegInit(0.U(DCACHE_OFFEST_WIDTH.W))
    // val wmask = if (DCACHE_OFFEST_WIDTH <= DATA_BITS_WIDTH) io.dcPort.wmask
    //             else io.dcPort.wmask << Cat(io.dcPort.addr(DCACHE_OFFEST_WIDTH-1,DATA_BITS_WIDTH), 0.U(3.W))
    // val addr = io.dcPort.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    // valid_r := io.dcPort.avalid
    // offset_r := io.dcPort.addr(DCACHE_IDX_WIDTH-1, 0)
    // data_r := data(addr)
    // when(io.dcPort.wen) {
    //     data(addr) := (data(addr) & ~wmask) | (io.dcPort.wdata & wmask)
    //     // data.write(addr, io.dcPort.wdata, wmask)
    //     // data.write(addr, VecInit(io.dcPort.wdata.asBools), VecInit(wmask.asBools))
    // }

    // io.dcPort.rvalid := valid_r
    // io.dcPort.ready := true.B
    // io.dcPort.rdata := data_r

}