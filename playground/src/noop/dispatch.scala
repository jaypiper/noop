package noop.dispatch

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.param.common._
import noop.utils.PerfAccumulate

class Dispatch extends Module {
  val io = IO(new Bundle {
    val df2dp = Vec(ISSUE_WIDTH, Flipped(DecoupledIO(new DF2EX)))
    val df2ex = Vec(ISSUE_WIDTH, DecoupledIO(new DF2EX))
    val df2mem = DecoupledIO(new DF2MEM)
    val mem2df = Input(new MEM2DF)
  })

  val is_alu = io.df2dp.map(_.bits.ctrl.dcMode === mode_NOP)
  val do_alu = io.df2dp.zip(is_alu).map{ case (v, alu) => v.valid && alu }
  val do_mem = io.df2dp.zip(is_alu).map{ case (v, alu) => v.valid && !alu }

  // When previous instructions are to mem as well, block it
  private def multiple_mem(i: Int): Bool = VecInit(do_mem.take(i)).asUInt.orR

  val block_alu = io.mem2df.membusy
  val block_mem = false.B +: (1 until ISSUE_WIDTH).map(i => multiple_mem(i))

  for (i <- 0 until ISSUE_WIDTH) {
    io.df2dp(i).ready := Mux(is_alu(i), io.df2ex(i).ready && !block_alu, io.df2mem.ready && !block_mem(i))

    io.df2ex(i).bits := io.df2dp(i).bits
    io.df2ex(i).valid := do_alu(i) && !block_alu
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
  io.df2mem.valid := VecInit(do_mem).asUInt.orR && !PriorityMux(do_mem, block_mem)

  val allow_in = RegNext(VecInit(io.df2dp.map(_.ready)).asUInt.andR, false.B)
  val num_in = PopCount(io.df2dp.map(_.valid))
  val num_out = PopCount(io.df2ex.map(_.fire)) + io.df2mem.fire
  for (i <- 0 until ISSUE_WIDTH + 1) {
    PerfAccumulate(s"dispatch_in_fire_$i", allow_in && num_in === i.U)
  }
  for (i <- 0 until ISSUE_WIDTH + 1) {
    for (j <- 0 until i + 1) {
      PerfAccumulate(s"dispatch_in_${i}_out_$j", num_in === i.U && num_out === j.U)
    }
  }
  for (i <- 0 until ISSUE_WIDTH) {
    val blocked = io.df2dp(i).valid && !io.df2dp(i).ready
    PerfAccumulate(s"dispatch_in_${i}_blocked", blocked)
    PerfAccumulate(s"dispatch_in_${i}_blocked_alu", blocked && is_alu(i))
    PerfAccumulate(s"dispatch_in_${i}_blocked_alu_membusy", blocked && is_alu(i) && io.mem2df.membusy)
    PerfAccumulate(s"dispatch_in_${i}_blocked_mem", blocked && !is_alu(i))
    PerfAccumulate(s"dispatch_in_${i}_blocked_mem_right_ready", blocked && !is_alu(i) && !io.df2mem.ready)
    if (i > 0) {
      PerfAccumulate(s"dispatch_in_${i}_blocked_mem_multiple", blocked && !is_alu(i) && multiple_mem(i))
    }
  }
}