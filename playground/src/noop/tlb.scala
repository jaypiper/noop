package noop.tlb

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.tlb_config._
import noop.param.cache_config._
import noop.datapath._

class TlbHItMsg extends Bundle{
    val tlbHit  = Bool()
    val tlbPa   = UInt(TLB_PA_WIDTH.W)
    val tlbMask = UInt(TLB_TAG_WIDTH.W)
    val tlbInfo = UInt(TLB_INFO_WIDTH.W)
    val tlbPteAddr = UInt(PADDR_WIDTH.W)
    val tlbIdx  = UInt(4.W)
}


class VA2PA extends Bundle{
    val vaddr   = Input(UInt(VADDR_WIDTH.W))
    val vvalid  = Input(Bool())
    val m_type  = Input(UInt(2.W))
    val ready   = Output(Bool())
    val paddr   = Output(UInt(PADDR_WIDTH.W))
    val pvalid  = Output(Bool())
    val tlb_excep = Output(new Exception_BASIC)
}

class TLB extends Module{
    val io = IO(new Bundle{
        val va2pa       = new VA2PA
        val mmuState    = Input(new MmuState)
        val flush       = Input(Bool())
        val dcacheRW    = Flipped(new DcacheRW)
    })
    val tag     = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U(TLB_TAG_WIDTH.W))))
    val paddr   = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U(TLB_PA_WIDTH.W))))
    val info    = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U(TLB_INFO_WIDTH.W))))
    val pte_addr    = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U(PADDR_WIDTH.W)))) // can be get by re-translating using tag
    val pte_level   = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U(2.W))))
    val valid   = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(false.B)))

    val pre_addr    = RegInit(0.U(VADDR_WIDTH.W))
    val pte_addr_r  = RegInit(0.U(PADDR_WIDTH.W))
    val wpte_data_r = RegInit(0.U(DATA_WIDTH.W))
    val dc_mode_r   = RegInit(0.U(DC_MODE_WIDTH.W))
    val inp_valid_r = RegInit(false.B)
    val out_valid_r = RegInit(false.B)
    val out_paddr_r = RegInit(0.U(PADDR_WIDTH.W))
    val out_excep_r = RegInit(0.U.asTypeOf(new Exception_BASIC))
    when(io.va2pa.ready && io.va2pa.vvalid){
        pre_addr := io.va2pa.vaddr
    }

    val inp_tag = io.va2pa.vaddr(VADDR_WIDTH-1, PAGE_WIDTH)
    val inp_offset = io.va2pa.vaddr(PAGE_WIDTH-1, 0)
    val mmuMode = Mux((io.mmuState.priv === PRV_M) && !io.mmuState.mstatus(MSTATUS_MPRV_BIT), Bare, io.mmuState.satp(63,60))
    val is_Sv39 = mmuMode === Sv39
    val tlbMsg = Wire(new TlbHItMsg)
    tlbMsg.tlbHit := 0.U; tlbMsg.tlbPa := 0.U; tlbMsg.tlbMask := 0.U; tlbMsg.tlbInfo := 0.U; tlbMsg.tlbPteAddr  := 0.U; tlbMsg.tlbIdx := 0.U
    
    for(i <- 0 until TLB_ENTRY_NUM){
        val tlb_tag_mask = tlb_mask(pte_level(i))
        when(((inp_tag & tlb_tag_mask) === tag(i)) && valid(i)){
            tlbMsg.tlbHit  := true.B
            tlbMsg.tlbPa   := paddr(i)
            tlbMsg.tlbMask := tlb_tag_mask
            tlbMsg.tlbInfo := info(i)
            tlbMsg.tlbPteAddr := pte_addr(i)
            tlbMsg.tlbIdx  := i.U
        }
    }
    val sIdle :: sPte :: sExcep :: sWritePte :: Nil = Enum(4)
    val state = RegInit(sIdle)
    val flush_r = RegInit(false.B)
    when(io.flush || flush_r){
        when(state === sIdle){
            valid := VecInit(Seq.fill(TLB_ENTRY_NUM)(false.B))
            flush_r := false.B
        }.otherwise{
            flush_r := true.B
        }
    }
    val handshake = io.va2pa.vvalid && io.va2pa.ready
    val m_type_r = RegInit(0.U(2.W))
    val cur_m_type = Mux(handshake, io.va2pa.m_type, m_type_r)
    val ad = get_ad(cur_m_type)
    io.va2pa.ready  := io.va2pa.vvalid && (state === sIdle) && !io.flush && !flush_r
    io.va2pa.pvalid := out_valid_r
    io.va2pa.paddr  := out_paddr_r
    when(handshake){
        inp_valid_r := true.B
        pre_addr := io.va2pa.vaddr
    }.elsewhen(io.va2pa.pvalid){
        inp_valid_r := false.B
    }

    when(io.va2pa.pvalid || io.va2pa.tlb_excep.en){
        out_valid_r := false.B
        out_excep_r.en := false.B
    }
    // tlb <> out
    io.va2pa.paddr := out_paddr_r
    io.va2pa.pvalid := out_valid_r
    io.va2pa.tlb_excep := out_excep_r
    // tlb <> dcache
    io.dcacheRW.addr := pte_addr_r
    io.dcacheRW.wdata := wpte_data_r
    io.dcacheRW.dc_mode := dc_mode_r
    io.dcacheRW.amo     := 0.U
    val pte     = io.dcacheRW.rdata
    val dc_hand = io.dcacheRW.ready && (io.dcacheRW.dc_mode =/= mode_NOP)
    val mstatus = io.mmuState.mstatus

    val select = LFSR(4)
    val select_r = RegInit(0.U(4.W))
    val offset  = RegInit(0.U(8.W))
    val level   = RegInit(0.U(2.W))
    val ppn     = RegInit(0.U(44.W))
    val wpte_hs_r = RegInit(false.B)
    when(is_Sv39 || state =/= sIdle){
        switch(state){
            is(sIdle){
                dc_mode_r := mode_NOP
                when(handshake && tlbMsg.tlbHit){
                    out_valid_r := true.B
                    out_paddr_r := Cat(tlbMsg.tlbPa, inp_offset)
                    when((ad & tlbMsg.tlbInfo) =/= ad && is_Sv39){
                        state := sWritePte
                        wpte_hs_r := false.B
                        pte_addr_r  := tlbMsg.tlbPteAddr
                        wpte_data_r := Cat(0.U(34.W), tlbMsg.tlbPa, tlbMsg.tlbInfo | ad)
                        info(tlbMsg.tlbIdx) := tlbMsg.tlbInfo | ad
                    }
                }
                when(handshake && !tlbMsg.tlbHit){
                    state := sPte
                    select_r   := select
                    m_type_r   := io.va2pa.m_type
                    out_excep_r.cause := m_type2cause(io.va2pa.m_type)
                    out_excep_r.tval  := io.va2pa.vaddr
                    when(io.va2pa.vaddr(63,39) =/= Fill(25, io.va2pa.vaddr(38))){
                        // state := sExcep
                        out_excep_r.en := true.B
                    }.otherwise{
                        pte_addr_r := Cat(io.mmuState.satp(43,0), (io.va2pa.vaddr >> 30.U)(8,0), 0.U(3.W))
                        dc_mode_r  := mode_LD
                        offset  := 30.U
                        level   := 3.U
                        state := sPte
                    }
                }
            }
            is(sWritePte){
                dc_mode_r := Mux(wpte_hs_r, mode_NOP, mode_SD)
                when(io.dcacheRW.ready){
                    dc_mode_r := mode_NOP
                    wpte_hs_r := true.B
                }
                when(io.dcacheRW.rvalid){
                    state := sIdle
                }
            }
            is(sPte){
                when(dc_hand){
                    dc_mode_r := mode_NOP
                    offset  := offset - 9.U
                    level   := level - 1.U
                }
                when(io.dcacheRW.rvalid){
                    when((pte & (PTE_V | PTE_R | PTE_W | PTE_X)) === PTE_V){ // page-table
                        when((pte & (PTE_D | PTE_A | PTE_U)) =/= 0.U){
                            state := sIdle
                            out_excep_r.en := true.B
                        }.otherwise{
                            pte_addr_r := Cat(pte(53,10), (pre_addr >> offset)(8,0), 0.U(3.W))
                            dc_mode_r  := mode_LD
                        }
                    }.elsewhen(Mux(pte(PTE_U_BIT), (io.mmuState.priv === PRV_S) && (!mstatus(MSTATUS_SUM_BIT) || (out_excep_r.cause === CAUSE_FETCH_PAGE_FAULT.U)), io.mmuState.priv === PRV_U)){
                        // U mode can only access (U=1); U=1 sum=1 S-mode can access(but can not execute)
                        state := sIdle
                        out_excep_r.en := true.B
                    }.elsewhen(!pte(PTE_V_BIT) || (!pte(PTE_R_BIT) && pte(PTE_W_BIT))){
                        // pte not valid or (writable but not readable)
                        state := sIdle
                        out_excep_r.en := true.B
                    }.elsewhen(((out_excep_r.cause === CAUSE_FETCH_PAGE_FAULT.U) && !pte(PTE_X_BIT)) || 
                                ((out_excep_r.cause === CAUSE_LOAD_PAGE_FAULT.U) && !(pte(PTE_R_BIT) || (mstatus(MSTATUS_MXR_BIT) && pte(PTE_X_BIT)))) ||
                            ((out_excep_r.cause === CAUSE_STORE_PAGE_FAULT.U) && !pte(PTE_W_BIT))){
                        // check whether the leaf pte is r w or x
                        state := sIdle
                        out_excep_r.en := true.B
                    }.elsewhen(((level === 1.U) && (pte(18,10) =/= 0.U)) || (level === 2.U && (pte(27,10) =/= 0.U))){
                        // super-page
                        state := sIdle
                        out_excep_r.en := true.B
                    }.otherwise{
                        state := sIdle
                        val ppn_mask = tlb_mask(level)
                        tag(select_r) := pre_addr(VADDR_WIDTH-1, PAGE_WIDTH) & ppn_mask
                        valid(select_r) := true.B
                        val update_pa = pte(29, 10) & ppn_mask
                        paddr(select_r) := update_pa
                        pte_addr(select_r) := pte_addr_r
                        pte_level(select_r) := level
                        info(select_r) := pte(9,0)
                    }
                }
            }
        }
    }.otherwise{ // Sv48 is not supported
        out_valid_r := io.va2pa.vvalid
        out_paddr_r := io.va2pa.vaddr
    }



}