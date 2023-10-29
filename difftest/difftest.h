
#ifndef DIFFTEST_H
#define DIFFTEST_H
#include <dlfcn.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "common.h"
#include <assert.h>
#include <csetjmp>
#include <vector>
#include "../obj_dir/Vnewtop.h"
#include "../obj_dir/Vnewtop___024root.h"

#include <iostream>
#include <elf.h>
#include <SDL2/SDL.h>

const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

const char *csrs[] = {
  "mtvec", "mepc", "mcause", "mie", "mip", "mtval", "mscratch", "mstatus",
};

extern "C" void flash_memcpy(uint8_t* src, size_t len);

void get_regs(CPU_state* state);
extern "C" void update_reg(int id, long long val);

int SDL_Init(Uint32 flags);
void init_sdcard();

#define TRACE_START 0

#endif