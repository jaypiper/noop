package noop.cache

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._
import noop.param.common.PADDR_WIDTH
import noop.utils.PipelineNext
import ram._

class ICache extends Module{
    val io = IO(new Bundle{
        val icPort      = new IcacheRead
        val icMem       = new DcacheRW
    })

    // stage 1 (corresponding to fetch stage 1)
    val s1_out = Wire(Decoupled()) // for instruction fetch only
    s1_out.valid := io.icPort.arvalid && !io.icMem.req.valid

    // Each RAM has 32 bits. We use two RAMs to form the 64-bit cache data array.
    val ram_en = io.icPort.arvalid || io.icMem.req.valid
    val ram_wen = io.icMem.req.bits.wen && io.icMem.req.valid
    val ram_addr = Mux(io.icMem.req.valid, io.icMem.req.bits.addr, io.icPort.addr)
    val ram_wdata = MuxLookup(io.icMem.req.bits.size, 0.U)(List(
        0.U -> Fill(8, io.icMem.req.bits.wdata(7, 0)),
        1.U -> Fill(4, io.icMem.req.bits.wdata(15, 0)),
        2.U -> Fill(2, io.icMem.req.bits.wdata(31, 0)),
        3.U -> io.icMem.req.bits.wdata
    ))
    val ram_wmask = MuxLookup(io.icMem.req.bits.size, 0.U)(List(
        0.U -> VecInit((0 to 7).map(i => (0x1 << i).U))(io.icMem.req.bits.addr(2, 0)), // sb
        1.U -> VecInit((0 to 3).map(_ * 2).map(i => (0x3 << i).U))(io.icMem.req.bits.addr(2, 1)), //sh
        2.U -> VecInit((0 to 1).map(_ * 4).map(i => (0xf << i).U))(io.icMem.req.bits.addr(2)), // sw
        3.U -> Fill(8, 1.U(1.W))
    ))


    val iram = Seq.fill(2)(Module(new IRAM))
    for ((ram, i) <- iram.zipWithIndex) {
        ram.io.cen := ram_en
        ram.io.wen := ram_wen
        ram.io.addr := ram_addr(ICACHE_IDX_START, ICACHE_IDX_END)
        ram.io.wdata := ram_wdata(i * 32 + 31, i * 32)
        ram.io.wmask := ram_wmask(i * 4 + 3, i * 4)
    }

    // For instruction fetch, we allow the second 32-bit to be read from the next cache line.
    when (s1_out.valid && io.icPort.addr(2)) {
        iram(0).io.addr := io.icPort.addr(ICACHE_IDX_START, ICACHE_IDX_END) + 1.U
    }

    val ram_rdata = VecInit(iram.map(_.io.rdata)).asUInt

    io.icMem.req.ready := true.B
    io.icPort.ready := s1_out.ready && !io.icMem.req.valid

    // stage 2
    val s2_in = PipelineNext(s1_out, false.B)
    val s2_data_valid = RegInit(false.B)
    when (RegNext(s1_out.fire) && !s2_in.ready) {
        s2_data_valid := true.B
    }.elsewhen(s2_in.ready) {
        s2_data_valid := false.B
    }
    val s2_data_r = RegEnable(ram_rdata, RegNext(s1_out.fire) && !s2_in.ready)
    val s2_data = Mux(s2_data_valid, s2_data_r, ram_rdata)

    io.icPort.rvalid := s2_in.valid
    io.icPort.inst := s2_data
    s2_in.ready := io.icPort.rready

    io.icMem.resp.valid := RegNext(io.icMem.req.valid)
    io.icMem.resp.bits := 0.U
}
