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
    // Always accept new request
    io.dcPort.req.ready := true.B

    val s0_ren = io.dcPort.req.valid && !io.dcPort.req.bits.wen
    val s0_req = io.dcPort.req.bits
    val dram = Module(new DRAM)

    // Always respond
    io.dcPort.resp.valid := RegNext(io.dcPort.req.fire, false.B)
    val s1_offset = RegEnable(io.dcPort.req.bits.addr(DCACHE_OFFEST_WIDTH - 1, 0), s0_ren)
    val s1_size = RegEnable(io.dcPort.req.bits.size, s0_ren)
    io.dcPort.resp.bits := MuxLookup(s1_size, 0.U)(List(
        0.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 8, UInt(8.W)))(s1_offset),
        1.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 16, UInt(16.W)))(s1_offset(2, 1)),
        2.U -> dram.io.rdata.asTypeOf(Vec(DCACHE_WIDTH / 32, UInt(32.W)))(s1_offset(2)),
        3.U -> dram.io.rdata
    ))

    // Stores are delayed for one clock cycle.
    // TODO: need to bypass write data??
    val s0_store = io.dcPort.req.fire && io.dcPort.req.bits.wen
    val s1_store_req = RegEnable(s0_req, s0_store)
    val s1_store_cancel = io.dcPort.req_cancel && RegNext(s0_store, false.B)
    val s1_store = RegInit(false.B)
    when (s0_store) {
        s1_store := true.B
    }.elsewhen (s1_store_cancel || !s0_ren) {
        s1_store := false.B
    }

    val s1_do_store = s1_store && !s0_ren && !s1_store_cancel
    dram.io.cen := s0_ren || s1_do_store
    dram.io.wen := s1_do_store && !s0_ren
    dram.io.addr := Mux(s0_ren, s0_req.addr, s1_store_req.addr)(DCACHE_IDX_START, DCACHE_IDX_END)
    dram.io.wdata := MuxLookup(s1_store_req.size, 0.U)(List(
        0.U -> Fill(8, s1_store_req.wdata(7, 0)),
        1.U -> Fill(4, s1_store_req.wdata(15, 0)),
        2.U -> Fill(2, s1_store_req.wdata(31, 0)),
        3.U -> s1_store_req.wdata
    ))
    dram.io.wmask := MuxLookup(s1_store_req.size, 0.U)(List(
        0.U -> VecInit((0 to 7).map(i => (0x1 << i).U))(s1_store_req.addr(2, 0)), // sb
        1.U -> VecInit((0 to 3).map(_ * 2).map(i => (0x3 << i).U))(s1_store_req.addr(2, 1)), //sh
        2.U -> VecInit((0 to 1).map(_ * 4).map(i => (0xf << i).U))(s1_store_req.addr(2)), // sw
        3.U -> Fill(8, 1.U(1.W))
    ))
}
