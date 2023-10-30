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
    val forwading   = Module(new Forwarding)
    val readregs    = Module(new ReadRegs)
    val execute     = Module(new Execute)
    val memory      = Module(new Memory)
    val writeback   = Module(new Writeback)

    val regs        = Module(new Regs)
    val csrs        = Module(new Csrs)
    val icache      = Module(new ICache)
    val dcache      = Module(new DCache)
    val bpu         = Module(new SimpleBPU)

    val mem2Axi     = Module(new ToAXI)
    // val flash2Axi   = Module(new ToAXI)

    // val crossBar    = Module(new CrossBar)
    // val fetchCrossbar = Module(new FetchCrossBar)
    val memCrossbar = Module(new MemCrossBar)
    // val tlb_if       = Module(new TLB)
    // val tlb_mem      = Module(new TLB)
    // val dcSelector  = Module(new DcacheSelector)
    // val clint       = Module(new CLINT)
    // val plic        = Module(new Plic)
    // val dmaBridge   = Module(new DmaBridge)

    fetch.io.instRead  <> icache.io.icPort
    fetch.io.bp <> bpu.io.predict


    // fetch.io.instRead   <> fetchCrossbar.io.instIO
    // fetch.io.va2pa      <> tlb_if.io.va2pa
    fetch.io.reg2if     <> csrs.io.reg2if
    fetch.io.wb2if      <> writeback.io.wb2if
    // fetch.io.intr_in    <> csrs.io.intr_out
    fetch.io.branchFail <> execute.io.ex2if
    fetch.io.if2id      <> decode.io.if2id
    fetch.io.recov      <> writeback.io.recov

    decode.io.id2df     <> forwading.io.id2df
    decode.io.idState   <> csrs.io.idState
    forwading.io.df2rr  <> readregs.io.df2rr
    forwading.io.d_rr   <> readregs.io.d_rr
    forwading.io.d_ex   <> execute.io.d_ex
    forwading.io.d_mem1 <> memory.io.d_mem1

    readregs.io.rr2ex   <> execute.io.rr2ex
    readregs.io.rs1Read <> regs.io.rs1
    readregs.io.rs2Read <> regs.io.rs2
    readregs.io.csrRead <> csrs.io.rs

    execute.io.ex2mem   <> memory.io.ex2mem
    execute.io.updateNextPc <> csrs.io.updateNextPc
    memory.io.mem2rb    <> writeback.io.mem2rb
    memory.io.dataRW    <> memCrossbar.io.dataRW
    // memory.io.va2pa     <> tlb_mem.io.va2pa

    writeback.io.wReg   <> regs.io.dst
    writeback.io.wCsr   <> csrs.io.rd
    writeback.io.excep  <> csrs.io.excep
    writeback.io.updateTrace <> bpu.io.updateTrace
    // clint.io.intr       <> csrs.io.clint
    // clint.io.intr_msip  <> csrs.io.intr_msip

    memCrossbar.io.dcRW         <> dcache.io.dcPort
    memCrossbar.io.mmio         <> mem2Axi.io.dataIO
    // bpu.io.priv := csrs.io.priv
    // bpu.io.ra := regs.io.ra

    // crossBar.io.mmioAxi <> mem2Axi.io.outAxi

    // io.slave <> dmaBridge.io.dmaAxi
    // dmaBridge.io.dcRW <> dcSelector.io.dma2dc

    mem2Axi.io.outAxi.wa.ready    := io.master.awready
    io.master.awvalid := mem2Axi.io.outAxi.wa.valid
    io.master.awaddr  := mem2Axi.io.outAxi.wa.bits.addr
    io.master.awid    := mem2Axi.io.outAxi.wa.bits.id
    io.master.awlen   := mem2Axi.io.outAxi.wa.bits.len
    io.master.awsize  := mem2Axi.io.outAxi.wa.bits.size
    io.master.awburst := mem2Axi.io.outAxi.wa.bits.burst

    mem2Axi.io.outAxi.wd.ready   := io.master.wready
    io.master.wvalid  :=  mem2Axi.io.outAxi.wd.valid
    io.master.wdata   :=  mem2Axi.io.outAxi.wd.bits.data
    io.master.wstrb   :=  mem2Axi.io.outAxi.wd.bits.strb
    io.master.wlast   :=  mem2Axi.io.outAxi.wd.bits.last

    io.master.bready  := mem2Axi.io.outAxi.wr.ready
    mem2Axi.io.outAxi.wr.valid        := io.master.bvalid
    mem2Axi.io.outAxi.wr.bits.resp    := io.master.bresp
    mem2Axi.io.outAxi.wr.bits.id      := io.master.bid

    mem2Axi.io.outAxi.ra.ready  := io.master.arready
    io.master.arvalid := mem2Axi.io.outAxi.ra.valid
    io.master.araddr  := mem2Axi.io.outAxi.ra.bits.addr
    io.master.arid    := mem2Axi.io.outAxi.ra.bits.id
    io.master.arlen   := mem2Axi.io.outAxi.ra.bits.len
    io.master.arsize  := mem2Axi.io.outAxi.ra.bits.size
    io.master.arburst := mem2Axi.io.outAxi.ra.bits.burst

    io.master.rready  := mem2Axi.io.outAxi.rd.ready
    mem2Axi.io.outAxi.rd.valid   := io.master.rvalid
    mem2Axi.io.outAxi.rd.bits.resp    := io.master.rresp
    mem2Axi.io.outAxi.rd.bits.data    := io.master.rdata
    mem2Axi.io.outAxi.rd.bits.last    := io.master.rlast
    mem2Axi.io.outAxi.rd.bits.id      := io.master.rid

}
