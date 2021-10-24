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
        val flush_tlb = Output(Bool())
        val flush_cache = Output(Bool())
    })
    dontTouch(io)
    val drop_r = RegInit(false.B)
    drop_r := false.B
    val forceJmp    = RegInit(0.U.asTypeOf(new ForceJmp))
    val tlb_r       = RegInit(false.B)
    val cache_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)
    valid_r         := false.B
    tlb_r           := false.B
    cache_r         := false.B
    forceJmp.valid  := false.B

    io.mem2rb.drop  := drop_r
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
    io.flush_tlb    := tlb_r
    io.flush_cache  := cache_r
    io.wb2if        := forceJmp
    io.mem2rb.ready := false.B
    when(!drop_r){
        when(io.mem2rb.valid){
            io.mem2rb.ready := true.B
            io.wReg.en      := io.mem2rb.dst_en
            io.wCsr.en      := io.mem2rb.csr_en
            io.excep.en     := io.mem2rb.excep.en
            valid_r := true.B
            inst_r  := io.mem2rb.inst
            pc_r    := io.mem2rb.pc
            when(io.mem2rb.excep.en){
                drop_r := true.B
            }
            when(io.mem2rb.special =/= 0.U){
                drop_r := true.B
                forceJmp.valid  := true.B
                forceJmp.seq_pc := io.mem2rb.next_pc
                when(io.mem2rb.special === SPECIAL_FENCE_I){
                    cache_r := true.B
                }.otherwise{
                    tlb_r   := true.B
                }
            }
        }
    }
    val instFinish = Module(new InstFinish)
    instFinish.io.clock     := clock
    instFinish.io.is_mmio   := false.B
    instFinish.io.valid     := valid_r
    instFinish.io.pc        := pc_r
    instFinish.io.inst      := inst_r
    instFinish.io.rcsr_id   := 0.U

    val transExcep = Module(new TransExcep)
    transExcep.io.clock     := clock
    transExcep.io.intr      := 0.U
    transExcep.io.cause     := 0.U
    transExcep.io.pc        := 0.U

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