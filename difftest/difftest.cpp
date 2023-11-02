#include "difftest.h"
#include "verilated.h"
#include "verilated_fst_c.h"
#include "vga.h"

VerilatedContext *const contextp = new VerilatedContext;
VerilatedFstC *tfp = new VerilatedFstC;

void (*ref_difftest_memcpy_from_dut)(paddr_t dest, void *src, uint64_t n) = NULL;
void (*ref_difftest_getregs)(void *c) = NULL;
void (*ref_difftest_setregs)(const void *c) = NULL;
void (*ref_difftest_setregs_all)(const void *c) = NULL;
void (*ref_difftest_exec)(uint64_t n) = NULL;
void (*ref_check_end)(void* indi) = NULL;
void (*ref_intr_exec)(uint64_t pc, uint64_t cause) = NULL;

#define MAX_PROGRAM_SIZE 0x8000000

#ifdef FLASH
    #define PC_START 0x30000000
#else
    #define PC_START 0x80000000
#endif
uint8_t program[MAX_PROGRAM_SIZE];
int program_sz;

CPU_state state;

Vnewtop* cpu;

int check = 0;
jmp_buf env;
CPU_state state_buf[4];
int inst_p = 0;

void load_program(char* filename){

    memset(&program, 0, sizeof(program));
    if(!filename){
        printf("Use the default build-in program\n");
        ((uint32_t*)program)[0] = 0x800002b7;  // lui t0,0x80000
        ((uint32_t*)program)[1] = 0x0002b023;  // sd  zero,0(t0)
        ((uint32_t*)program)[2] = 0x0002b503;  // ld  a0,0(t0)
        ((uint32_t*)program)[3] = 0x0000006b;  // nemu_trap
        program_sz = 32;
        return;
    }

    FILE* fp = fopen(filename, "rb");
    assert(fp);

    fseek(fp, 0, SEEK_END);
    program_sz = ftell(fp);
    assert(program_sz < MAX_PROGRAM_SIZE);
    
    fseek(fp, 0, SEEK_SET);
    int ret = fread(program, program_sz, 1, fp);
    assert(ret == 1);
    printf("load program size: 0x%x\n", program_sz);
    return;
}

void init_difftest(char *ref_so_file){
    memset(state.gpr, 0, sizeof(state.gpr));

    state.pc = PC_START;
    state.priv = 0b11;
#ifdef DIFFTEST
    assert(ref_so_file != NULL);

    void* handle;
    handle = dlopen(ref_so_file,  RTLD_LAZY | RTLD_DEEPBIND);
    assert(handle);

    ref_difftest_memcpy_from_dut = (void (*)(paddr_t dest, void *src, size_t n))dlsym(handle, "isa_difftest_memcpy_from_dut");
    assert(ref_difftest_memcpy_from_dut);

    ref_difftest_getregs = (void (*)(void *))dlsym(handle, "isa_difftest_getregs");
    assert(ref_difftest_getregs);

    ref_difftest_setregs = (void (*)(const void *))dlsym(handle, "isa_difftest_setregs");
    assert(ref_difftest_setregs);

    ref_difftest_setregs_all = (void (*)(const void *))dlsym(handle, "isa_difftest_setregs_all");
    assert(ref_difftest_setregs_all);

    ref_difftest_exec = (void (*)(uint64_t))dlsym(handle, "isa_exec_once");
    assert(ref_difftest_exec);

    ref_check_end = (void (*)(void *))dlsym(handle, "is_nemu_trap");
    assert(ref_check_end);

    ref_intr_exec = (void(*)(uint64_t pc, uint64_t cause))dlsym(handle, "isa_intr_exec");
    assert(ref_intr_exec);

    void (*ref_difftest_init)(void) = (void (*)(void))dlsym(handle, "isa_difftest_init");
    assert(ref_difftest_init);

    ref_difftest_init();

    //加载程序到ref中
    ref_difftest_memcpy_from_dut(PC_START, (void*)program, program_sz);
    ref_difftest_setregs_all(&state);
#endif
    //加载程序到dut中
#ifdef FLASH
    flash_memcpy(program, program_sz);
#else
    assert(program_sz <= sizeof(cpu->rootp->newtop__DOT__cpu__DOT__icache__DOT__data));
    memcpy(((unsigned char *)(&cpu->rootp->newtop__DOT__cpu__DOT__icache__DOT__data[0])), program, program_sz);
    memcpy(((unsigned char *)(&cpu->rootp->newtop__DOT__cpu__DOT__dcache__DOT__data[0])), program, program_sz);
#endif
}

long long inst_num = 0;
long long cycle = 0;
time_t init_time;
void disp_ipc(){
     std :: cout << "cycle: " << cycle << " inst_num: " << inst_num << " ipc: " << (double)inst_num / cycle << " time(min): " << (time(NULL) - init_time) /  60 << std::endl;
}

void disp_counter() {
    std::cout << "branchNum: " << cpu->rootp->newtop__DOT__cpu__DOT__execute__DOT__branchCounter << \
                " branchMiss: " << cpu->rootp->newtop__DOT__cpu__DOT__execute__DOT__branchMissCounter << \
                " missRate: " << (double)cpu->rootp->newtop__DOT__cpu__DOT__execute__DOT__branchMissCounter / cpu->rootp->newtop__DOT__cpu__DOT__execute__DOT__branchCounter << std::endl;
}

static int diff_csrs[] = {
    MTVEC_ID, MEPC_ID, MCAUSE_ID, MIE_ID, MIP_ID, MTVAL_ID,
    MSCRATCH_ID, MSTATUS_ID
};

static int csr_num = sizeof(diff_csrs)/sizeof(int);

void disp_state_buf(){
    for(int i = 0; i < 4; i++){
        int idx = (inst_p + i) % 4;
        printf("pc: %lx inst: %x intr/excep: %d cause: %lx\n", state_buf[idx].pc, state_buf[idx].inst, state_buf[idx].intr, state_buf[idx].cause);
    }
}

void print_info(CPU_state* ref_r){
    if(ref_r){
        printf("   ref                    |    dut  \n");
        for(int i = 0; i < 32; i++){
            printf("%-4s    %016lx  | %016lx  \n", regs[i], ref_r->gpr[i], state.gpr[i]);
        }
        printf("pc      %16lx    %16lx\n------\n", ref_r->pc, state.pc);
        for(int i = 0; i < csr_num; i++){
            int id = diff_csrs[i];
            printf("%-8s    %016lx  | %016lx  \n", csrs[i], ref_r->csr[id], state.csr[id]);
        }
        printf("priv    %lx  |  %lx\n", ref_r->priv, state.priv);
    }
    else{
        for(int i = 0; i < 16; i++){
            printf("%-4s    %016lx |   ", regs[i], state.gpr[i]);
            printf("%-4s    %016lx |\n", regs[i+16], state.gpr[i+16]);
        }
        printf("pc      %16lx\n------\n", state.pc);
    }
    disp_state_buf();
}
rtlreg_t ref_pre_pc = PC_START;

bool check_gregs(CPU_state* ref_r){
    if(!check) return 1;
    int jud = true;
    if(!state.intr && ref_pre_pc != state.pc){
        printf("pc diff: %lx %lx\n", ref_pre_pc, state.pc);
        jud = false;
    }
    for(int i = 0; i < 32; i++){
        if(ref_r->gpr[i] != state.gpr[i]){
            printf("reg diff: %s\n", regs[i]);
            jud = false;
        }
    }
    // for(int i = 0; i < csr_num; i++){
    //     int id = diff_csrs[i];
    //     rtlreg_t ref_csr = id == MIP_ID? ref_r->csr[id] & MIP_MASK : ref_r->csr[id];
    //     rtlreg_t state_csr = id == MIP_ID? state.csr[id] & MIP_MASK : state.csr[id];
    //     if(ref_csr != state_csr){
    //         printf("csr diff: %s\n", csrs[i]);
    //         jud = false;
    //     }
    // }
    if(ref_r->priv != state.priv) {
        printf("priv_diff\n");
        jud = false;
    };
    return jud;
}
int end_timer = -1;
int dut_end = 0;
int count = 0;

void clock_cycle(int n){
    while(n--){
        cpu->clock = 0;
        cpu->eval();
#ifdef TRACE
        if(cycle > TRACE_START){
            contextp->timeInc(1);
            tfp->dump(contextp->time());
        }
#endif
        cpu->clock = 1;
        cpu->eval();
#ifdef TRACE
        if(cycle > TRACE_START){
            contextp->timeInc(1);
            tfp->dump(contextp->time());
        }
#endif
        cycle ++;
    }
}
void dut_step(uint32_t n){
    while(n--){
        count = 0;
        clock_cycle(1);
        while(!state.valid){
            clock_cycle(1);
            count ++;
            if(count > 100) {
                print_info(NULL);
                disp_ipc();
#ifdef TRACE
                tfp->close();
#endif
                assert(0);
            }
        }

        inst_num ++;
        state_buf[inst_p] = state;
        inst_p = (inst_p + 1) % 4;
        if(state.valid && (state.inst == 0x6b || state.inst == 0x100073)){
            dut_end = 1;
            break;
        }
    }
}

int is_diff = 0;
void difftest_step(uint32_t n){
    CPU_state ref_r;
    while(n --){
        dut_step(1);
        //ref执行一步
        if(state.is_mmio || state.rcsr_id == MIP_ID || state.rcsr_id == 0xc00){
            memcpy(ref_r.gpr, state.gpr, sizeof(state.gpr));
            ref_r.pc = state.pc + ((state.inst&0x3) == 0x3 ? 4 : 2);
            memcpy(ref_r.csr, state.csr, sizeof(state.csr));
            ref_difftest_setregs(&ref_r);
        }else if(state.intr && (state.cause &(1ULL << 63)) != 0){
            ref_intr_exec(state.excep_pc, state.cause);
        }else{
            ref_difftest_exec(1);
        }
        ref_difftest_getregs(&ref_r);

        // if(check) print_info(&ref_r);
        if(!check_gregs(&ref_r)){
            print_info(&ref_r);
            is_diff = 1;
            return;
        }
        ref_pre_pc = ref_r.pc;
    }
}

void init_csr(){
    state.csr[MISA] = 0x800000000014112dull;
    state.csr[MSTATUS_ID] = 0xa00000000ull;
}

void reset(){
    cpu->reset = 1;
    for(int i = 0; i < 20; i++){
        clock_cycle(1);
    }

    cpu->reset = 0;
    cycle = 0;
    inst_num = 0;
    init_time = time(NULL);
}

int flag = 0;

void int_handeler(int sig) {
	flag = 1;
    longjmp(env,1);
}
void check_and_exit(){
  if(!flag) return;
  disp_ipc();
  disp_counter();
#ifdef TRACE
  tfp->close();
#endif
  exit(0);
}

int main(int argc, char **argv){
    signal(SIGINT, int_handeler);
    signal(SIGABRT, int_handeler);
    cpu = new Vnewtop;
    contextp->commandArgs(argc, argv);
#ifdef TRACE
    contextp->traceEverOn(true);
    cpu->trace(tfp, 0);
    tfp->open("dump.fst");
#endif
    // char filename[] = "am-kernels/benchmarks/microbench/build/microbench-riscv64-nemu.bin";
    // char filename[] = "../ics2020/fceux-am/build/fceux-riscv64-nemu.bin";
    // char filename[] = "am-kernels/tests/cpu-tests/build/loop-riscv64-nemu.bin";
    // char filename[] = "am-kernels/tests/am-tests/build/amtest-riscv64-nemu.bin";
    //nano-lite
    // char filename[] = "../ics2020/nanos-lite/build/nanos-lite-riscv64-nemu.bin";
    //rt-thread
    // char filename[] = "../program/rt-thread/bsp/qemu-riscv-virt64/rtthread.bin";
    //xv6
    // char filename[] = "../program/xv6-riscv/kernel/kernel.bin"
    char* filename = argv[1];

    load_program(filename);
    state.csr[USCRATCH] = -1;
    if(argc >= 3){
        state.csr[USCRATCH] = atoi(argv[2]);
    }
    init_csr();
    char ref_so_path[] = "../../ics2020/nemu/build/riscv64-nemu-interpreter-so";
    init_difftest(ref_so_path);
    printf("after initialization\n");
    reset();
    init_csr();
    // cpu->rootp->newtop__DOT__socfull__DOT__asic__DOT__cpu__DOT__cpu__DOT__csrs__DOT__(uscratch)=state.csr[USCRATCH];
    // cpu->rootp->newtop__DOT__socfull__DOT__asic__DOT__cpu__DOT__cpu__DOT__csrs__DOT__(mstatus)=state.csr[MSTATUS_ID];
    init_vga();
    init_sdcard();
    uint32_t is_end = 0;
    if (setjmp (env) == 0) {
#ifdef DIFFTEST
        check = 1;
        while(!is_end && !is_diff){
            difftest_step(1);
            ref_check_end(&is_end);
            if(inst_num % 10000000 == 0 && inst_num != 0){
                disp_ipc();
            }
            check_and_exit();
        }
#else
        while(!dut_end){
            dut_step(1);
            if(inst_num % 10000000 == 0){
                disp_ipc();
            }
        }
#endif
    }else{
        check_and_exit();
    }
    if(!is_diff){
        if(state.gpr[10] == 0){
            printf("\33[1;32mCPU HIT GOOD TRAP\033[0m\n");
        }else{
            printf("\33[1;31mCPU HIT BAD TRAP\033[0m\n");
        }
    }
    else if(is_diff){
        printf("\33[1;31mCPU HIT BAD TRAP\033[0m\n");
    }
    disp_ipc();
    disp_counter();
#ifdef TRACE
    tfp->close();
#endif
}

