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
    val dram = Module(new DRAM)
    dram.io.cen := io.dcPort.req.valid
    dram.io.wen := io.dcPort.req.bits.wen
    dram.io.addr := io.dcPort.req.bits.addr(DCACHE_IDX_START, DCACHE_IDX_END)
    dram.io.wdata := MuxLookup(io.dcPort.req.bits.size, 0.U)(List(
        0.U -> Fill(8, io.dcPort.req.bits.wdata(7,0)),
        1.U -> Fill(4, io.dcPort.req.bits.wdata(15,0)),
        2.U -> Fill(2, io.dcPort.req.bits.wdata(31,0)),
        3.U -> io.dcPort.req.bits.wdata
    ))
    dram.io.wmask := MuxLookup(io.dcPort.req.bits.size, 0.U)(List(
       0.U -> VecInit((0 to 7).map(i => (0x1 << i).U))(io.dcPort.req.bits.addr(2,0)), // sb
       1.U -> VecInit((0 to 3).map(_ * 2).map(i => (0x3 << i).U))(io.dcPort.req.bits.addr(2,1)), //sh
       2.U -> VecInit((0 to 1).map(_ * 4).map(i => (0xf << i).U))(io.dcPort.req.bits.addr(2)), // sw
       3.U -> Fill(8, 1.U(1.W))
    ) )

    val s1_valid = RegNext(io.dcPort.req.fire, false.B)
    val s1_offset = RegEnable(io.dcPort.req.bits.addr(DCACHE_OFFEST_WIDTH - 1, 0), io.dcPort.req.fire)
    val s1_size = RegEnable(io.dcPort.req.bits.size, io.dcPort.req.fire)
    io.dcPort.resp.valid := s1_valid
    io.dcPort.req.ready := true.B
    io.dcPort.resp.bits := MuxLookup(s1_size, 0.U)(List(
        0.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 8, UInt(8.W)))(s1_offset),
        1.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 16, UInt(16.W)))(s1_offset(2, 1)),
        2.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 32, UInt(32.W)))(s1_offset(2)),
        3.U -> dram.io.rdata
    ))

}
