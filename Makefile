BUILD_DIR = ./build
OBJ_DIR = ./obj_dir

test:
	mill -i __.test

verilog:
	mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain NoopTop -td $(BUILD_DIR)

help:
	mill -i __.test.runMain NoopTop --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	-rm -rf $(BUILD_DIR)
	-rm -rf $(OBJ_DIR)

PWD = $(shell pwd)
# PERP_DIR = $(PWD)/peripheral
PERP_DIR = /home/chenlu/ysyx/ysyxSoC/ysyx/peripheral
# component = $(shell ls peripheral)
component = $(shell ls /home/chenlu/ysyx/ysyxSoC/ysyx/peripheral)
# INC_DIR = $(addprefix $(PERP_DIR)/, $(component))
INC_DIR +=  $(PERP_DIR)/uart16550/rtl $(PERP_DIR)/spi/rtl $(PERP_DIR)/spiFlash# $(PERP_DIR)/axi2apb/inner
VFLAGS = $(addprefix -I, $(INC_DIR))
VFLAGS += --exe --trace-fst --trace-underscore --timescale "1ns/1ns"
VFLAGS += --trace-threads 4 --threads 3
VFLAGS += --autoflush
VFLAGS += --compiler clang

CORE = $(shell find build | grep -xo .*\.v)
CSRCS = $(shell find $(PERP_DIR) difftest | grep -xPo '.*\.(cpp|c)')

LDFLAGS = -ldl -lSDL2 -O3 -Og -fPIE
CFLAGS = -O3 -Og -pthread $(shell sdl2-config --cflags) -fPIE -g

TRACE?=0
SIM?=1
FLASH?=0
DIFF?=0

ifeq ($(FLASH),1)
	CFLAGS += -DFLASH
endif
ifeq ($(TRACE),1)
	CFLAGS += -DTRACE
endif

ifeq ($(SIM),1)
	CFLAGS += -DSIM
	VSRCS = $(CORE)
else
	VSRCS = $(CORE) $(PWD)/top/newtop.v #/home/chenlu/ysyx/ysyxSoC/ysyx/soc/ysyxSoCFull.v
	VSRCS += $(shell find /home/chenlu/ysyx/ysyxSoC/ysyx/soc | grep -xo .*\.v)
endif
NAME = newtop
VFLAGS += --top newtop
ifeq ($(DIFF),1)
	CFLAGS += -DDIFFTEST
endif


PROGRAM_DIR = ./bin
BIN?=hello-riscv64-mycpu

compile-verilator:
	# verilator --lint-only -Wall -Wno-DECLFILENAME -Wno-UNUSED ./build/CPU.v ./build/ram.v
	time verilator $(VFLAGS) -j 8 --cc $(VSRCS) -CFLAGS "$(CFLAGS)" -LDFLAGS "$(LDFLAGS)" --exe $(CSRCS)
	time make -s OPT_FAST="-O3" CXX=clang -j -C ./obj_dir -f V$(NAME).mk V$(NAME)

difftest:
	make compile-verilator
	time ./obj_dir/V$(NAME) $(PROGRAM_DIR)/$(BIN).bin ${mainargs}
# copy:
# 	cp $(BUILD_DIR)/CPU.v ./peripheral/core/

nemu:
	make -C ./nemu ISA=riscv64 SHARE=1 FLASH=$(FLASH)

test-all:
	bash test.sh V$(NAME) $(PROGRAM_DIR)

.PHONY: test verilog help compile bsp reformat checkformat clean difftest nemu test-all
