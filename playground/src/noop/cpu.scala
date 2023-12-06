package noop.cpu

import chisel3._
import chisel3.util._
import noop.bpu._
import noop.bus._
import noop.cache._
import noop.decode._
import noop.dispatch.Dispatch
import noop.execute._
import noop.fetch._
import noop.ibuffer.IBuffer
import noop.memory._
import noop.param.common._
import noop.param.decode_config._
import noop.regs._
import noop.utils.{PerfAccumulate, VecDecoupledIO, VecPipelineConnect}
import noop.writeback._

class CPU_AXI_IO extends Bundle{
    val awready = Input(Bool())
    val awvalid = Output(Bool())
    val awaddr  = Output(UInt(32.W))
    val awid    = Output(UInt(4.W))
    val awlen   = Output(UInt(8.W))
    val awsize  = Output(UInt(3.W))
    val awburst = Output(UInt(2.W))
    val wready  = Input(Bool())
    val wvalid  = Output(Bool())
    val wdata   = Output(UInt(64.W))
    val wstrb   = Output(UInt(8.W))
    val wlast   = Output(Bool())
    val bready  = Output(Bool())
    val bvalid  = Input(Bool())
    val bresp   = Input(UInt(2.W))
    val bid     = Input(UInt(4.W))
    val arready = Input(Bool())
    val arvalid = Output(Bool())
    val araddr  = Output(UInt(32.W))
    val arid    = Output(UInt(4.W))
    val arlen   = Output(UInt(8.W))
    val arsize  = Output(UInt(3.W))
    val arburst = Output(UInt(2.W))
    val rready  = Output(Bool())
    val rvalid  = Input(Bool())
    val rresp   = Input(UInt(2.W))
    val rdata   = Input(UInt(64.W))
    val rlast   = Input(Bool())
    val rid     = Input(UInt(4.W))
}

class CPUIO extends Bundle{
    // val outAxi = new AxiMaster
    val master      = new CPU_AXI_IO
    // val slave       = Flipped(new CPU_AXI_IO)
    // val interrupt   = Input(Bool())
}

class CPU extends Module{
    val io = IO(new CPUIO)
    val fetch       = Module(new Fetch)
    val decode      = Module(new Decode)
    val ibuffer     = Module(new IBuffer)
    val forwarding  = Seq.tabulate(2)(i => Module(new Forwarding(6 + i)))
    val dispatch    = Module(new Dispatch)
    val execute     = Seq.fill(2)(Module(new Execute))
    val memory      = Module(new Memory)
    val writeback   = Module(new Writeback)

    val regs        = Module(new Regs)
    val csrs        = Module(new Csrs)
    val icache      = Module(new ICache)
    val dcache      = Module(new DCache)
    val bpu         = Module(new SimpleBPU2)

    val mem2Axi     = Module(new ToAXI)
    val fetch2Axi   = Module(new ToAXI)

    val memCrossbar = Module(new MemCrossBar)
    val fetchCrossbar = Module(new FetchCrossBar)
    val crossBar = Module(new CrossBar)

    // Flush
    val execute_flush = VecInit(execute.map(_.io.flushOut)).asUInt.orR
    val forward_flush = VecInit(forwarding.map(_.io.flush)).asUInt.orR || execute_flush

    // Fetch
    fetch.io.instRead <> fetchCrossbar.io.instIO
    fetchCrossbar.io.icRead <> icache.io.icPort
    fetchCrossbar.io.flashRead <> fetch2Axi.io.dataIO
    crossBar.io.flashAxi <> fetch2Axi.io.outAxi
    crossBar.io.mmioAxi <> mem2Axi.io.outAxi

    fetch.io.bp <> bpu.io.predict

    fetch.io.reg2if     <> csrs.io.reg2if
    fetch.io.wb2if      <> writeback.io.wb2if
    fetch.io.branchFail := PriorityMux(execute.map(_.io.ex2if.valid), execute.map(_.io.ex2if))
    // branch mis-prediction has higher priority than decode stall
    fetch.io.stall := decode.io.stall.asUInt.orR && !execute_flush
    fetch.io.flush := forward_flush || decode.io.stall.asUInt.orR
    fetch.io.recov := writeback.io.recov

    // Ibuffer
    ibuffer.io.in <> fetch.io.if2id
    ibuffer.io.out <> decode.io.if2id
    ibuffer.io.flush := forward_flush || decode.io.stall.asUInt.orR && decode.io.id2df.ready

    // Decode
    decode.io.id2df.connectNoPipe(forwarding.map(_.io.id2df), forward_flush)
    forwarding.head.io.blockOut := false.B
    for (i <- 1 until ISSUE_WIDTH) {
        forwarding(i).io.blockOut := VecInit(forwarding.take(i).map(_.io.rightStall)).asUInt.orR
        PerfAccumulate(s"forwarding_${i}_blocked", forwarding(i).io.id2df.valid && !forwarding(i).io.rightStall && forwarding(i).io.blockOut)
    }
    decode.io.idState := RegNext(csrs.io.idState)
    val forwarding_allow_in = RegNext(VecInit(forwarding.map(_.io.id2df.ready)).asUInt.andR, false.B)
    val forwarding_num_in = PopCount(forwarding.map(_.io.id2df.valid))
    val forwarding_num_out = PopCount(forwarding.map(_.io.df2dp.fire))
    for (i <- 0 until ISSUE_WIDTH + 1) {
        PerfAccumulate(s"forwarding_in_fire_$i", forwarding_allow_in && forwarding_num_in === i.U)
    }
    for (i <- 0 until ISSUE_WIDTH + 1) {
        for (j <- 0 until i + 1) {
            PerfAccumulate(s"forwarding_in_${i}_out_$j", forwarding_num_in === i.U && forwarding_num_out === j.U)
        }
    }


    // Regfile and Forwarding
    val fwd_source = execute.map(_.io.d_ex0).reverse ++
      Seq(memory.io.d_mem0) ++
      execute.map(_.io.d_ex1).reverse ++
      Seq(memory.io.d_mem1)
    for (i <- 0 until ISSUE_WIDTH) {
        forwarding(i).io.fwd_source := forwarding.take(i).map(_.io.d_fd) ++ fwd_source
        forwarding(i).io.rs1Read <> regs.io.rs1(i)
        forwarding(i).io.rs2Read <> regs.io.rs2(i)
        forwarding(i).io.csrRead <> csrs.io.rs(i)
    }

    // Dispatch arbiter and execution units
    VecPipelineConnect(forwarding.map(_.io.df2dp), dispatch.io.df2dp, execute_flush)
    for (i <- 0 until ISSUE_WIDTH) {
        dispatch.io.df2ex(i) <> execute(i).io.df2ex
        dispatch.io.df2mem <> memory.io.df2mem
        dispatch.io.mem2df := memory.io.mem2df
        execute(i).io.blockIn := false.B
        execute(i).io.flushIn := false.B
        if (i > 0) {
            execute(i).io.blockIn := memory.io.mem2df.membusy
            execute(i).io.flushIn := VecInit(execute.take(i).map(_.io.flushOut)).asUInt.orR
        }

    }
    memory.io.flushIn := execute(0).io.flushOut
    val is_jmp = execute.map(_.io.updateBPU.valid)
    val is_mispred = execute.map(exe => RegNext(exe.io.flushOut, false.B))
    // Should only count the instructions before flush
    private def is_counted_jmp(i: Int) = is_jmp(i) && !VecInit((false.B +: is_mispred).take(i + 1)).asUInt.orR
    PerfAccumulate("branchNum_all", PopCount((0 until ISSUE_WIDTH).map(is_counted_jmp)))
    PerfAccumulate("branchMiss_all", VecInit(is_mispred).asUInt.orR)
    val jmp_types = Seq((JMP_PC, "pc"), (JMP_REG, "reg"), (JMP_CSR, "csr"), (JMP_COND, "cond"))
    for ((t, name) <- jmp_types) {
        val is_t = execute.map(exe => RegNext(exe.io.df2ex.bits.jmp_type === t, false.B))
        PerfAccumulate(s"branchNum_$name", PopCount((0 until ISSUE_WIDTH).map(i => is_counted_jmp(i) && is_t(i))))
        PerfAccumulate(s"branchMiss_$name", VecInit(is_mispred.zip(is_t).map(x => x._1 && x._2)).asUInt.orR)
    }

    // Writeback
    writeback.io.ex2wb.zip(execute.map(_.io.ex2wb)).foreach(x => x._1 := x._2)
    bpu.io.update := PriorityMux(execute.map(_.io.updateBPU.needUpdate), execute.map(_.io.updateBPU))
    memory.io.mem2wb    <> writeback.io.mem2wb
    memory.io.dataRW    <> memCrossbar.io.dataRW
    // memory.io.va2pa     <> tlb_mem.io.va2pa

    writeback.io.wReg   <> regs.io.dst
    writeback.io.wCsr   <> csrs.io.rd
    writeback.io.excep  <> csrs.io.excep

    memCrossbar.io.dcRW         <> dcache.io.dcPort
    memCrossbar.io.mmio         <> mem2Axi.io.dataIO
    memCrossbar.io.icRW         <> icache.io.icMem

    crossBar.io.outAxi.wa.ready    := io.master.awready
    io.master.awvalid := crossBar.io.outAxi.wa.valid
    io.master.awaddr  := crossBar.io.outAxi.wa.bits.addr
    io.master.awid    := crossBar.io.outAxi.wa.bits.id
    io.master.awlen   := crossBar.io.outAxi.wa.bits.len
    io.master.awsize  := crossBar.io.outAxi.wa.bits.size
    io.master.awburst := crossBar.io.outAxi.wa.bits.burst

    crossBar.io.outAxi.wd.ready   := io.master.wready
    io.master.wvalid  :=  crossBar.io.outAxi.wd.valid
    io.master.wdata   :=  crossBar.io.outAxi.wd.bits.data
    io.master.wstrb   :=  crossBar.io.outAxi.wd.bits.strb
    io.master.wlast   :=  crossBar.io.outAxi.wd.bits.last

    io.master.bready  := crossBar.io.outAxi.wr.ready
    crossBar.io.outAxi.wr.valid        := io.master.bvalid
    crossBar.io.outAxi.wr.bits.resp    := io.master.bresp
    crossBar.io.outAxi.wr.bits.id      := io.master.bid

    crossBar.io.outAxi.ra.ready  := io.master.arready
    io.master.arvalid := crossBar.io.outAxi.ra.valid
    io.master.araddr  := crossBar.io.outAxi.ra.bits.addr
    io.master.arid    := crossBar.io.outAxi.ra.bits.id
    io.master.arlen   := crossBar.io.outAxi.ra.bits.len
    io.master.arsize  := crossBar.io.outAxi.ra.bits.size
    io.master.arburst := crossBar.io.outAxi.ra.bits.burst

    io.master.rready  := crossBar.io.outAxi.rd.ready
    crossBar.io.outAxi.rd.valid   := io.master.rvalid
    crossBar.io.outAxi.rd.bits.resp    := io.master.rresp
    crossBar.io.outAxi.rd.bits.data    := io.master.rdata
    crossBar.io.outAxi.rd.bits.last    := io.master.rlast
    crossBar.io.outAxi.rd.bits.id      := io.master.rid

}
