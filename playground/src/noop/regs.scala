package noop.regs

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.regs_config._
import noop.datapath._

class Regs extends Module{
    val io = IO(new Bundle{
        val rs1 = new RegRead
        val rs2 = new RegRead
        val dst = new RegWrite
    })
    val regs = RegInit(VecInit(Seq.fill(32)(0.U(DATA_WIDTH.W))))
    io.rs1.data := regs(io.rs1.id)
    io.rs2.data := regs(io.rs2.id)
    when(io.dst.en && io.dst.id =/= 0.U){
        regs(io.dst.id) := io.dst.data
    }
    if (isSim) {
        val updateRegs = Module(new UpdateRegs)
        updateRegs.io.regs_data := regs.asUInt
        updateRegs.io.clock := clock
    }
}

class Csrs extends Module{
    val io = IO(new Bundle{
        val rs      = new CSRRead
        val rd      = new CSRWrite
        val excep   = Input(new Exception)
        // val mmuState = Output(new MmuState)
        val idState = Output(new IdState)
        val reg2if  = Output(new ForceJmp)
        // val intr_out = Output(new RaiseIntr)
        // val intr_msip = Input(new Intr)
    })
    val priv        = RegInit(PRV_M)
    // val misa        = RegInit("h800000000014112d".U(DATA_WIDTH.W))
    val mstatus     = RegInit("ha00000000".U(DATA_WIDTH.W))
    val mepc        = RegInit(0.U(DATA_WIDTH.W))
    val mtval       = RegInit(0.U(DATA_WIDTH.W))
    val mscratch    = RegInit(0.U(DATA_WIDTH.W))
    val mcause      = RegInit(0.U(DATA_WIDTH.W))
    val mtvec       = RegInit(0.U(DATA_WIDTH.W))
    val mie         = RegInit(0.U(DATA_WIDTH.W))
    val mip         = RegInit(0.U(DATA_WIDTH.W))
    val mcycle      = RegInit(0.U(DATA_WIDTH.W))
    mcycle := mcycle + 1.U


    io.idState.priv    := priv
    val forceJmp        = RegInit(0.U.asTypeOf(new ForceJmp))
    io.reg2if           := forceJmp
    forceJmp.valid      := false.B
    val cause = io.excep.cause
    when(io.excep.en){
        when(io.excep.etype === ETYPE_MRET){ //mret
            forceJmp.seq_pc := mepc
            forceJmp.valid  := true.B
            val ms          = mstatus
            priv            := ms(12,11)
            val new_mstatus = Cat(ms(63,13), PRV_U, ms(10,8), 1.U(1.W), ms(6,4), ms(7), ms(2,0))
            mstatus         := new_mstatus
        }.otherwise{
            // exceptions & interruptions & ecall
            val seq_pc      = mtvec + Mux(mtvec(1), cause << 2.U, 0.U)
            forceJmp.seq_pc := seq_pc
            forceJmp.valid  := true.B
            mcause          := cause
            mepc            := io.excep.pc
            val ms          = mstatus
            val new_mstatus = Cat(ms(63,13), priv(1,0), ms(10,8), ms(3), ms(6,4), 0.U(1.W), ms(2,0))
            mstatus         := new_mstatus
            mtval           := io.excep.tval
            priv            := PRV_M
        }
    }
// intr
   
// csr read
    io.rs.is_err    := false.B
    when(io.rs.id === CSR_MSTATUS){
        io.rs.data := mstatus
    }.elsewhen(io.rs.id === CSR_MEPC){
        io.rs.data := mepc
    }.elsewhen(io.rs.id === CSR_MTVAL){
        io.rs.data := mtval
    }.elsewhen(io.rs.id === CSR_MSCRATCH){
        io.rs.data := mscratch
    }.elsewhen(io.rs.id === CSR_MTVEC){
        io.rs.data := mtvec
    }.elsewhen(io.rs.id === CSR_MIE){
        io.rs.data := mie
    }.elsewhen(io.rs.id === CSR_MIP){
        io.rs.data := mip
    }.elsewhen(io.rs.id === CSR_MCAUSE){
        io.rs.data := mcause
    }.elsewhen(io.rs.id === CSR_MCYCLE) {
        io.rs.data := mcycle
    }.otherwise{
        io.rs.data      := 0.U
        io.rs.is_err    := true.B
    }
    when(!io.rd.en){
    }.elsewhen(io.rd.id === CSR_MSTATUS){
        val new_mstatus = io.rd.data & MSTATUS_MASK
        val sd          = Mux((io.rd.data(14,13) === 3.U) || (io.rd.data(16,15) === 3.U), MSTATUS64_SD, 0.U)
        mstatus := set_partial_val(mstatus, MSTATUS_MASK | MSTATUS64_SD, new_mstatus | sd)
    }.elsewhen(io.rd.id === CSR_MEPC){
        mepc := io.rd.data
    }.elsewhen(io.rd.id === CSR_MTVAL){
        mtval := io.rd.data
    }.elsewhen(io.rd.id === CSR_MSCRATCH){
        mscratch := io.rd.data
    }.elsewhen(io.rd.id === CSR_MTVEC){
        mtvec := io.rd.data
    }.elsewhen(io.rd.id === CSR_MIE){
        mie := io.rd.data
    }.elsewhen(io.rd.id === CSR_MIP){
        // mip := set_partial_val(mip, W_MIP_MASK, io.rd.data) | (intr_seip << IRQ_S_EXT.U)
    }.elsewhen(io.rd.id === CSR_MCAUSE){
        mcause := io.rd.data
    }.otherwise{

    }
    if (isSim) {
        val updateCsrs = Module(new UpdateCsrs)
        updateCsrs.io.priv      := priv
        updateCsrs.io.mstatus   := Cat(CSR_MSTATUS,mstatus)
        updateCsrs.io.mepc      := Cat(CSR_MEPC,mepc)
        updateCsrs.io.mtval     := Cat(CSR_MTVAL,mtval)
        updateCsrs.io.mscratch  := Cat(CSR_MSCRATCH,mscratch)
        updateCsrs.io.mcause    := Cat(CSR_MCAUSE,mcause)
        updateCsrs.io.mtvec     := Cat(CSR_MTVEC,mtvec)
        updateCsrs.io.mie       := Cat(CSR_MIE,mie)
        updateCsrs.io.mip       := Cat(CSR_MIP,mip)

        updateCsrs.io.clock     := clock
    }
}

class UpdateRegs extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val regs_data   = Input(UInt((32*DATA_WIDTH).W))
        val clock       = Input(Clock())

    })
    addPath("playground/src/interface/UpdateRegs.v")
}

class UpdateCsrs extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val priv        = Input(UInt(2.W))
        val mstatus     = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mepc        = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mtval       = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mscratch    = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mcause      = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mtvec       = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mie         = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mip         = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val clock       = Input(Clock())

    })
    addPath("playground/src/interface/UpdateCsrs.v")
}