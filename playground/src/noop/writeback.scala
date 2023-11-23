package noop.writeback

import chisel3._
import chisel3.util._
import difftest.{ArchEvent, DiffArchEvent, DiffInstrCommit, DiffTrapEvent, DifftestModule}
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class Writeback extends Module{
    val io = IO(new Bundle{
        val mem2wb  = Flipped(DecoupledIO(new MEM2RB))
        val ex2wb   = Flipped(DecoupledIO(new MEM2RB))
        val stall   = Output(Bool())
        val wReg    = Flipped(new RegWrite)
        val wCsr    = Flipped(new CSRWrite)
        val excep   = Output(new Exception)
        val wb2if   = Output(new ForceJmp)
        val recov   = Output(Bool())
        val updateTrace = Output(new UpdateTrace)
    })
    dontTouch(io)
    val recov_r     = RegInit(false.B)
    val stall_r     = RegInit(false.B)
    recov_r := false.B;  stall_r := false.B
    val forceJmp    = RegInit(0.U.asTypeOf(new ForceJmp))
    val tlb_r       = RegInit(false.B)
    val cache_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)
    val rfwen_r     = RegInit(false.B)
    val wdest_r     = RegEnable(io.wReg.id, io.wReg.en)
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val rcsr_id_r   = RegInit(0.U(CSR_WIDTH.W))
    valid_r         := false.B
    rfwen_r         := false.B
    tlb_r           := false.B
    cache_r         := false.B
    forceJmp.valid  := false.B
    excep_r.en      := false.B
    rcsr_id_r       := 0.U

    io.recov        := recov_r
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(PADDR_WIDTH.W))
    io.wReg.id      := Mux(io.mem2wb.valid, io.mem2wb.bits.dst, io.ex2wb.bits.dst)
    io.wReg.data    := Mux(io.mem2wb.valid, io.mem2wb.bits.dst_d, io.ex2wb.bits.dst_d)
    io.wReg.en      := false.B
    io.wCsr.id      := Mux(io.mem2wb.valid, io.mem2wb.bits.csr_id, io.ex2wb.bits.csr_id)
    io.wCsr.data    := Mux(io.mem2wb.valid, io.mem2wb.bits.csr_d, io.ex2wb.bits.csr_d)
    io.wCsr.en      := false.B
    io.excep        := io.ex2wb.bits.excep
    io.excep.en     := false.B
    io.wb2if        := forceJmp
    // io.mem2wb.ready := false.B
    io.stall := stall_r
    // io.ex2wb.ready := false.B
    io.mem2wb.ready := true.B
    io.ex2wb.ready := true.B
    when(io.mem2wb.valid){
        // io.mem2wb.ready := true.B
        io.wReg.en      := io.mem2wb.bits.dst_en
        io.wCsr.en      := io.mem2wb.bits.csr_en
        io.excep.en     := io.mem2wb.bits.excep.en
        valid_r := true.B
        rfwen_r := io.mem2wb.bits.dst_en
        inst_r  := io.mem2wb.bits.inst
        pc_r    := io.mem2wb.bits.pc
        recov_r := io.mem2wb.bits.recov
        excep_r := io.mem2wb.bits.excep
        rcsr_id_r   := io.mem2wb.bits.rcsr_id
        when(io.mem2wb.bits.recov && !io.mem2wb.bits.excep.en){
            forceJmp.valid  := true.B
            forceJmp.seq_pc := io.mem2wb.bits.pc + 4.U
        }
    }.elsewhen(io.ex2wb.valid) {
        // io.ex2wb.ready := true.B
        io.wReg.en      := io.ex2wb.bits.dst_en
        io.wCsr.en      := io.ex2wb.bits.csr_en
        io.excep.en     := io.ex2wb.bits.excep.en
        valid_r         := true.B
        rfwen_r         := io.ex2wb.bits.dst_en
        inst_r          := io.ex2wb.bits.inst
        pc_r            := io.ex2wb.bits.pc
        recov_r := io.ex2wb.bits.recov
        excep_r := io.ex2wb.bits.excep
        rcsr_id_r   := io.ex2wb.bits.rcsr_id
        when(io.ex2wb.bits.recov && !io.ex2wb.bits.excep.en){
            forceJmp.valid  := true.B
            forceJmp.seq_pc := io.ex2wb.bits.pc + 4.U
        }
    }
    io.updateTrace.valid := io.ex2wb.valid
    io.updateTrace.inst := io.ex2wb.bits.inst
    io.updateTrace.pc := io.ex2wb.bits.pc
    if (false) {
        val is_mmio_r   = RegNext(io.mem2wb.bits.is_mmio && io.mem2wb.valid)
        val instFinish = Module(new InstFinish)
        instFinish.io.clock     := clock
        instFinish.io.is_mmio   := is_mmio_r
        instFinish.io.valid     := valid_r
        instFinish.io.pc        := pc_r
        instFinish.io.inst      := inst_r
        instFinish.io.rcsr_id   := rcsr_id_r

        val transExcep = Module(new TransExcep)
        transExcep.io.clock     := clock
        transExcep.io.intr      := excep_r.en && excep_r.etype === 0.U
        transExcep.io.cause     := excep_r.cause
        transExcep.io.pc        := excep_r.pc
    }

    if (isSim) {
        val is_mmio_r = RegNext(io.mem2wb.bits.is_mmio && io.mem2wb.valid)
        val is_timer_r = rcsr_id_r === CSR_MCYCLE
        val difftest = DifftestModule(new DiffInstrCommit, dontCare = true)
        difftest.valid := valid_r
        difftest.skip := is_mmio_r || is_timer_r
        difftest.rfwen := rfwen_r
        difftest.wdest := wdest_r
        difftest.pc := pc_r
        difftest.instr := inst_r
    }

    if (isSim) {
        val difftest = DifftestModule(new DiffArchEvent, dontCare = true)
        difftest.valid := false.B
        difftest.interrupt := excep_r.etype === 0.U
        difftest.exception := excep_r.cause
        difftest.exceptionPC := excep_r.pc
    }

    if (isSim) {
        val timer = RegInit(0.U(64.W))
        timer := timer + 1.U
        val difftest = DifftestModule(new DiffTrapEvent, dontCare = true)
        difftest.hasTrap := false.B
        difftest.cycleCnt := timer
    }
}

class InstFinish extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val clock   = Input(Clock())
        val is_mmio = Input(Bool())
        val valid   = Input(Bool())
        val pc      = Input(UInt(PADDR_WIDTH.W))
        val inst    = Input(UInt(INST_WIDTH.W))
        val rcsr_id = Input(UInt(CSR_WIDTH.W))
    })
    addPath("playground/src/interface/InstFinish.v")
}

class TransExcep extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val clock   = Input(Clock())
        val intr    = Input(Bool())
        val cause   = Input(UInt(DATA_WIDTH.W))
        val pc      = Input(UInt(PADDR_WIDTH.W))
    })
    addPath("playground/src/interface/TransExcep.v")
}