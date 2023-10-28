package noop.writeback

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class Writeback extends Module{
    val io = IO(new Bundle{
        val mem2rb  = Flipped(new MEM2RB)
        val wReg    = Flipped(new RegWrite)
        val wCsr    = Flipped(new CSRWrite)
        val excep   = Output(new Exception)
        val wb2if   = Output(new ForceJmp)
        val recov   = Output(Bool())
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
    val pc_r        = RegInit(0.U(VADDR_WIDTH.W))
    io.wReg.id      := io.mem2rb.dst
    io.wReg.data    := io.mem2rb.dst_d
    io.wReg.en      := false.B
    io.wCsr.id      := io.mem2rb.csr_id
    io.wCsr.data    := io.mem2rb.csr_d
    io.wCsr.en      := false.B
    io.excep        := io.mem2rb.excep
    io.excep.en     := false.B
    io.wb2if        := forceJmp
    io.mem2rb.ready := false.B
    io.mem2rb.stall := stall_r
    when(io.mem2rb.valid){
        io.mem2rb.ready := true.B
        io.wReg.en      := io.mem2rb.dst_en
        io.wCsr.en      := io.mem2rb.csr_en
        io.excep.en     := io.mem2rb.excep.en
        valid_r := true.B
        inst_r  := io.mem2rb.inst
        pc_r    := io.mem2rb.pc
        recov_r := io.mem2rb.recov
        excep_r := io.mem2rb.excep
        rcsr_id_r   := io.mem2rb.rcsr_id
        when(io.mem2rb.special =/= 0.U || (io.mem2rb.recov && !io.mem2rb.excep.en)){
            forceJmp.valid  := true.B
            forceJmp.seq_pc := io.mem2rb.pc + 4.U
        }
    }
    val is_mmio_r   = RegNext(io.mem2rb.is_mmio)
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

class InstFinish extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val clock   = Input(Clock())
        val is_mmio = Input(Bool())
        val valid   = Input(Bool())
        val pc      = Input(UInt(VADDR_WIDTH.W))
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
        val pc      = Input(UInt(VADDR_WIDTH.W))
    })
    addPath("playground/src/interface/TransExcep.v")
}