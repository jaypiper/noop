package noop.dispatch

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.param.common._

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

  // If port 0 is memory and port 1 is ALU, we have to delay the second-port ALU until not membusy
  private def alu_order_violation(i: Int): Bool = do_mem(0)
  // If port 0 is ALU is blocked, we should blocked port 1.
  private def instr_order_violation(i: Int): Bool = VecInit((0 until i).map(i => do_alu(i) && !io.df2ex(i).ready)).asUInt.orR
  // When previous instructions are to mem as well, block it
  private def multiple_mem(i: Int): Bool = VecInit(do_mem.take(i)).asUInt.orR
  // When previous instructions are to ALU and may flush the pipeline, we should also block it
  // TODO: support flush in the memory pipeline and remove this constraint
  val is_jmp = io.df2dp.map(in => in.valid && in.bits.jmp_type =/= NO_JMP)
  private def may_flush(i: Int): Bool = VecInit(is_jmp.take(i)).asUInt.orR

  val block_alu = io.mem2df.membusy +: (1 until ISSUE_WIDTH).map(i => alu_order_violation(i) || io.mem2df.membusy || instr_order_violation(i))
  val block_mem = false.B +: (1 until ISSUE_WIDTH).map(i => multiple_mem(i) || may_flush(i) || instr_order_violation(i))

  for (i <- 0 until ISSUE_WIDTH) {
    io.df2dp(i).ready := Mux(is_alu(i), io.df2ex(i).ready && !block_alu(i), io.df2mem.ready && !block_mem(i))

    io.df2ex(i).bits := io.df2dp(i).bits
    io.df2ex(i).valid := do_alu(i) && !block_alu(i)
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

}