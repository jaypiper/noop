#ifndef __COMMON_H__
#define __COMMON_H__
#include <stdint.h>
#include <signal.h>
#include <ctime>
#include "../obj_dir/Vnewtop.h"
typedef uint64_t word_t;
typedef int64_t sword_t;

typedef word_t rtlreg_t;
typedef word_t vaddr_t;
typedef uint32_t paddr_t;
// #define DIFFTEST

typedef struct CPU_STATE{
    rtlreg_t gpr[32];
    rtlreg_t pc;
    rtlreg_t csr[0x1000];
    rtlreg_t priv;
    uint32_t inst;
    int is_mmio;
    int valid;
    int intr;
    rtlreg_t cause;
    rtlreg_t excep_pc;
    int rcsr_id;
}CPU_state;

#ifdef SIM
    #define newtop__DOT__socfull__DOT__mem__DOT__srams__DOT__mem__DOT__mem_ext__DOT__ram newtop__DOT__mem__DOT__ram
    #define newtop__DOT__socfull__DOT__asic__DOT__cpu__DOT__cpu__DOT__csrs__DOT__uscratch newtop__DOT__cpu__DOT__csrs__DOT__uscratch
#endif

# define SCREEN_W 400
# define SCREEN_H 300

extern Vnewtop* cpu;
extern CPU_state state;
extern int is_mmio;
extern int valid;


#define SEPC_LAB      0x0
#define STVEC_LAB     0x1
#define SCAUSE_LAB    0x2
#define STVAL_LAB     0x3
#define SSCRATCH_LAB  0x4
#define SSTATUS_LAB   0x5
#define SATP_LAB      0x6
#define MTVEC_LAB     0x7
#define MEPC_LAB      0x8
#define MCAUSE_LAB    0x9
#define MIE_LAB       0xa
#define MIP_LAB       0xb
#define MTVAL_LAB     0xc
#define MSCRATCH_LAB  0xd
#define MSTATUS_LAB   0xe
#define MHARTID_LAB   0xf

#define SEPC_ID       0x141
#define STVEC_ID      0x105
#define SCAUSE_ID     0x142
#define STVAL_ID      0x143
#define SSCRATCH_ID   0x140
#define SSTATUS_ID    0x100
#define SATP_ID       0x180
#define SIE_ID        0x104
#define SIP_ID        0x144
#define MTVEC_ID      0x305
#define MEPC_ID       0x341
#define MCAUSE_ID     0x342
#define MIE_ID        0x304
#define MIP_ID        0x344
#define MTVAL_ID      0x343
#define MSCRATCH_ID   0x340
#define MSTATUS_ID    0x300
#define MEDELEG_ID    0x302
#define MIDELEG_ID    0x303
#define MHARTID       0xf14
#define USCRATCH      0x40
#define MISA          0x301

#define MIP_MASK      0x2

#endif