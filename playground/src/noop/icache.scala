package noop.cache

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._
import noop.utils.PipelineNext
import ram._

class ICache extends Module{
    val io = IO(new Bundle{
        val icPort      = new IcacheRead
        val icMem       = new DcacheRW
    })

    // stage 1 (corresponding to fetch stage 1)
    val s1_out = Wire(Decoupled()) // for instruction fetch only
    s1_out.valid := io.icPort.arvalid && !io.icMem.avalid

    val iram = Module(new IRAM)
    iram.io.cen := io.icPort.arvalid || io.icMem.avalid
    iram.io.wen := io.icMem.wen && io.icMem.avalid
    iram.io.addr := Mux(io.icMem.avalid, io.icMem.addr, io.icPort.addr)(ICACHE_IDX_START, ICACHE_IDX_END)

    iram.io.wdata := MuxLookup(io.icMem.size, 0.U)(List(
        0.U -> Fill(8, io.icMem.wdata(7, 0)),
        1.U -> Fill(4, io.icMem.wdata(15, 0)),
        2.U -> Fill(2, io.icMem.wdata(31, 0)),
        3.U -> io.icMem.wdata
    ))
    iram.io.wmask := MuxLookup(io.icMem.size, 0.U)(List(
        0.U -> VecInit((0 to 7).map(i => (0x1 << i).U))(io.icMem.addr(2, 0)), // sb
        1.U -> VecInit((0 to 3).map(_ * 2).map(i => (0x3 << i).U))(io.icMem.addr(2, 1)), //sh
        2.U -> VecInit((0 to 1).map(_ * 4).map(i => (0xf << i).U))(io.icMem.addr(2)), // sw
        3.U -> Fill(8, 1.U(1.W))
    ))

    io.icMem.ready := true.B
    io.icPort.ready := s1_out.ready && !io.icMem.avalid

    // stage 2
    val s2_in = PipelineNext(s1_out, false.B)
    val s2_offset = Reg(UInt(ICACHE_OFFEST_WIDTH.W))
    when (io.icMem.avalid) {
        s2_offset := io.icMem.addr
    }.elsewhen (s1_out.fire) {
        s2_offset := io.icPort.addr
    }
    val s2_size = RegEnable(io.icMem.size, s1_out.fire)
    val s2_data_valid = RegInit(false.B)
    when (s2_in.valid && !s2_in.ready) {
        s2_data_valid := true.B
    }.elsewhen(s2_in.ready) {
        s2_data_valid := false.B
    }
    val s2_data_r = RegEnable(io.icMem.avalid, s2_in.valid && !s2_in.ready)
    val s2_data = Mux(s2_data_valid, s2_data_r, iram.io.rdata)

    val s2_out = Wire(Decoupled()) // for instruction fetch only
    s2_out.valid := s2_in.valid
    s2_in.ready := !s2_in.valid || s2_out.ready

    io.icMem.rvalid := RegNext(io.icMem.avalid)
    io.icMem.rdata := 0.U

    // stage 3
    val s3_in = PipelineNext(s2_out, false.B)
    val s3_data = RegEnable(s2_data, s2_out.fire)

    s3_in.ready := !s3_in.valid || io.icPort.rready

    io.icPort.rvalid := s3_in.valid
    io.icPort.inst := s3_data
}
