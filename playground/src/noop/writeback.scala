package noop.writeback

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class Writeback extends Module{
    val io = IO(new Bundle{
        val mem2wb  = Flipped(new MEM2RB)
        val ex2wb  = Flipped(new MEM2RB)
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
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val rcsr_id_r   = RegInit(0.U(CSR_WIDTH.W))
    valid_r         := false.B
    tlb_r           := false.B
    cache_r         := false.B
    forceJmp.valid  := false.B
    excep_r.en      := false.B
    rcsr_id_r       := 0.U

    io.recov        := recov_r
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(PADDR_WIDTH.W))
    io.wReg.id      := Mux(io.mem2wb.valid, io.mem2wb.dst, io.ex2wb.dst)
    io.wReg.data    := Mux(io.mem2wb.valid, io.mem2wb.dst_d, io.ex2wb.dst_d)
    io.wReg.en      := false.B
    io.wCsr.id      := Mux(io.mem2wb.valid, io.mem2wb.csr_id, io.ex2wb.csr_id)
    io.wCsr.data    := Mux(io.mem2wb.valid, io.mem2wb.csr_d, io.ex2wb.csr_d)
    io.wCsr.en      := false.B
    io.excep        := io.ex2wb.excep
    io.excep.en     := false.B
    io.wb2if        := forceJmp
    // io.mem2wb.ready := false.B
    io.mem2wb.stall := stall_r
    io.ex2wb.stall := stall_r
    // io.ex2wb.ready := false.B
    io.mem2wb.ready := true.B
    io.ex2wb.ready := true.B
    when(io.mem2wb.valid){
        // io.mem2wb.ready := true.B
        io.wReg.en      := io.mem2wb.dst_en
        io.wCsr.en      := io.mem2wb.csr_en
        io.excep.en     := io.mem2wb.excep.en
        valid_r := true.B
        inst_r  := io.mem2wb.inst
        pc_r    := io.mem2wb.pc
        recov_r := io.mem2wb.recov
        excep_r := io.mem2wb.excep
        rcsr_id_r   := io.mem2wb.rcsr_id
        when(io.mem2wb.recov && !io.mem2wb.excep.en){
            forceJmp.valid  := true.B
            forceJmp.seq_pc := io.mem2wb.pc + 4.U
        }
    }.elsewhen(io.ex2wb.valid) {
        // io.ex2wb.ready := true.B
        io.wReg.en      := io.ex2wb.dst_en
        io.wCsr.en      := io.ex2wb.csr_en
        io.excep.en     := io.ex2wb.excep.en
        valid_r         := true.B
        inst_r          := io.ex2wb.inst
        pc_r            := io.ex2wb.pc
        recov_r := io.ex2wb.recov
        excep_r := io.ex2wb.excep
        rcsr_id_r   := io.ex2wb.rcsr_id
        when(io.ex2wb.recov && !io.ex2wb.excep.en){
            forceJmp.valid  := true.B
            forceJmp.seq_pc := io.ex2wb.pc + 4.U
        }
    }
    io.updateTrace.valid := io.ex2wb.valid
    io.updateTrace.inst := io.ex2wb.inst
    io.updateTrace.pc := io.ex2wb.pc
    if (isSim) {
        val is_mmio_r   = RegNext(io.mem2wb.is_mmio && io.mem2wb.valid)
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