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
        val wReg    = Vec(ISSUE_WIDTH, Output(new RegWrite))
        val wCsr    = Flipped(new CSRWrite)
        val excep   = Output(new Exception)
        val wb2if   = Output(new ForceJmp)
        val recov   = Output(Bool())
    })
    // Dirty code here: mem2wb will choose either port 0 or port 1 for writeback
    val mem2wb0 = WireInit(io.mem2wb)
    mem2wb0.valid := io.mem2wb.valid && !io.ex2wb.head.valid
    val mem2wb1 = WireInit(io.mem2wb)
    mem2wb1.valid := io.mem2wb.valid && io.ex2wb.head.valid

    val writeback_ports = Seq(
        Seq(io.ex2wb(0), mem2wb0),
        Seq(io.ex2wb(1), mem2wb1)
    ) ++ io.ex2wb.drop(2).map(Seq(_))
    val wb_valid = writeback_ports.map(ports => {
        assert(PopCount(ports.map(_.valid)) <= 1.U, "do not allow simultaneous writeback now")
        VecInit(ports.map(_.valid)).asUInt.orR
    })
    val writebacks = writeback_ports.map(ports => Mux1H(ports.map(_.valid), ports.map(_.bits)))

    // register file write
    for ((wReg, (v, wb)) <- io.wReg.zip(wb_valid.zip(writebacks))) {
        wReg.id := wb.dst
        wReg.data := wb.dst_d
        wReg.en := v && wb.dst_en
    }

    val csr_wen = wb_valid.zip(writebacks).map{ case (v, w) => v && w.csr_en }
    assert(PopCount(csr_wen) <= 1.U, "do not allow simultaneous csr_wen now")
    val csr_wb = PriorityMux(csr_wen, writebacks)
    io.wCsr.id := csr_wb.csr_id
    io.wCsr.data := csr_wb.csr_d
    io.wCsr.en := VecInit(csr_wen).asUInt.orR

    val excep_en = io.ex2wb.map(wb => wb.valid && wb.bits.excep.en)
    assert(PopCount(excep_en) <= 1.U, "do not allow simultaneous excep_en now")
    val excep_wb = PriorityMux(excep_en, writebacks)
    io.excep := excep_wb.excep
    io.excep.en := VecInit(excep_en).asUInt.orR
    io.excep.pc := excep_wb.dst_d

    val recov_en = wb_valid.zip(writebacks).map{ case (v, w) => v && w.recov }
    io.recov := RegNext(VecInit(recov_en).asUInt.orR, false.B)

    val wb2if = wb_valid.zip(writebacks).map{ case (v, w) => v && w.recov && !w.excep.en }
    assert(PopCount(wb2if) <= 1.U, "do not allow simultaneous wb2if_valid now")
    val wb2if_wb = PriorityMux(wb2if, writebacks)
    val wb2if_valid = VecInit(wb2if).asUInt.orR
    io.wb2if.valid := RegNext(wb2if_valid, false.B)
    io.wb2if.seq_pc := RegEnable(wb2if_wb.excep.tval, wb2if_valid)

    if (isSim) {
        for (((v, wb), i) <- wb_valid.zip(writebacks).zipWithIndex) {
            val difftest = DifftestModule(new DiffInstrCommit, delay = 1, dontCare = true)
            difftest.index := i.U
            difftest.valid := v
            difftest.skip := wb.is_mmio || wb.rcsr_id === CSR_MCYCLE
            difftest.rfwen := wb.dst_en
            difftest.wdest := wb.dst
            difftest.pc := wb.pc << 2
            difftest.instr := wb.inst
        }
    }

    if (isSim) {
        val difftest = DifftestModule(new DiffArchEvent, delay = 1, dontCare = true)
        difftest.valid := false.B
        difftest.interrupt := excep_wb.excep.etype === 0.U
        difftest.exception := excep_wb.excep.cause
        difftest.exceptionPC := io.excep.pc
    }

    if (isSim) {
        val instrCnt = RegInit(0.U(64.W))
        instrCnt := instrCnt + PopCount(wb_valid)
        val timer = RegInit(0.U(64.W))
        timer := timer + 1.U
        val difftest = DifftestModule(new DiffTrapEvent, dontCare = true)
        difftest.hasTrap := false.B
        difftest.cycleCnt := timer
        difftest.instrCnt := instrCnt
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