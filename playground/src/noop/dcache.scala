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
    val size_r = RegInit(0.U(2.W))
    size_r := io.dcPort.size
    offset_r := io.dcPort.addr(DCACHE_OFFEST_WIDTH-1, 0)
    val dram = Module(new DRAM)
    dram.io.cen := io.dcPort.avalid
    dram.io.wen := io.dcPort.wen
    dram.io.addr := io.dcPort.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    dram.io.wdata := MuxLookup(io.dcPort.size, 0.U, List(
        (0.U -> Fill(8, io.dcPort.wdata(7,0))),
        (1.U -> Fill(4, io.dcPort.wdata(15,0))),
        (2.U -> Fill(2, io.dcPort.wdata(31,0))),
        (3.U -> io.dcPort.wdata)
    ))
    dram.io.wmask := MuxLookup(io.dcPort.size, 0.U, List(
       (0.U -> VecInit((0 to 7).map(_ * 8).map(i => (BigInt(0xff) << i).U))(io.dcPort.addr(2,0))), // sb
       (1.U -> VecInit((0 to 3).map(_ * 16).map(i => (BigInt(0xffff) << i).U))(io.dcPort.addr(2,1))), //sh
       (2.U -> VecInit((0 to 1).map(_ * 32).map(i => (BigInt(0xffffffffL) << i).U))(io.dcPort.addr(2))), // sw
       (3.U -> Fill(64, 1.U(1.W)))
    ) )

    io.dcPort.rvalid := valid_r
    io.dcPort.ready := true.B
    io.dcPort.rdata := MuxLookup(size_r, 0.U, List(
        (0.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH/8, UInt(8.W)))(offset_r)),
        (1.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH/16, UInt(16.W)))(offset_r(2,1))),
        (2.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH/32, UInt(32.W)))(offset_r(2))),
        (3.U -> dram.io.rdata)
    ))

}
