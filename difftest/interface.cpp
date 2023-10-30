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

extern "C" void update_pc(int pc, int inst){
    state.pc = (uint32_t)pc;
    state.inst = (uint32_t)inst;
}

extern "C" void update_csr(int id, long long val){
    state.csr[id] = val;
}

extern "C" void update_priv(int priv){
    state.priv = priv;
}

extern "C" void update_excep(svBit intr, long long cause, int pc){
    state.intr = intr;
    state.cause = cause;
    state.excep_pc = (uint32_t)pc;
}