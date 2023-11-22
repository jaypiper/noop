NAME      = SimTop
BUILD_DIR = $(abspath ./build)
SIM_TOP_V = $(BUILD_DIR)/$(NAME).v

export NOOP_HOME := $(abspath .)

init:
	git submodule update --init

test:
	mill -i __.test

SCALA_FILE =  $(shell find ./playground -name '*.scala')
$(SIM_TOP_V): $(SCALA_FILE)
	@mkdir -p $(BUILD_DIR)
	mill -i __.test.runMain NoopTop -td $(BUILD_DIR)

verilog: $(SIM_TOP_V)

sim-verilog: verilog

help:
	mill -i __.test.runMain NoopTop --help

compile:
	mill -i __.compile

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

clean:
	@-rm -rf $(BUILD_DIR)

emu: sim-verilog
	$(MAKE) -C difftest emu WITH_CHISELDB=0 WITH_CONSTANTIN=0

.PHONY: test verilog sim-verilog help compile bsp idea reformat checkformat clean emu
