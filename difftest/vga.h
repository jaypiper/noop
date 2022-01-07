#ifndef VGA_H
#define VGA_H
#include <dlfcn.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include "common.h"
#include <SDL2/SDL.h>

#include "../obj_dir/VSimTop.h"

#include "verilated.h"
#include "verilated_fst_c.h"

void init_vga();
void vga_update_screen();
void update_screen();

#endif