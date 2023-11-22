package noop.cpu

import chisel3._
import chisel3.util._
import noop.param.common._
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
import noop.plic._

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
    dontTouch(io)
    val fetch       = Module(new Fetch)
    val decode      = Module(new Decode)
    val forwarding  = Module(new Forwarding)
    val execute     = Module(new Execute)
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

    // fetch.io.instRead  <> icache.io.icPort
    fetch.io.instRead <> fetchCrossbar.io.instIO
    fetchCrossbar.io.icRead <> icache.io.icPort
    fetchCrossbar.io.flashRead <> fetch2Axi.io.dataIO
    crossBar.io.flashAxi <> fetch2Axi.io.outAxi
    crossBar.io.mmioAxi <> mem2Axi.io.outAxi

    fetch.io.bp <> bpu.io.predict

    fetch.io.reg2if     <> csrs.io.reg2if
    fetch.io.wb2if      <> writeback.io.wb2if
    fetch.io.branchFail <> execute.io.ex2if
    fetch.io.if2id      <> decode.io.if2id
    fetch.io.recov      <> writeback.io.recov

    decode.io.id2df     <> forwarding.io.id2df
    decode.io.idState   <> csrs.io.idState
    forwarding.io.df2ex  <> execute.io.df2ex
    forwarding.io.d_ex   <> execute.io.d_ex
    forwarding.io.d_mem1 <> memory.io.d_mem1
    forwarding.io.d_mem0 <> memory.io.d_mem0
    forwarding.io.rs1Read <> regs.io.rs1
    forwarding.io.rs2Read <> regs.io.rs2
    forwarding.io.csrRead <> csrs.io.rs
    forwarding.io.d_ex0 <> execute.io.d_ex0
    forwarding.io.df2mem <> memory.io.df2mem

    execute.io.ex2wb   <> writeback.io.ex2wb
    execute.io.updateBPU <> bpu.io.update
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
