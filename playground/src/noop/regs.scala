package noop.regs

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.regs_config._
import noop.datapath._
import csr_config._

object csr_config{
    val CSR_SEPC        = 0x141.U
    val CSR_STVEC       = 0x105.U
    val CSR_SCAUSE      = 0x142.U
    val CSR_STVAL       = 0x143.U
    val CSR_SSCRATCH    = 0x140.U
    val CSR_SSTATUS     = 0x100.U
    val CSR_SATP        = 0x180.U
    val CSR_SIE         = 0x104.U
    val CSR_SIP         = 0x144.U
    val CSR_MTVEC       = 0x305.U
    val CSR_MEPC        = 0x341.U
    val CSR_MCAUSE      = 0x342.U
    val CSR_MIE         = 0x304.U
    val CSR_MIP         = 0x344.U
    val CSR_MTVAL       = 0x343.U
    val CSR_MSCRATCH    = 0x340.U
    val CSR_MSTATUS     = 0x300.U
    val CSR_MHARTID     = 0xf14.U
    val CSR_MEDELEG     = 0x302.U
    val CSR_MIDELEG     = 0x303.U
    val CSR_PMPADDR0    = 0x3b0.U
    val CSR_PMPADDR1    = 0x3b1.U
    val CSR_PMPADDR2    = 0x3b2.U
    val CSR_PMPADDR3    = 0x3b3.U
    val CSR_PMPCFG0     = 0x3a0.U
    val CSR_USCRATCH    = 0x40.U
    val CSR_MISA        = 0x301.U
    val CSR_SCOUNTEREN  = 0x106.U
    val CSR_MCOUNTEREN  = 0x306.U

    val MEDELEG_MASK = ((1 << CAUSE_MISALIGNED_FETCH) | (1 << CAUSE_BREAKPOINT) |
                    (1 << CAUSE_USER_ECALL) | (1 << CAUSE_SUPERVISOR_ECALL) |
                    (1 << CAUSE_FETCH_PAGE_FAULT) | (1 << CAUSE_LOAD_PAGE_FAULT) |
                    (1 << CAUSE_STORE_PAGE_FAULT)).U(DATA_WIDTH.W)

    val SUP_INTS = MIP_SSIP | MIP_STIP | MIP_SEIP

    val SSTATUS_MASK = SSTATUS_SIE | SSTATUS_SPIE | SSTATUS_SPP | SSTATUS_FS |
                        SSTATUS_XS | SSTATUS_SUM | SSTATUS_MXR | SSTATUS64_SD
    val MSTATUS_MASK = MSTATUS_MIE | MSTATUS_MPIE | MSTATUS_MPRV | MSTATUS_SIE | MSTATUS_SPIE |
                        MSTATUS_TW | MSTATUS_TSR | MSTATUS_MXR | MSTATUS_SUM | MSTATUS_TVM |
                        MSTATUS_FS | MSTATUS_VS | MSTATUS_SPP | MSTATUS_MPP
    val PMPADDR_MASK = "h3fffffffffffff".U(DATA_WIDTH.W)
    val W_SATP_MASK = "hf0000fffffffffff".U(DATA_WIDTH.W)

    def set_partial_val(preVal: UInt, mask: UInt, newVal: UInt) = {
        (preVal & ~mask) | (newVal & mask)
    }

}

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
    val updateRegs = Module(new UpdateRegs)
    updateRegs.io.regs_data := regs.asUInt
    updateRegs.io.clock := clock
}

class Csrs extends Module{
    val io = IO(new Bundle{
        val rs      = new CSRRead
        val rd      = new CSRWrite
        val excep   = Input(new Exception)
        val mmuState = Output(new MmuState)
        val idState = Output(new IdState)
        val reg2if  = Output(new ForceJmp)
        val intr_out = new RaiseIntr // TODO
    })
    val priv        = RegInit(PRV_M)
    val misa        = RegInit("h800000000014112d".U(DATA_WIDTH.W))
    val mstatus     = RegInit(0.U(DATA_WIDTH.W))
    val mepc        = RegInit(0.U(DATA_WIDTH.W))
    val mtval       = RegInit(0.U(DATA_WIDTH.W))
    val mscratch    = RegInit(0.U(DATA_WIDTH.W))
    val mcause      = RegInit(0.U(DATA_WIDTH.W))
    val mtvec       = RegInit(0.U(DATA_WIDTH.W))
    val mie         = RegInit(0.U(DATA_WIDTH.W))
    val mip         = RegInit(0.U(DATA_WIDTH.W))
    val medeleg     = RegInit(0.U(DATA_WIDTH.W))
    val mideleg     = RegInit(0.U(DATA_WIDTH.W))
    val mcounteren  = RegInit(0.U(32.W))
    val scounteren  = RegInit(0.U(32.W))
    val sepc        = RegInit(0.U(DATA_WIDTH.W))
    val stval       = RegInit(0.U(DATA_WIDTH.W))
    val sscratch    = RegInit(0.U(DATA_WIDTH.W))
    val stvec       = RegInit(0.U(DATA_WIDTH.W))
    val satp        = RegInit(0.U(DATA_WIDTH.W))
    val scause      = RegInit(0.U(DATA_WIDTH.W))
    val pmpaddr0    = RegInit(0.U(DATA_WIDTH.W))
    val pmpaddr1    = RegInit(0.U(DATA_WIDTH.W))
    val pmpaddr2    = RegInit(0.U(DATA_WIDTH.W))
    val pmpaddr3    = RegInit(0.U(DATA_WIDTH.W))
    val uscratch    = RegInit(0.U(DATA_WIDTH.W))
    val pmpcfg0     = RegInit(0.U(DATA_WIDTH.W))
    val mhartid     = RegInit(0.U(DATA_WIDTH.W))
    val sstatus     = mstatus & SSTATUS_MASK

    io.mmuState.priv    := priv
    io.mmuState.mstatus := mstatus
    io.mmuState.satp    := satp
    io.idState.priv    := priv
    io.intr_out.en      := false.B
    io.intr_out.cause   := 0.U
    val forceJmp        = RegInit(0.U.asTypeOf(new ForceJmp))
    io.reg2if           := forceJmp
    forceJmp.valid      := false.B
    val cause = io.excep.cause

    when(io.excep.en){
        when(io.excep.etype === ETYPE_SRET){ //sret
            forceJmp.seq_pc := sepc
            forceJmp.valid  := true.B
            val ss          = sstatus
            priv            := Cat(0.U(1.W), ss(8))
            val new_sstatus = Cat(ss(63,9), 0.U(1.W), ss(7,6), 1.U(1.W), ss(4,2), ss(5), ss(0))
            mstatus         := set_partial_val(mstatus, SSTATUS_MASK, new_sstatus)
            // io.clear_lr := true.B
        }.elsewhen(io.excep.etype === ETYPE_MRET){ //mret
            forceJmp.seq_pc := mepc
            forceJmp.valid  := true.B
            val ms          = mstatus
            priv            := ms(12,11)
            val new_mstatus = Cat(ms(63,13), PRV_U, ms(10,8), 1.U(1.W), ms(6,4), ms(7), ms(2,0))
            mstatus         := new_mstatus
            // io.clear_lr := true.B
        }.otherwise{
            // exceptions & interruptions & ecall
            val deleg = Mux(cause(63), mideleg, medeleg)
            when(priv <= PRV_S && deleg(cause(62,0))){
                // printf("S-mode priv: %d cause: %x pc: %x mtime: %x\n", priv, io.excep.cause, io.excep.pc, mtime)
                val seq_pc = stvec + Mux(stvec(1) === 1.U, cause << 2.U, 0.U)
                forceJmp.seq_pc := seq_pc
                forceJmp.valid  := true.B
                scause          := cause
                sepc            := io.excep.pc
                val ss          = sstatus
                val new_sstatus = Cat(ss(63,9), priv(0), ss(7,6), ss(1), ss(4,2), 0.U(1.W), ss(0))
                mstatus         := set_partial_val(mstatus, SSTATUS_MASK, new_sstatus)
                stval           := io.excep.tval
                priv            := PRV_S
            }.otherwise{
                // printf("M-mode priv: %d cause: %x pc: %x mtime: %x\n", priv, io.excep.cause, io.excep.pc, mtime)
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
    }
    io.rs.is_err    := false.B
    when(io.rs.id === CSR_MISA){
        io.rs.data := misa
    }.elsewhen(io.rs.id === CSR_MSTATUS){
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
    }.elsewhen(io.rs.id === CSR_MEDELEG){
        io.rs.data := medeleg
    }.elsewhen(io.rs.id === CSR_MIDELEG){
        io.rs.data := mideleg
    }.elsewhen(io.rs.id === CSR_MCOUNTEREN){
        io.rs.data := mcounteren
    }.elsewhen(io.rs.id === CSR_SCOUNTEREN){
        io.rs.data := scounteren
    }.elsewhen(io.rs.id === CSR_SEPC){
        io.rs.data := sepc
    }.elsewhen(io.rs.id === CSR_STVAL){
        io.rs.data := stval
    }.elsewhen(io.rs.id === CSR_SSCRATCH){
        io.rs.data := sscratch
    }.elsewhen(io.rs.id === CSR_STVEC){
        io.rs.data := stvec
    }.elsewhen(io.rs.id === CSR_SATP){
        io.rs.data := satp
    }.elsewhen(io.rs.id === CSR_SCAUSE){
        io.rs.data := scause
    }.elsewhen(io.rs.id === CSR_SSTATUS){
        io.rs.data := sstatus
    }.elsewhen(io.rs.id === CSR_SIE){
        io.rs.data := mie & mideleg
    }.elsewhen(io.rs.id === CSR_SIP){
        io.rs.data := mip & SUP_INTS
    }.elsewhen(io.rs.id === CSR_PMPADDR0){
        io.rs.data := pmpaddr0
    }.elsewhen(io.rs.id === CSR_PMPADDR1){
        io.rs.data := pmpaddr1
    }.elsewhen(io.rs.id === CSR_PMPADDR2){
        io.rs.data := pmpaddr2
    }.elsewhen(io.rs.id === CSR_PMPADDR3){
        io.rs.data := pmpaddr3
    }.elsewhen(io.rs.id === CSR_PMPCFG0){
        io.rs.data := pmpaddr3
    }.elsewhen(io.rs.id === CSR_USCRATCH){
        io.rs.data := uscratch
    }.elsewhen(io.rs.id === CSR_MHARTID){
        io.rs.data := mhartid
    }.otherwise{
        io.rs.data      := 0.U
        io.rs.is_err    := true.B
    }
    when(!io.rd.en){
    }.elsewhen(io.rd.id === CSR_MISA){
        misa :=  io.rd.data
    }.elsewhen(io.rd.id === CSR_MSTATUS){
        val new_mstatus = io.rd.data & MSTATUS_MASK
        val sd          = Mux((io.rd.data(14,13) === 3.U) || (io.rd.data(16,15) === 3.U), MSTATUS64_SD, 0.U)
        mstatus := set_partial_val(mstatus, MSTATUS_MASK, new_mstatus) | sd
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
        mip := io.rd.data
    }.elsewhen(io.rd.id === CSR_MCAUSE){
        mcause := io.rd.data
    }.elsewhen(io.rd.id === CSR_MEDELEG){
        medeleg := io.rd.data & MEDELEG_MASK
    }.elsewhen(io.rd.id === CSR_MIDELEG){
        mideleg := io.rd.data & SUP_INTS
    }.elsewhen(io.rd.id === CSR_MCOUNTEREN){
        mcounteren := io.rd.data
    }.elsewhen(io.rd.id === CSR_SCOUNTEREN){
        scounteren := io.rd.data
    }.elsewhen(io.rd.id === CSR_SEPC){
        sepc := io.rd.data
    }.elsewhen(io.rd.id === CSR_STVAL){
        stval := io.rd.data
    }.elsewhen(io.rd.id === CSR_SSCRATCH){
        sscratch := io.rd.data
    }.elsewhen(io.rd.id === CSR_STVEC){
        stvec := io.rd.data
    }.elsewhen(io.rd.id === CSR_SATP){
        satp := io.rd.data
    }.elsewhen(io.rd.id === CSR_SCAUSE){
        scause := io.rd.data
    }.elsewhen(io.rd.id === CSR_SSTATUS){
        mstatus := set_partial_val(mstatus, SSTATUS_MASK, io.rd.data)
    }.elsewhen(io.rd.id === CSR_SIE){
        mie := set_partial_val(mie, mideleg, io.rd.data)
    }.elsewhen(io.rd.id === CSR_SIP){
        mip := set_partial_val(mip, SUP_INTS, io.rd.data)
    }.elsewhen(io.rd.id === CSR_PMPADDR0){
        pmpaddr0 := io.rd.data & PMPADDR_MASK
    }.elsewhen(io.rd.id === CSR_PMPADDR1){
        pmpaddr1 := io.rd.data & PMPADDR_MASK
    }.elsewhen(io.rd.id === CSR_PMPADDR2){
        pmpaddr2 := io.rd.data & PMPADDR_MASK
    }.elsewhen(io.rd.id === CSR_PMPADDR3){
        pmpaddr3 := io.rd.data & PMPADDR_MASK
    }.elsewhen(io.rd.id === CSR_PMPCFG0){
        pmpcfg0 := io.rd.data
    }.elsewhen(io.rd.id === CSR_USCRATCH){
        uscratch := io.rd.data
    }.elsewhen(io.rd.id === CSR_MHARTID){
        mhartid := io.rd.data
    }.otherwise{

    }
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
    updateCsrs.io.medeleg   := Cat(CSR_MEDELEG,medeleg)
    updateCsrs.io.mideleg   := Cat(CSR_MIDELEG,mideleg)
    updateCsrs.io.sepc      := Cat(CSR_SEPC,sepc)
    updateCsrs.io.stval     := Cat(CSR_STVAL,stval)
    updateCsrs.io.sscratch  := Cat(CSR_SSCRATCH,sscratch)
    updateCsrs.io.stvec     := Cat(CSR_STVEC,stvec)
    updateCsrs.io.satp      := Cat(CSR_SATP,satp)
    updateCsrs.io.scause    := Cat(CSR_SCAUSE,scause)
    updateCsrs.io.clock     := clock
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
        val medeleg     = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val mideleg     = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val sepc        = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val stval       = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val sscratch    = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val stvec       = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val satp        = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val scause      = Input(UInt((CSR_WIDTH+DATA_WIDTH).W))
        val clock       = Input(Clock())

    })
    addPath("playground/src/interface/UpdateCsrs.v")
}