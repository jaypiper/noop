package noop.fetch

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.bpu._
import noop.datapath._
import noop.utils.PipelineNext

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
    val inp_mem = (io.instIO.addr >= "h80000000".U) && (io.instIO.addr < "h80008000".U)
    io.flashRead.addr   := io.instIO.addr
    io.flashRead.wdata  := 0.U;
    io.flashRead.wen    := false.B
    io.flashRead.wmask  := 0.U
    io.flashRead.size   := 3.U
    io.flashRead.avalid := false.B
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
                io.flashRead.avalid := io.instIO.arvalid
                io.instIO.ready := io.flashRead.ready
            }
            io.instIO.inst  := io.flashRead.rdata
            io.instIO.rvalid := io.flashRead.rvalid
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
    val if2id       = DecoupledIO(new IF2ID)
    val id2if       = Input(new ID2IF)
    val bp          = Flipped(new PredictIO2)
}

class FetchS1 extends Module {
    val io = IO(new Bundle {
        val reg2if = Input(new ForceJmp)
        val wb2if = Input(new ForceJmp)
        val recov = Input(Bool())
        val branchFail = Input(new ForceJmp)
        val stall = Input(Bool())
        val drop = Input(Bool())
        val bp_is_jmp = Input(Bool())
        val bp_target = Input(UInt(PADDR_WIDTH.W))
    })
    val instRead = IO(DecoupledIO())
    val out = IO(DecoupledIO(new Bundle {
        val pc = UInt(PADDR_WIDTH.W)
    }))

    val pc = RegInit(PC_START)
    val next_pc = PriorityMux(Seq(
        (io.reg2if.valid, io.reg2if.seq_pc),
        (io.wb2if.valid, io.wb2if.seq_pc),
        (io.branchFail.valid, io.branchFail.seq_pc),
        (io.bp_is_jmp && out.fire, io.bp_target),
        (out.fire, pc + 4.U),
        (true.B, pc)))
    pc := next_pc

    val sIdle :: sStall :: Nil = Enum(2)
    val state = RegInit(sIdle)
    switch(state) {
        is(sIdle) {
            when(io.stall) {
                state := sStall
            }
        }
        is(sStall) {
            when((io.drop && !io.stall) || io.recov) {
                state := sIdle
            }
        }
    }

    out.valid := state === sIdle && instRead.ready
    out.bits.pc := pc

    instRead.valid := state === sIdle && out.ready
}

class Fetch extends Module{
    val io = IO(new FetchIO)

    val drop_in = io.id2if.drop
    val stall_in = io.id2if.stall

    // Stage 1
    val s1 = Module(new FetchS1)
    s1.io.reg2if := io.reg2if
    s1.io.wb2if := io.wb2if
    s1.io.recov := io.recov
    s1.io.branchFail := io.branchFail
    s1.io.stall := stall_in
    s1.io.drop := drop_in
    s1.io.bp_is_jmp := io.bp.jmp
    s1.io.bp_target := io.bp.target

    io.bp.pc := s1.out.bits.pc
    io.bp.valid := s1.out.fire

    s1.instRead.ready := io.instRead.ready
    io.instRead.addr := s1.out.bits.pc
    io.instRead.arvalid := s1.instRead.valid

    // Stage 2
    val s2_out_ready = Wire(Bool())
    val s2_in = PipelineNext(s1.out, s2_out_ready, drop_in)
    val s2_out = WireInit(s2_in)
    s2_out_ready := s2_out.ready
    s2_in.ready := !s2_in.valid || s2_out.ready
    val s2_nextpc = s1.out.bits.pc

    // Stage 3
    val s3_in = PipelineNext(s2_out, io.if2id.ready, drop_in)
    s3_in.ready := !s3_in.valid || io.if2id.ready
    val s3_nextpc = RegEnable(s2_nextpc, s2_out.fire)

    val s3_inst_valid = RegInit(false.B)
    val s3_inst = RegEnable(io.instRead.inst, io.instRead.rvalid && io.instRead.rready)
    io.instRead.rready := !s3_inst_valid || io.if2id.ready
    when(drop_in) {
        s3_inst_valid := false.B
    }.elsewhen(io.instRead.rvalid && !s3_inst_valid && !io.if2id.ready) {
        s3_inst_valid := s3_in.valid
    }.elsewhen(!io.instRead.rvalid && s3_inst_valid && io.if2id.ready) {
        s3_inst_valid := false.B
    }

    // When instRead returns, try bypass it to if2id.
    val s3_out_valid = s3_in.valid && (s3_inst_valid || io.instRead.rvalid)
    val s3_out_inst = Mux(s3_inst_valid, s3_inst, io.instRead.inst)

    io.if2id.valid := s3_out_valid
    io.if2id.bits.pc := s3_in.bits.pc
    private def inst_sel(inst: UInt, pc: UInt): UInt = inst.asTypeOf(Vec(ISSUE_WIDTH, UInt(INST_WIDTH.W)))(pc(2))
    io.if2id.bits.inst := inst_sel(s3_out_inst, io.if2id.bits.pc)
    io.if2id.bits.nextPC := s3_nextpc
    io.if2id.bits.recov := false.B  // TODO: remove
}