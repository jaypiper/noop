package noop.dispatch

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._
import noop.param.common._

class Dispatch extends Module {
  val io = IO(new Bundle {
    val df2dp = Vec(ISSUE_WIDTH, Flipped(DecoupledIO(new DF2EX)))
    val df2ex = Vec(ISSUE_WIDTH, DecoupledIO(new DF2EX))
    val df2mem = DecoupledIO(new DF2MEM)
    val mem2df = Input(new MEM2DF)
  })

  val is_alu = io.df2dp.map(_.bits.ctrl.dcMode === mode_NOP)
  val do_mem = io.df2dp.zip(is_alu).map{ case (v, alu) => v.valid && !alu }

  for (i <- 0 until ISSUE_WIDTH) {
    val is_to_mem = if (i == 0) true.B else !VecInit(do_mem.take(i)).asUInt.orR
    io.df2dp(i).ready := Mux(is_alu(i), io.df2ex(i).ready && !io.mem2df.membusy, io.df2mem.ready && is_to_mem)

    io.df2ex(i).bits := io.df2dp(i).bits
    io.df2ex(i).valid := io.df2dp(i).valid && !io.mem2df.membusy && is_alu(i)
  }

  val to_mem = PriorityMux(do_mem, io.df2dp.map(_.bits))
  io.df2mem.bits.inst := to_mem.inst
  io.df2mem.bits.pc := to_mem.pc
  io.df2mem.bits.excep := 0.U.asTypeOf(new Exception) // TODO: remove
  io.df2mem.bits.ctrl := to_mem.ctrl
  io.df2mem.bits.mem_addr := to_mem.rs1_d + to_mem.dst_d
  io.df2mem.bits.mem_data := to_mem.rs2_d
  io.df2mem.bits.csr_id := 0.U
  io.df2mem.bits.csr_d := 0.U
  io.df2mem.bits.dst := to_mem.dst
  io.df2mem.bits.dst_d := 0.U
  io.df2mem.bits.rcsr_id := 0.U
  io.df2mem.bits.recov := to_mem.recov
  io.df2mem.valid := VecInit(do_mem).asUInt.orR

}