package noop.fetch

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.bpu._
import noop.datapath._
import noop.utils.{PerfAccumulate, PipelineNext, VecDecoupledIO}

class FetchCrossBar extends Module{
    val io = IO(new Bundle{
        val instIO = new IcacheRead
        val icRead = Flipped(new IcacheRead)
        val flashRead = Flipped(new DcacheRW)
    })
    val pre_mem = RegInit(false.B)
    val sMem :: sFlash :: Nil = Enum(2)
    val state = RegInit(sMem)
    val memNum = RegInit(0.U(2.W))
    val flashNum = RegInit(0.U(2.W))
    val inp_mem = in_imem(io.instIO.addr)
    io.flashRead.req.bits.addr   := io.instIO.addr
    io.flashRead.req.bits.wdata  := 0.U;
    io.flashRead.req.bits.wen    := false.B
    io.flashRead.req.bits.wmask  := 0.U
    io.flashRead.req.bits.size   := 3.U
    io.flashRead.req.valid := false.B
    io.flashRead.req_cancel := false.B
    io.icRead.addr      := io.instIO.addr
    io.icRead.arvalid   := false.B
    io.instIO.ready     := false.B
    io.icRead.rready := io.instIO.rready
    io.instIO.inst      := 0.U
    io.instIO.rvalid    := false.B
    val hs_in = io.instIO.ready && io.instIO.arvalid
    val hs_out = io.instIO.rready && io.instIO.rvalid
    when (hs_in && !hs_out) {
        memNum := memNum + inp_mem
    }.elsewhen(!hs_in && hs_out) {
        memNum := memNum - (state === sMem)
    }
    when(hs_in && !io.instIO.rvalid) {
        flashNum := flashNum + !inp_mem
    }.elsewhen(!hs_in && io.instIO.rvalid) {
        flashNum := 0.U
    }
    switch(state) {
        is(sMem) {
            when (!inp_mem && io.instIO.arvalid) {
                when(memNum === 0.U) {
                    state := sFlash
                }
            }.otherwise {
                io.instIO.ready := io.icRead.ready
                io.icRead.arvalid := io.instIO.arvalid
            }
            io.instIO.inst  := io.icRead.inst
            io.instIO.rvalid := io.icRead.rvalid
        }
        is(sFlash) {
            when (inp_mem && io.instIO.arvalid) {
                when(flashNum === 0.U) {
                    state := sMem
                }
            }.otherwise {
                io.flashRead.req.valid := io.instIO.arvalid
                io.instIO.ready := io.flashRead.req.ready
            }
            io.instIO.inst  := io.flashRead.resp.bits
            io.instIO.rvalid := io.flashRead.resp.valid
        }
    }
}

class FetchIO extends Bundle{
    val instRead    = Flipped(new IcacheRead)
    val reg2if      = Input(new ForceJmp)
    val wb2if       = Input(new ForceJmp)
    val recov       = Input(Bool())
    // val intr_in     = Input(new RaiseIntr)
    val branchFail  = Input(new ForceJmp)
    val if2id       = VecDecoupledIO(ISSUE_WIDTH, new IF2ID)
    val stall       = Input(Bool())
    val flush       = Input(Bool())
    val bp          = Vec(ISSUE_WIDTH, Flipped(new PredictIO2))
}

class FetchS1 extends Module {
    val io = IO(new Bundle {
        val reg2if = Input(new ForceJmp)
        val wb2if = Input(new ForceJmp)
        val recov = Input(Bool())
        val branchFail = Input(new ForceJmp)
        val stall = Input(Bool())
        val flush = Input(Bool())
        val bp = Vec(ISSUE_WIDTH, Flipped(new PredictIO2))
    })
    val instRead = IO(DecoupledIO())
    val out = IO(DecoupledIO(new Bundle {
        val pc = Vec(2, UInt(PADDR_WIDTH.W))
        val fetch_two = Bool()
        val is_jmp = Vec(2, Bool())
    }))

    val pc_r = RegInit((PC_START >> 2).asTypeOf(UInt((PADDR_WIDTH - 2).W)))
    val pc = Cat(pc_r, 0.U(2.W))

    val fetch_two = (!pc(2) || in_imem(pc) && !pc(14, 2).andR) && !io.bp(0).jmp
    val pc_seq = Mux(fetch_two, pc + 8.U, pc + 4.U)
    val next_pc = PriorityMux(Seq(
        (io.reg2if.valid, io.reg2if.seq_pc),
        (io.wb2if.valid, io.wb2if.seq_pc),
        (io.branchFail.valid, io.branchFail.seq_pc),
        (io.bp(0).jmp && out.fire, Cat(io.bp(0).target, 0.U(2.W))),
        (fetch_two && io.bp(1).jmp && out.fire, Cat(io.bp(1).target, 0.U(2.W))),
        (out.fire, pc_seq),
        (true.B, pc)))
    pc_r := next_pc(PADDR_WIDTH - 1, 2)

    val sIdle :: sStall :: Nil = Enum(2)
    val state = RegInit(sIdle)
    switch(state) {
        is(sIdle) {
            when(io.stall) {
                state := sStall
            }
        }
        is(sStall) {
            when((io.flush && !io.stall) || io.recov) {
                state := sIdle
            }
        }
    }

    out.valid := state === sIdle && instRead.ready
    out.bits.pc(0) := pc
    out.bits.pc(1) := Cat(pc(PADDR_WIDTH - 1, 2) + 1.U, 0.U(2.W))
    out.bits.fetch_two := fetch_two
    out.bits.is_jmp := io.bp.map(_.jmp)

    instRead.valid := state === sIdle && out.ready

    io.bp(0).pc := pc(PADDR_WIDTH - 1, 2)
    io.bp.map(_.v := out.fire)
    io.bp(1).pc := out.bits.pc(1)(PADDR_WIDTH - 1, 2)

    PerfAccumulate("fetch_one", out.fire && !out.bits.fetch_two)
    PerfAccumulate("fetch_two", out.fire && out.bits.fetch_two)
}

class Fetch extends Module{
    val io = IO(new FetchIO)

    val flush_in = io.flush
    val stall_in = io.stall

    // Stage 1
    val s1 = Module(new FetchS1)
    s1.io.reg2if := io.reg2if
    s1.io.wb2if := io.wb2if
    s1.io.recov := io.recov
    s1.io.branchFail := io.branchFail
    s1.io.stall := stall_in
    s1.io.flush := flush_in
    s1.io.bp <> io.bp

    s1.instRead.ready := io.instRead.ready
    io.instRead.addr := s1.out.bits.pc.head
    io.instRead.arvalid := s1.instRead.valid

    // Stage 2
    val s2_in = PipelineNext(s1.out, flush_in)
    val s2_nextpc = s1.out.bits.pc.head
    val s2_inst_valid = RegInit(false.B)
    when(flush_in || io.if2id.ready) {
        s2_inst_valid := false.B
    }.elsewhen(io.instRead.rvalid) {
        s2_inst_valid := true.B
        assert(s2_in.valid, "response for what?")
    }
    val s2_inst = Mux(s2_inst_valid, RegEnable(io.instRead.inst, io.instRead.rvalid), io.instRead.inst)

    val s2_inst_ok = io.instRead.rvalid || s2_inst_valid
    s2_in.ready := !s2_in.valid || io.if2id.ready && s2_inst_ok
    io.instRead.rready := true.B

    val s2_out_valid = s2_in.valid && s2_inst_ok
    val insts = s2_inst.asTypeOf(Vec(ISSUE_WIDTH, UInt(INST_WIDTH.W)))
    io.if2id.valid(0) := s2_out_valid
    io.if2id.bits(0).pc := s2_in.bits.pc(0)
    io.if2id.bits(0).inst := insts(io.if2id.bits(0).pc(2))
    io.if2id.bits(0).nextPC := Mux(s2_in.bits.fetch_two, io.if2id.bits(1).pc, s2_nextpc)
    io.if2id.bits(0).recov := false.B  // TODO: remove
    io.if2id.bits(0).is_jmp := s2_in.bits.is_jmp(0)

    io.if2id.valid(1) := s2_out_valid && s2_in.bits.fetch_two
    io.if2id.bits(1).pc := s2_in.bits.pc(1)
    io.if2id.bits(1).inst := insts(!io.if2id.bits(0).pc(2))
    io.if2id.bits(1).nextPC := s2_nextpc
    io.if2id.bits(1).recov := false.B
    io.if2id.bits(1).is_jmp := s2_in.bits.is_jmp(1)
}