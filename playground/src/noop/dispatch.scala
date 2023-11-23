package noop.dispatch

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.cache_config._

class Dispatch extends Module {
  val io = IO(new Bundle {
    val df2dp = Flipped(DecoupledIO(new DF2EX))
    val dp2df = Output(new PipelineBackCtrl)
    val df2ex = DecoupledIO(new DF2EX)
    val ex2df = Input(new EX2DF)
    val df2mem = DecoupledIO(new DF2MEM)
    val mem2df = Input(new MEM2DF)
  })

  val out_is_ready = !io.mem2df.membusy && io.df2dp.bits.ctrl.dcMode === mode_NOP && io.df2ex.ready ||
    !io.ex2df.exBusy && io.df2dp.bits.ctrl.dcMode =/= mode_NOP && io.df2mem.ready
  io.df2dp.ready := !io.df2dp.valid || out_is_ready

  val drop_in = io.ex2df.drop || io.mem2df.drop
  io.dp2df.drop := drop_in
  io.dp2df.stall := io.ex2df.stall

  io.df2ex.bits.inst := io.df2dp.bits.inst
  io.df2ex.bits.pc := io.df2dp.bits.pc
  io.df2ex.bits.nextPC := io.df2dp.bits.nextPC
  io.df2ex.bits.excep := io.df2dp.bits.excep
  io.df2ex.bits.ctrl := io.df2dp.bits.ctrl
  io.df2ex.bits.rs1 := io.df2dp.bits.rs1
  io.df2ex.bits.rs1_d := io.df2dp.bits.rs1_d
  io.df2ex.bits.rs2 := io.df2dp.bits.rs2
  io.df2ex.bits.rs2_d := io.df2dp.bits.rs2_d
  io.df2ex.bits.dst := io.df2dp.bits.dst
  io.df2ex.bits.dst_d := io.df2dp.bits.dst_d
  io.df2ex.bits.jmp_type := io.df2dp.bits.jmp_type
  io.df2ex.bits.rcsr_id := io.df2dp.bits.rcsr_id
  io.df2ex.bits.recov := io.df2dp.bits.recov
  io.df2ex.valid := io.df2dp.valid && !drop_in && !io.mem2df.membusy && io.df2dp.bits.ctrl.dcMode === mode_NOP

  io.df2mem.bits.inst := io.df2dp.bits.inst
  io.df2mem.bits.pc := io.df2dp.bits.pc
  io.df2mem.bits.excep := 0.U.asTypeOf(new Exception) // TODO: remove
  io.df2mem.bits.ctrl := io.df2dp.bits.ctrl
  io.df2mem.bits.mem_addr := io.df2dp.bits.rs1_d + io.df2dp.bits.dst_d
  io.df2mem.bits.mem_data := io.df2dp.bits.rs2_d
  io.df2mem.bits.csr_id := 0.U
  io.df2mem.bits.csr_d := 0.U
  io.df2mem.bits.dst := io.df2dp.bits.dst
  io.df2mem.bits.dst_d := 0.U
  io.df2mem.bits.rcsr_id := 0.U
  io.df2mem.bits.recov := io.df2dp.bits.recov
  io.df2mem.valid := io.df2dp.valid && !drop_in && !io.ex2df.exBusy && io.df2dp.bits.ctrl.dcMode =/= mode_NOP

}