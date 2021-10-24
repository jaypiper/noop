#include "common.h"
#include "svdpi.h"
#include "Vnewtop__Dpi.h"

extern "C" void update_reg(int id, long long val){
    state.gpr[id] = val;
}

extern "C" void update_indi(svBit cpu_is_mmio, svBit cpu_valid, int rcsr_id){
    state.is_mmio = cpu_is_mmio;
    state.valid   = cpu_valid;
    state.rcsr_id = rcsr_id;
}

extern "C" void update_pc(long long pc, int inst){
    state.pc = (uint64_t)pc;
    state.inst = (uint32_t)inst;
}

int csr_lookup[] = {
    SEPC_ID, STVEC_ID, SCAUSE_ID, STVAL_ID, SSCRATCH_ID, SSTATUS_ID,
    SATP_ID, SIE_ID, SIP_ID, MTVEC_ID, MEPC_ID, MCAUSE_ID, MIE_ID, MIP_ID, MTVAL_ID,
    MSCRATCH_ID, MSTATUS_ID, MHARTID, MEDELEG_ID, MIDELEG_ID
};

extern "C" void update_csr(int id, long long val){
    if(id >= sizeof(csr_lookup) / sizeof(int)) return;
    int csr_id = csr_lookup[id];
    state.csr[csr_id] = val;
}

extern "C" void update_priv(int priv){
    state.priv = priv;
}

extern "C" void update_excep(svBit intr, long long cause, long long pc){
    state.intr = intr;
    state.cause = cause;
    state.excep_pc = pc;
}