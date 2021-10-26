package noop.cpu

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.tlb._
import noop.cache._
import noop.bus._
import noop.bpu._
import noop.fetch._
import noop.decode._
import noop.execute._
import noop.memory._
import noop.writeback._
import noop.regs._
import noop.clint._

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
    val slave       = Flipped(new CPU_AXI_IO)
    val interrupt   = Input(Bool())
}

class CPU extends Module{
    val io = IO(new CPUIO)
    dontTouch(io)
    val fetch       = Module(new Fetch)
    val decode      = Module(new Decode)
    val forwading   = Module(new Forwarding)
    val readregs    = Module(new ReadRegs)
    val execute     = Module(new Execute)
    val memory      = Module(new Memory)
    val writeback   = Module(new Writeback)

    val regs        = Module(new Regs)
    val csrs        = Module(new Csrs)
    val icache      = Module(new InstCache)
    val dcache      = Module(new DataCache)

    val mem2Axi     = Module(new ToAXI)
    val flash2Axi   = Module(new ToAXI)

    val crossBar    = Module(new CrossBar)
    val fetchCrossbar = Module(new FetchCrossBar)
    val memCrossbar = Module(new MemCrossBar)
    val tlb_if       = Module(new TLB)
    val tlb_mem      = Module(new TLB)
    val dcSelector  = Module(new DcacheSelector)
    val bpu         = Module(new BPU)
    val clint       = Module(new CLINT)

    fetch.io.bpuSearch  <> bpu.io.search
    fetch.io.instRead   <> fetchCrossbar.io.instIO
    fetch.io.va2pa      <> tlb_if.io.va2pa
    fetch.io.reg2if     <> csrs.io.reg2if
    fetch.io.wb2if      <> writeback.io.wb2if
    fetch.io.intr_in    <> csrs.io.intr_out
    fetch.io.branchFail <> execute.io.ex2if
    fetch.io.if2id      <> decode.io.if2id
    fetch.io.recov      <> writeback.io.recov

    decode.io.id2df     <> forwading.io.id2df
    decode.io.idState   <> csrs.io.idState
    forwading.io.df2rr  <> readregs.io.df2rr
    forwading.io.d_rr   <> readregs.io.d_rr
    forwading.io.d_ex   <> execute.io.d_ex
    forwading.io.d_mem1 <> memory.io.d_mem1
    forwading.io.d_mem2 <> memory.io.d_mem2
    forwading.io.d_mem3 <> memory.io.d_mem3
    readregs.io.rr2ex   <> execute.io.rr2ex
    readregs.io.rs1Read <> regs.io.rs1
    readregs.io.rs2Read <> regs.io.rs2
    readregs.io.csrRead <> csrs.io.rs

    execute.io.ex2mem   <> memory.io.ex2mem
    execute.io.bpuUpdate<> bpu.io.update
    memory.io.mem2rb    <> writeback.io.mem2rb
    memory.io.dataRW    <> memCrossbar.io.dataRW
    memory.io.va2pa     <> tlb_mem.io.va2pa

    writeback.io.wReg   <> regs.io.dst
    writeback.io.wCsr   <> csrs.io.rd
    writeback.io.excep  <> csrs.io.excep
    clint.io.intr       <> csrs.io.clint

    icache.io.flush     <> writeback.io.flush_cache
    dcache.io.flush     <> writeback.io.flush_cache
    tlb_if.io.flush     <> writeback.io.flush_tlb
    tlb_mem.io.flush    <> writeback.io.flush_tlb

    fetchCrossbar.io.icRead     <> icache.io.icRead
    fetchCrossbar.io.flashRead  <> flash2Axi.io.dataIO
    memCrossbar.io.dcRW         <> dcSelector.io.mem2dc
    memCrossbar.io.mmio         <> mem2Axi.io.dataIO
    memCrossbar.io.clintIO      <> clint.io.rw
    dcSelector.io.select        <> dcache.io.dcRW
    tlb_if.io.dcacheRW          <> dcSelector.io.tlb_if2dc
    tlb_if.io.mmuState          <> csrs.io.mmuState
    tlb_mem.io.dcacheRW         <> dcSelector.io.tlb_mem2dc
    tlb_mem.io.mmuState         <> csrs.io.mmuState

    crossBar.io.icAxi   <> icache.io.instAxi
    crossBar.io.memAxi  <> dcache.io.dataAxi
    crossBar.io.mmioAxi <> mem2Axi.io.outAxi
    crossBar.io.flashAxi <> flash2Axi.io.outAxi

    crossBar.io.outAxi.wa.ready    := io.master.awready
    io.master.awvalid := crossBar.io.outAxi.wa.valid
    io.master.awaddr  := crossBar.io.outAxi.wa.bits.addr
    io.master.awid    := crossBar.io.outAxi.wa.bits.id
    io.master.awlen   := crossBar.io.outAxi.wa.bits.len
    io.master.awsize  := crossBar.io.outAxi.wa.bits.size
    io.master.awburst := crossBar.io.outAxi.wa.bits.burst

    crossBar.io.outAxi.wd.ready   := io.master.wready
    io.master.wvalid  := crossBar.io.outAxi.wd.valid
    io.master.wdata   := crossBar.io.outAxi.wd.bits.data
    io.master.wstrb   := crossBar.io.outAxi.wd.bits.strb
    io.master.wlast   := crossBar.io.outAxi.wd.bits.last

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

    io.slave := DontCare
}
