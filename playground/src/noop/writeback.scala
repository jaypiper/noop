package noop.writeback

import chisel3._
import chisel3.util._
import difftest.{ArchEvent, DiffArchEvent, DiffInstrCommit, DiffTrapEvent, DifftestModule}
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._

class Writeback extends Module{
    val io = IO(new Bundle{
        val mem2wb  = Flipped(ValidIO(new MEM2RB))
        val ex2wb   = Flipped(ValidIO(new MEM2RB))
        val d_wb    = Output(new RegForward)
        val wReg    = Flipped(new RegWrite)
        val wCsr    = Flipped(new CSRWrite)
        val excep   = Output(new Exception)
        val wb2if   = Output(new ForceJmp)
        val recov   = Output(Bool())
    })
    val wb_valid = io.ex2wb.valid || io.mem2wb.valid
    assert(!io.ex2wb.valid || !io.mem2wb.valid, "do not allow simultaneous writeback now")
    val wb = Mux(io.ex2wb.valid, io.ex2wb.bits, io.mem2wb.bits)

    io.recov := RegNext(wb_valid && wb.recov, false.B)

    // data forwarding
    io.d_wb.id := io.ex2wb.bits.dst
    io.d_wb.data := io.ex2wb.bits.dst_d
    io.d_wb.state := Mux(io.ex2wb.valid && io.ex2wb.bits.ctrl.writeRegEn, d_valid, d_invalid)

    io.wReg.id := wb.dst
    io.wReg.data := wb.dst_d
    io.wReg.en := wb_valid && wb.dst_en

    io.wCsr.id := wb.csr_id
    io.wCsr.data := wb.csr_d
    io.wCsr.en := wb_valid && wb.csr_en

    io.excep := wb.excep
    io.excep.en := wb_valid && wb.excep.en || io.ex2wb.valid &&io.ex2wb.bits.excep.en

    val force_jump = wb_valid && wb.recov && !wb.excep.en
    io.wb2if.valid := RegNext(force_jump, false.B)
    io.wb2if.seq_pc := RegEnable(wb.pc + 4.U, force_jump)

    if (false) {
        val is_mmio = io.mem2wb.bits.is_mmio && io.mem2wb.valid
        val instFinish = Module(new InstFinish)
        instFinish.io.clock     := clock
        instFinish.io.is_mmio   := RegEnable(is_mmio, wb_valid)
        instFinish.io.valid     := RegNext(wb_valid)
        instFinish.io.pc        := RegEnable(wb.pc, wb_valid)
        instFinish.io.inst      := RegEnable(wb.inst, wb_valid)
        instFinish.io.rcsr_id   := RegEnable(wb.rcsr_id, wb_valid)

        val transExcep = Module(new TransExcep)
        transExcep.io.clock     := clock
        transExcep.io.intr      := RegNext(wb.excep.en && wb.excep.etype === 0.U, false.B)
        transExcep.io.cause     := RegNext(wb.excep.cause, 0.U)
        transExcep.io.pc        := RegNext(wb.excep.pc)
    }

    if (isSim) {
        val is_mmio = io.mem2wb.bits.is_mmio && io.mem2wb.valid
        val is_timer = wb.rcsr_id === CSR_MCYCLE
        val difftest = DifftestModule(new DiffInstrCommit, delay = 1, dontCare = true)
        difftest.valid := wb_valid
        difftest.skip := is_mmio || is_timer
        difftest.rfwen := wb.dst_en
        difftest.wdest := io.wReg.id
        difftest.pc := wb.pc
        difftest.instr := wb.inst
    }

    if (isSim) {
        val difftest = DifftestModule(new DiffArchEvent, delay = 1, dontCare = true)
        difftest.valid := false.B
        difftest.interrupt := wb.excep.etype === 0.U
        difftest.exception := wb.excep.cause
        difftest.exceptionPC := wb.excep.pc
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