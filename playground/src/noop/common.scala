package noop.param

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._

object common extends mem_access_mode{
    val VADDR_WIDTH = 64
    val PADDR_WIDTH = 32
    val DATA_WIDTH  = 64
    val INST_WIDTH  = 32
    val REG_WIDTH   = 5
    val CSR_WIDTH   = 12
    val PC_START    = "h80000000".U(VADDR_WIDTH.W)

    val PAGE_WIDTH  = 12

}

trait mem_access_mode{
    val MEM_NONE    = 0.U(2.W)
    val MEM_FETCH   = 1.U(2.W)
    val MEM_LOAD    = 2.U(2.W)
    val MEM_STORE   = 3.U(2.W)
}

trait satp_mode{
    val Bare = 0.U(4.W)
    val Sv39 = 8.U(4.W)
    val Sv48 = 9.U(4.W)
}

trait dataForw{
    val d_invalid   = 0.U(2.W)
    val d_valid     = 1.U(2.W)
    val d_wait      = 2.U(2.W)
}

trait priv_encoding{
    val PRV_U           = 0.U(2.W)
    val PRV_S           = 1.U(2.W)
    val PRV_M           = 3.U(2.W)
    val CAUSE_MISALIGNED_FETCH = 0x0
    val CAUSE_FETCH_ACCESS = 0x1
    val CAUSE_ILLEGAL_INSTRUCTION = 0x2
    val CAUSE_BREAKPOINT = 0x3
    val CAUSE_MISALIGNED_LOAD = 0x4
    val CAUSE_LOAD_ACCESS = 0x5
    val CAUSE_MISALIGNED_STORE = 0x6
    val CAUSE_STORE_ACCESS = 0x7
    val CAUSE_USER_ECALL = 0x8
    val CAUSE_SUPERVISOR_ECALL = 0x9
    val CAUSE_VIRTUAL_SUPERVISOR_ECALL = 0xa
    val CAUSE_MACHINE_ECALL = 0xb
    val CAUSE_FETCH_PAGE_FAULT = 0xc
    val CAUSE_LOAD_PAGE_FAULT = 0xd
    val CAUSE_STORE_PAGE_FAULT = 0xf
    val CAUSE_FETCH_GUEST_PAGE_FAULT = 0x14
    val CAUSE_LOAD_GUEST_PAGE_FAULT = 0x15
    val CAUSE_VIRTUAL_INSTRUCTION = 0x16
    val CAUSE_STORE_GUEST_PAGE_FAULT = 0x17

    val SSTATUS_UIE     = "h00000001".U(DATA_WIDTH.W)
    val SSTATUS_SIE     = "h00000002".U(DATA_WIDTH.W)
    val SSTATUS_UPIE    = "h00000010".U(DATA_WIDTH.W)
    val SSTATUS_SPIE    = "h00000020".U(DATA_WIDTH.W)
    val SSTATUS_UBE     = "h00000040".U(DATA_WIDTH.W)
    val SSTATUS_SPP     = "h00000100".U(DATA_WIDTH.W)
    val SSTATUS_VS      = "h00000600".U(DATA_WIDTH.W)
    val SSTATUS_FS      = "h00006000".U(DATA_WIDTH.W)
    val SSTATUS_XS      = "h00018000".U(DATA_WIDTH.W)
    val SSTATUS_SUM     = "h00040000".U(DATA_WIDTH.W)
    val SSTATUS_MXR     = "h00080000".U(DATA_WIDTH.W)
    val SSTATUS32_SD    = "h80000000".U(DATA_WIDTH.W)
    val SSTATUS_UXL     = "h0000000300000000".U(DATA_WIDTH.W)
    val SSTATUS64_SD    = "h8000000000000000".U(DATA_WIDTH.W)

    val MSTATUS_UIE     = "h00000001".U(DATA_WIDTH.W)
    val MSTATUS_SIE     = "h00000002".U(DATA_WIDTH.W)
    val MSTATUS_HIE     = "h00000004".U(DATA_WIDTH.W)
    val MSTATUS_MIE     = "h00000008".U(DATA_WIDTH.W)
    val MSTATUS_UPIE    = "h00000010".U(DATA_WIDTH.W)
    val MSTATUS_SPIE    = "h00000020".U(DATA_WIDTH.W)
    val MSTATUS_UBE     = "h00000040".U(DATA_WIDTH.W)
    val MSTATUS_MPIE    = "h00000080".U(DATA_WIDTH.W)
    val MSTATUS_SPP     = "h00000100".U(DATA_WIDTH.W)
    val MSTATUS_VS      = "h00000600".U(DATA_WIDTH.W)
    val MSTATUS_MPP     = "h00001800".U(DATA_WIDTH.W)
    val MSTATUS_FS      = "h00006000".U(DATA_WIDTH.W)
    val MSTATUS_XS      = "h00018000".U(DATA_WIDTH.W)
    val MSTATUS_MPRV    = "h00020000".U(DATA_WIDTH.W)
    val MSTATUS_SUM     = "h00040000".U(DATA_WIDTH.W)
    val MSTATUS_MXR     = "h00080000".U(DATA_WIDTH.W)
    val MSTATUS_TVM     = "h00100000".U(DATA_WIDTH.W)
    val MSTATUS_TW      = "h00200000".U(DATA_WIDTH.W)
    val MSTATUS_TSR     = "h00400000".U(DATA_WIDTH.W)
    val MSTATUS32_SD    = "h80000000".U(DATA_WIDTH.W)
    val MSTATUS64_SD    = "h8000000000000000".U(DATA_WIDTH.W)
    val MSTATUS_UIE_BIT     = 0
    val MSTATUS_SIE_BIT     = 1
    val MSTATUS_HIE_BIT     = 2
    val MSTATUS_MIE_BIT     = 3
    val MSTATUS_UPIE_BIT    = 4
    val MSTATUS_SPIE_BIT    = 5
    val MSTATUS_UBE_BIT     = 6
    val MSTATUS_MPIE_BIT    = 7
    val MSTATUS_SPP_BIT     = 8
    val MSTATUS_MPRV_BIT    = 17
    val MSTATUS_SUM_BIT     = 18
    val MSTATUS_MXR_BIT     = 19

    val IRQ_U_SOFT   = 0
    val IRQ_S_SOFT   = 1
    val IRQ_VS_SOFT  = 2
    val IRQ_M_SOFT   = 3
    val IRQ_U_TIMER  = 4
    val IRQ_S_TIMER  = 5
    val IRQ_VS_TIMER = 6
    val IRQ_M_TIMER  = 7
    val IRQ_U_EXT    = 8
    val IRQ_S_EXT    = 9
    val IRQ_VS_EXT   = 10
    val IRQ_M_EXT    = 11
    val IRQ_S_GEXT   = 12
    val IRQ_COP      = 12
    val IRQ_HOST     = 13
    val INVALID_IRQ  = 63

    val MIP_USIP     = (1 << IRQ_U_SOFT).U(DATA_WIDTH.W)
    val MIP_SSIP     = (1 << IRQ_S_SOFT).U(DATA_WIDTH.W)
    val MIP_VSSIP    = (1 << IRQ_VS_SOFT).U(DATA_WIDTH.W)
    val MIP_MSIP     = (1 << IRQ_M_SOFT).U(DATA_WIDTH.W)
    val MIP_UTIP     = (1 << IRQ_U_TIMER).U(DATA_WIDTH.W)
    val MIP_STIP     = (1 << IRQ_S_TIMER).U(DATA_WIDTH.W)
    val MIP_VSTIP    = (1 << IRQ_VS_TIMER).U(DATA_WIDTH.W)
    val MIP_MTIP     = (1 << IRQ_M_TIMER).U(DATA_WIDTH.W)
    val MIP_UEIP     = (1 << IRQ_U_EXT).U(DATA_WIDTH.W)
    val MIP_SEIP     = (1 << IRQ_S_EXT).U(DATA_WIDTH.W)
    val MIP_VSEIP    = (1 << IRQ_VS_EXT).U(DATA_WIDTH.W)
    val MIP_MEIP     = (1 << IRQ_M_EXT).U(DATA_WIDTH.W)
    val MIP_SGEIP    = (1 << IRQ_S_GEXT).U(DATA_WIDTH.W)

    val ETYPE_NONE  = 0.U(2.W)
    val ETYPE_ECALL = 1.U(2.W)
    val ETYPE_SRET  = 2.U(2.W)
    val ETYPE_MRET  = 3.U(2.W)
}

trait csr_config extends priv_encoding{
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

    val RSSTATUS_MASK = SSTATUS_SIE | SSTATUS_SPIE | SSTATUS_SPP | SSTATUS_FS |
                        SSTATUS_XS | SSTATUS_SUM | SSTATUS_MXR | SSTATUS64_SD
    val WSSTATUS_MASK = SSTATUS_SIE | SSTATUS_SPIE | SSTATUS_SPP | SSTATUS_FS |
                        SSTATUS_XS | SSTATUS_SUM | SSTATUS_MXR
    val MSTATUS_MASK = MSTATUS_MIE | MSTATUS_MPIE | MSTATUS_MPRV | MSTATUS_SIE | MSTATUS_SPIE |
                        MSTATUS_TW | MSTATUS_TSR | MSTATUS_MXR | MSTATUS_SUM | MSTATUS_TVM |
                        MSTATUS_FS | MSTATUS_VS | MSTATUS_SPP | MSTATUS_MPP
    val PMPADDR_MASK = "h3fffffffffffff".U(DATA_WIDTH.W)
    val W_SATP_MASK = "hf0000fffffffffff".U(DATA_WIDTH.W)

    def set_partial_val(preVal: UInt, mask: UInt, newVal: UInt) = {
        (preVal & ~mask) | (newVal & mask)
    }

}

object regs_config extends csr_config{}

trait pte_encoding{
    val PTE_V       = 0x001.U(10.W) /* Valid */
    val PTE_R       = 0x002.U(10.W) /* Read */
    val PTE_W       = 0x004.U(10.W) /* Write */
    val PTE_X       = 0x008.U(10.W) /* Execute */
    val PTE_U       = 0x010.U(10.W) /* User */
    val PTE_G       = 0x020.U(10.W) /* Global */
    val PTE_A       = 0x040.U(10.W) /* Accessed */
    val PTE_D       = 0x080.U(10.W) /* Dirty */
    val PTE_SOFT    = 0x300.U(10.W) /* Reserved for Software */
    val PTE_V_BIT   = 0
    val PTE_R_BIT   = 1
    val PTE_W_BIT   = 2
    val PTE_X_BIT   = 3
    val PTE_U_BIT   = 4
    val PTE_G_BIT   = 5
    val PTE_A_BIT   = 6
    val PTE_D_BIT   = 7
}

object tlb_config extends satp_mode with mem_access_mode 
        with pte_encoding with csr_config{
    val TLB_ENTRY_WIDTH = 4
    val TLB_ENTRY_NUM   = 1 << TLB_ENTRY_WIDTH
    val TLB_TAG_WIDTH   = VADDR_WIDTH - PAGE_WIDTH
    val TLB_PA_WIDTH    = PADDR_WIDTH - PAGE_WIDTH
    val TLB_INFO_WIDTH  = 10

    def tlb_mask(level: UInt) = {
        MuxLookup(level, 0.U(TLB_TAG_WIDTH.W), Seq(
            0.U -> ~(0.U(TLB_TAG_WIDTH.W)),
            1.U -> ~(0x1ff.U(TLB_TAG_WIDTH.W)),
            2.U -> ~(0x3ffff.U(TLB_TAG_WIDTH.W))
        ))
    }
    def get_ad(m_type: UInt) = {
        Mux(m_type === MEM_STORE, PTE_A | PTE_D, PTE_A)
    }
    def m_type2cause(m_type: UInt) = {
        MuxLookup(m_type, 0.U(DATA_WIDTH.W), Seq(
            MEM_FETCH   -> CAUSE_FETCH_PAGE_FAULT.U,
            MEM_LOAD    -> CAUSE_LOAD_PAGE_FAULT.U,
            MEM_STORE   -> CAUSE_STORE_PAGE_FAULT.U
        ))
    }
}

object cache_config{ // U S L WIDTH
    val RAM_DATA_WIDTH  = 128
    val RAM_ADDR_WIDTH  = 6
    val RAM_DEPTH       = 1 << RAM_ADDR_WIDTH     // 64
    val RAM_WIDTH_BIT   = 4
    val RAM_WIDTH       = 1 << RAM_WIDTH_BIT      // 16B
    val RAM_MASK_WIDTH  = 128

    val CACHE_WAY_NUM   = 4
    val IC_BLOCK_WIDTH  = 6
    val IC_INDEX_WIDTH  = 4
    val IC_BLOCK_SIZE   = 1 << IC_BLOCK_WIDTH
    val IC_BLOCK_NUM    = 1 << IC_INDEX_WIDTH
    val IC_TAG_WIDTH    = PADDR_WIDTH - IC_BLOCK_WIDTH - IC_INDEX_WIDTH
    val IC_BLOCK_MASK   = "hffffffc0".U(PADDR_WIDTH.W)
    val DC_BLOCK_WIDTH  = 6
    val DC_INDEX_WIDTH  = 4
    val DC_BLOCK_SIZE   = 1 << DC_BLOCK_WIDTH
    val DC_BLOCK_NUM    = 1 << DC_INDEX_WIDTH
    val DC_TAG_WIDTH    = PADDR_WIDTH - DC_BLOCK_WIDTH - DC_INDEX_WIDTH
    val DC_BLOCK_MASK   = "hffffffc0".U(PADDR_WIDTH.W)

    val DC_MODE_WIDTH = 5
    val mode_NOP = "b00000".U(DC_MODE_WIDTH.W)
    val mode_LB  = "b00100".U(DC_MODE_WIDTH.W)
    val mode_LH  = "b00101".U(DC_MODE_WIDTH.W)
    val mode_LW  = "b00110".U(DC_MODE_WIDTH.W)
    val mode_LD  = "b00111".U(DC_MODE_WIDTH.W)
    val mode_LBU = "b10100".U(DC_MODE_WIDTH.W)
    val mode_LHU = "b10101".U(DC_MODE_WIDTH.W)
    val mode_LWU = "b10110".U(DC_MODE_WIDTH.W)
    val mode_SB  = "b01000".U(DC_MODE_WIDTH.W)
    val mode_SH  = "b01001".U(DC_MODE_WIDTH.W)
    val mode_SW  = "b01010".U(DC_MODE_WIDTH.W)
    val mode_SD  = "b01011".U(DC_MODE_WIDTH.W)
    val mode_LSW = "b01110".U(DC_MODE_WIDTH.W)
    val mode_LSD = "b01111".U(DC_MODE_WIDTH.W)
    val DC_U_BIT = 4
    val DC_S_BIT = 3
    val DC_L_BIT = 2
    def rdata_by_mode(mode: UInt, rdata64: UInt) = {
        MuxLookup(mode, 0.U, Seq(  // can take advantage of the encoding of dc_mode
            mode_LB  -> Cat(Fill(DATA_WIDTH - 8, rdata64(7)), rdata64(7, 0)),
            mode_LBU -> rdata64(7, 0).asUInt,
            mode_LH  -> Cat(Fill(DATA_WIDTH - 16, rdata64(15)), rdata64(15, 0)),
            mode_LHU -> rdata64(15, 0).asUInt,
            mode_LW  -> Cat(Fill(DATA_WIDTH - 32, rdata64(31)), rdata64(31, 0)),
            mode_LWU -> rdata64(31, 0).asUInt,
            mode_LD  -> rdata64,
            mode_LSW -> Cat(Fill(DATA_WIDTH - 32, rdata64(31)), rdata64(31, 0)),
            mode_LSD -> rdata64
        ))
    }
     def zeroTruncateData(width: UInt, data: UInt) = {
        val ans = MuxLookup(width, 0.U(DATA_WIDTH.W), Seq(
            3.U -> data(63,0),
            2.U -> Cat(0.U(32.W), data(31,0)),
            1.U -> Cat(0.U(48.W), data(15,0)),
            0.U -> Cat(0.U(56.W), data(7,0))
        ))
        ans
    }
    def signTruncateData(width: UInt, data: UInt) = {
        val ans = MuxLookup(width, 0.U(DATA_WIDTH.W), Seq(
            3.U -> data(63,0),
            2.U -> Cat(Fill(32, data(31)), data(31,0)),
            1.U -> Cat(Fill(48, data(15)), data(15,0)),
            0.U -> Cat(Fill(56, data(7)), data(7,0))
        ))
        ans
    }
}

object Insts{
    //mv U-type           | 28| 24| 20| 16| 12|  8|  4|  0|
    def LUI     = BitPat("b?????????????????????????0110111")
    def AUIPC   = BitPat("b?????????????????????????0010111")
    // jmp J-type
    def JAL     = BitPat("b?????????????????????????1101111")
    def JALR    = BitPat("b?????????????????000?????1100111")
    // Branch B-type
    def BEQ     = BitPat("b?????????????????000?????1100011")
    def BNE     = BitPat("b?????????????????001?????1100011")
    def BLT     = BitPat("b?????????????????100?????1100011")
    def BGE     = BitPat("b?????????????????101?????1100011")
    def BLTU    = BitPat("b?????????????????110?????1100011")
    def BGEU    = BitPat("b?????????????????111?????1100011") 
    //load I-type
    def LB      = BitPat("b?????????????????000?????0000011")
    def LH      = BitPat("b?????????????????001?????0000011")
    def LW      = BitPat("b?????????????????010?????0000011")
    def LD      = BitPat("b?????????????????011?????0000011")
    def LBU     = BitPat("b?????????????????100?????0000011")
    def LHU     = BitPat("b?????????????????101?????0000011")
    def LWU     = BitPat("b?????????????????110?????0000011")
    //store S-type
    def SB      = BitPat("b?????????????????000?????0100011")
    def SH      = BitPat("b?????????????????001?????0100011")
    def SW      = BitPat("b?????????????????010?????0100011")
    def SD      = BitPat("b?????????????????011?????0100011")
    // I-type
    def ADDI    = BitPat("b?????????????????000?????0010011")
    def SLTI    = BitPat("b?????????????????010?????0010011")
    def SLTIU   = BitPat("b?????????????????011?????0010011")
    def XORI    = BitPat("b?????????????????100?????0010011")
    def ORI     = BitPat("b?????????????????110?????0010011")
    def ANDI    = BitPat("b?????????????????111?????0010011")
    def SLLI    = BitPat("b000000???????????001?????0010011")
    def SRLI    = BitPat("b000000???????????101?????0010011")
    def SRAI    = BitPat("b010000???????????101?????0010011")
    //I-type
    def ADDIW   = BitPat("b?????????????????000?????0011011")
    def SLLIW   = BitPat("b0000000??????????001?????0011011")
    def SRLIW   = BitPat("b0000000??????????101?????0011011")
    def SRAIW   = BitPat("b0100000??????????101?????0011011")
    //R-type
    def ADDW    = BitPat("b0000000??????????000?????0111011")
    def SUBW    = BitPat("b0100000??????????000?????0111011")
    def SLLW    = BitPat("b0000000??????????001?????0111011")
    def SRLW    = BitPat("b0000000??????????101?????0111011")
    def SRAW    = BitPat("b0100000??????????101?????0111011")
    //RV64 Extension R-type
    def MULW    = BitPat("b0000001??????????000?????0111011")
    def DIVW    = BitPat("b0000001??????????100?????0111011")
    def DIVUW   = BitPat("b0000001??????????101?????0111011")
    def REMW    = BitPat("b0000001??????????110?????0111011")
    def REMUW   = BitPat("b0000001??????????111?????0111011")
    // R-type
    def ADD     = BitPat("b0000000??????????000?????0110011")
    def SUB     = BitPat("b0100000??????????000?????0110011")
    def SLL     = BitPat("b0000000??????????001?????0110011")
    def SLT     = BitPat("b0000000??????????010?????0110011")
    def SLTU    = BitPat("b0000000??????????011?????0110011")
    def XOR     = BitPat("b0000000??????????100?????0110011")
    def SRL     = BitPat("b0000000??????????101?????0110011")
    def SRA     = BitPat("b0100000??????????101?????0110011")
    def OR      = BitPat("b0000000??????????110?????0110011")
    def AND     = BitPat("b0000000??????????111?????0110011")

    def MUL     = BitPat("b0000001??????????000?????0110011")
    def MULH    = BitPat("b0000001??????????001?????0110011")
    def MULHSU  = BitPat("b0000001??????????010?????0110011")
    def MULHU   = BitPat("b0000001??????????011?????0110011")
    def DIV     = BitPat("b0000001??????????100?????0110011")
    def DIVU    = BitPat("b0000001??????????101?????0110011")
    def REM     = BitPat("b0000001??????????110?????0110011")
    def REMU    = BitPat("b0000001??????????111?????0110011")

    def ECALL   = BitPat("b00000000000000000000000001110011")
    def SRET    = BitPat("b00010000001000000000000001110011")
    def MRET    = BitPat("b00110000001000000000000001110011")

    // CSR I-type
    def CSRRW   = BitPat("b?????????????????001?????1110011")
    def CSRRS   = BitPat("b?????????????????010?????1110011")
    def CSRRC   = BitPat("b?????????????????011?????1110011")
    def CSRRWI  = BitPat("b?????????????????101?????1110011")
    def CSRRSI  = BitPat("b?????????????????110?????1110011")
    def CSRRCI  = BitPat("b?????????????????111?????1110011")

    //nemu_trap
    def TRAP    = BitPat("b00000000000000000000000001101011")
    def FENCE_I = BitPat("b00000000000000000001000000001111")
    def SFENCE_VMA  = BitPat("b0001001??????????000000001110011")

    //atomic
    def LR_W        = BitPat("b00010??00000?????010?????0101111")
    def SC_W        = BitPat("b00011????????????010?????0101111")
    def AMOSWAP_W   = BitPat("b00001????????????010?????0101111")
    def AMOADD_W    = BitPat("b00000????????????010?????0101111")
    def AMOXOR_W    = BitPat("b00100????????????010?????0101111")
    def AMOAND_W    = BitPat("b01100????????????010?????0101111")
    def AMOOR_W     = BitPat("b01000????????????010?????0101111")
    def AMOMIN_W    = BitPat("b10000????????????010?????0101111")
    def AMOMAX_W    = BitPat("b10100????????????010?????0101111")
    def AMOMINU_W   = BitPat("b11000????????????010?????0101111")
    def AMOMAXU_W   = BitPat("b11100????????????010?????0101111")

    def LR_D        = BitPat("b00010??00000?????011?????0101111")
    def SC_D        = BitPat("b00011????????????011?????0101111")
    def AMOSWAP_D   = BitPat("b00001????????????011?????0101111")
    def AMOADD_D    = BitPat("b00000????????????011?????0101111")
    def AMOXOR_D    = BitPat("b00100????????????011?????0101111")
    def AMOAND_D    = BitPat("b01100????????????011?????0101111")
    def AMOOR_D     = BitPat("b01000????????????011?????0101111")
    def AMOMIN_D    = BitPat("b10000????????????011?????0101111")
    def AMOMAX_D    = BitPat("b10100????????????011?????0101111")
    def AMOMINU_D   = BitPat("b11000????????????011?????0101111")
    def AMOMAXU_D   = BitPat("b11100????????????011?????0101111")
}

trait DeType{
    val EMPTY = 0.U(3.W)
    val RType = 1.U(3.W)
    val IType = 2.U(3.W)
    val SType = 3.U(3.W)
    val BType = 4.U(3.W)
    val UType = 5.U(3.W)
    val JType = 6.U(3.W)
    val TRAP  = 7.U(3.W)
}

trait ALUOP{
    val (alu_NOP    :: alu_MV1  :: alu_MV2  :: alu_ADD  :: alu_XOR   ::
         alu_OR     :: alu_AND  :: alu_SLL  :: alu_SRL  :: alu_SRA   ::
         alu_SUB    :: alu_SLT  :: alu_SLTU :: alu_MUL  :: alu_MULH  ::
         alu_MULHU  :: alu_MULHSU :: alu_DIV  :: alu_DIVU :: alu_REM ::
         alu_REMU   :: alu_NAND   :: Nil) = Enum(22)
    val ALUOP_WIDTH = 5
}

trait BrType{
    val bEQ  = 0.U(3.W)
    val bNE  = 1.U(3.W)
    val bLT  = 4.U(3.W)
    val bGE  = 5.U(3.W)
    val bLTU = 6.U(3.W)
    val bGEU = 7.U(3.W)
}

object decode_config extends DeType with ALUOP with BrType 
        with csr_config with dataForw{
    val IS_ALU64 = 0.U
    val IS_ALU32 = 1.U
                            // decode aluop    alu-w    ram-mode|write-reg|跳转信号|csr-read|csr-write|rs1-imm
    val decodeDefault = List(EMPTY, alu_NOP,   IS_ALU64,  mode_NOP, false.B, false.B, false.B, false.B, false.B)
    val decodeTable = Array(   
        Insts.LUI    -> List(UType, alu_MV1,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.AUIPC  -> List(UType, alu_ADD,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.JAL    -> List(JType, alu_MV2,   IS_ALU64,  mode_NOP, true.B,  true.B,  false.B, false.B, false.B),
        Insts.JALR   -> List(IType, alu_MV2,   IS_ALU64,  mode_NOP, true.B,  true.B,  false.B, false.B, false.B),
        Insts.BEQ    -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),
        Insts.BNE    -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),
        Insts.BLT    -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),
        Insts.BGE    -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),
        Insts.BLTU   -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),
        Insts.BGEU   -> List(BType, alu_NOP,   IS_ALU64,  mode_NOP, false.B, true.B,  false.B, false.B, false.B),

        Insts.LB     -> List(IType, alu_ADD,   IS_ALU64,  mode_LB,  true.B,  false.B, false.B, false.B, false.B),
        Insts.LH     -> List(IType, alu_ADD,   IS_ALU64,  mode_LH,  true.B,  false.B, false.B, false.B, false.B),
        Insts.LW     -> List(IType, alu_ADD,   IS_ALU64,  mode_LW,  true.B,  false.B, false.B, false.B, false.B),
        Insts.LD     -> List(IType, alu_ADD,   IS_ALU64,  mode_LD,  true.B,  false.B, false.B, false.B, false.B),
        Insts.LBU    -> List(IType, alu_ADD,   IS_ALU64,  mode_LBU, true.B,  false.B, false.B, false.B, false.B),
        Insts.LHU    -> List(IType, alu_ADD,   IS_ALU64,  mode_LHU, true.B,  false.B, false.B, false.B, false.B),
        Insts.LWU    -> List(IType, alu_ADD,   IS_ALU64,  mode_LWU, true.B,  false.B, false.B, false.B, false.B),
        Insts.SB     -> List(SType, alu_ADD,   IS_ALU64,  mode_SB,  false.B, false.B, false.B, false.B, false.B),        
        Insts.SH     -> List(SType, alu_ADD,   IS_ALU64,  mode_SH,  false.B, false.B, false.B, false.B, false.B),
        Insts.SW     -> List(SType, alu_ADD,   IS_ALU64,  mode_SW,  false.B, false.B, false.B, false.B, false.B),
        Insts.SD     -> List(SType, alu_ADD,   IS_ALU64,  mode_SD,  false.B, false.B, false.B, false.B, false.B),
        Insts.ADDI   -> List(IType, alu_ADD,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLTI   -> List(IType, alu_SLT,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLTIU  -> List(IType, alu_SLTU,  IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.XORI   -> List(IType, alu_XOR,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.ORI    -> List(IType, alu_OR,    IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.ANDI   -> List(IType, alu_AND,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLLI   -> List(IType, alu_SLL,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRLI   -> List(IType, alu_SRL,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRAI   -> List(IType, alu_SRA,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),

        Insts.ADD    -> List(RType, alu_ADD,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SUB    -> List(RType, alu_SUB,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLL    -> List(RType, alu_SLL,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLT    -> List(RType, alu_SLT,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLTU   -> List(RType, alu_SLTU,  IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.XOR    -> List(RType, alu_XOR,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRL    -> List(RType, alu_SRL,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRA    -> List(RType, alu_SRA,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.OR     -> List(RType, alu_OR,    IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.AND    -> List(RType, alu_AND,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),

        Insts.MUL    -> List(RType, alu_MUL,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.MULH   -> List(RType, alu_MULH,  IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.MULHU  -> List(RType, alu_MULHU, IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.MULHSU -> List(RType, alu_MULHSU,IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.DIV    -> List(RType, alu_DIV,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.DIVU   -> List(RType, alu_DIVU,  IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.REM    -> List(RType, alu_REM,   IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.REMU   -> List(RType, alu_REMU,  IS_ALU64,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),

        Insts.MULW   -> List(RType, alu_MUL,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.DIVW   -> List(RType, alu_DIV,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.DIVUW  -> List(RType, alu_DIVU,  IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.REMW   -> List(RType, alu_REM,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.REMUW  -> List(RType, alu_REMU,  IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),

        Insts.ADDIW  -> List(IType, alu_ADD,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLLIW  -> List(IType, alu_SLL,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRLIW  -> List(IType, alu_SRL,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRAIW  -> List(IType, alu_SRA,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.ADDW   -> List(RType, alu_ADD,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SUBW   -> List(RType, alu_SUB,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SLLW   -> List(RType, alu_SLL,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRLW   -> List(RType, alu_SRL,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
        Insts.SRAW   -> List(RType, alu_SRA,   IS_ALU32,  mode_NOP, true.B,  false.B, false.B, false.B, false.B),
                                                                //|write-reg|跳转信号|csr-read|csr-write|rs1-imm
        Insts.CSRRW  -> List(IType, alu_MV1,   IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  false.B),
        Insts.CSRRS  -> List(IType, alu_OR,    IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  false.B),
        Insts.CSRRC  -> List(IType, alu_NAND,  IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  false.B),
        Insts.CSRRWI -> List(IType, alu_MV1,   IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  true.B),
        Insts.CSRRSI -> List(IType, alu_OR,    IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  true.B),
        Insts.CSRRCI -> List(IType, alu_NAND,  IS_ALU64,  mode_NOP, true.B,  false.B, true.B,  true.B,  true.B),

        Insts.LR_W      -> List(RType, alu_MV1, IS_ALU64, mode_LW,  true.B,  false.B, false.B, false.B, false.B),
        Insts.SC_W      -> List(RType, alu_MV1, IS_ALU64, mode_SW,  true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOSWAP_W -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOADD_W  -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOXOR_W  -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOAND_W  -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOOR_W   -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMIN_W  -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMAX_W  -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMINU_W -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMAXU_W -> List(RType, alu_MV1, IS_ALU64, mode_LSW, true.B,  false.B, false.B, false.B, false.B),

        Insts.LR_D      -> List(RType, alu_MV1, IS_ALU64, mode_LD,  true.B,  false.B, false.B, false.B, false.B),
        Insts.SC_D      -> List(RType, alu_MV1, IS_ALU64, mode_SD,  true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOSWAP_D -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOADD_D  -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOOR_D   -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOXOR_D  -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOAND_D  -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMIN_D  -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMAX_D  -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMINU_D -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),
        Insts.AMOMAXU_D -> List(RType, alu_MV1, IS_ALU64, mode_LSD, true.B,  false.B, false.B, false.B, false.B),

        Insts.TRAP   -> List(TRAP,  alu_NOP,   IS_ALU64,  mode_NOP, false.B, false.B, false.B, false.B, false.B)
    )
    val NO_JMP     = "b00".U(2.W)
    val JMP_UNCOND = "b01".U(2.W)
    val JMP_COND   = "b10".U(2.W)
    val JMP_CSR    = "b11".U(2.W)

    val AMO_WIDTH = 5
    val amoSwap = "b00001".U(AMO_WIDTH.W)
    val amoAdd  = "b00000".U(AMO_WIDTH.W)
    val amoXor  = "b00100".U(AMO_WIDTH.W)
    val amoAnd  = "b01100".U(AMO_WIDTH.W)
    val amoOr   = "b01000".U(AMO_WIDTH.W)
    val amoMin  = "b10000".U(AMO_WIDTH.W)
    val amoMax  = "b10100".U(AMO_WIDTH.W)
    val amoMinU = "b11100".U(AMO_WIDTH.W)
    val amoMaxU = "b11100".U(AMO_WIDTH.W)

    val SPECIAL_FENCE_I = 1.U(2.W)
    val SPECIAL_SFENCE_VMA = 2.U(2.W)

    val SWAP_WIDTH  = 6
    val NO_SWAP     = "b011011".U(6.W)
    val SWAP_2_d    = "b011110".U(6.W)
    val COPY_2_d    = "b011010".U(6.W)
}

object noop_tools{
    def sext32to64(value: UInt) = {
         Cat(Fill(32, value(31)), value(31,0))
    }
    def zext32to64(value: UInt) = {
        Cat(0.U(32.W), value(31,0))
    }
}
