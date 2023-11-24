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
        val ex2wb   = Vec(ISSUE_WIDTH, Flipped(ValidIO(new MEM2RB)))
        val d_wb    = Vec(ISSUE_WIDTH, Output(new RegForward))
        val wReg    = Vec(ISSUE_WIDTH, Output(new RegWrite))
        val wCsr    = Flipped(new CSRWrite)
        val excep   = Output(new Exception)
        val wb2if   = Output(new ForceJmp)
        val recov   = Output(Bool())
    })
    val writeback_ports = Seq(io.ex2wb.head, io.mem2wb) +: io.ex2wb.tail.map(Seq(_))
    val wb_valid = writeback_ports.map(ports => {
        assert(PopCount(ports.map(_.valid)) <= 1.U, "do not allow simultaneous writeback now")
        VecInit(ports.map(_.valid)).asUInt.orR
    })
    val writebacks = writeback_ports.map(ports => Mux1H(ports.map(_.valid), ports.map(_.bits)))

    // data forwarding
    for ((d, w) <- io.d_wb.zip(io.ex2wb)) {
        d.id := w.bits.dst
        d.data := w.bits.dst_d
        d.state := Mux(w.valid && w.bits.ctrl.writeRegEn, d_valid, d_invalid)
    }

    // register file write
    for ((wReg, (v, wb)) <- io.wReg.zip(wb_valid.zip(writebacks))) {
        wReg.id := wb.dst
        wReg.data := wb.dst_d
        wReg.en := v && wb.dst_en
    }

    // TODO: allow only the first instruction for now
    val wb_special_v = wb_valid.head
    val wb_special = writebacks.head

    io.wCsr.id := wb_special.csr_id
    io.wCsr.data := wb_special.csr_d
    io.wCsr.en := wb_special_v && wb_special.csr_en

    io.excep := wb_special.excep
    io.excep.en := wb_special_v && wb_special.excep.en

    // TODO: what is recov
    io.recov := RegNext(wb_special_v && wb_special.recov, false.B)

    val force_jump = wb_special_v && wb_special.recov && !wb_special.excep.en
    io.wb2if.valid := RegNext(force_jump, false.B)
    io.wb2if.seq_pc := RegEnable(wb_special.pc + 4.U, force_jump)

    if (isSim) {
        for (((v, wb), i) <- wb_valid.zip(writebacks).zipWithIndex) {
            val difftest = DifftestModule(new DiffInstrCommit, delay = 1, dontCare = true)
            difftest.index := i.U
            difftest.valid := v
            difftest.skip := wb.is_mmio || wb.rcsr_id === CSR_MCYCLE
            difftest.rfwen := wb.dst_en
            difftest.wdest := wb.dst
            difftest.pc := wb.pc
            difftest.instr := wb.inst
        }
    }

    if (isSim) {
        val difftest = DifftestModule(new DiffArchEvent, delay = 1, dontCare = true)
        difftest.valid := false.B
        difftest.interrupt := wb_special.excep.etype === 0.U
        difftest.exception := wb_special.excep.cause
        difftest.exceptionPC := wb_special.excep.pc
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