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
	@sed -i 's/$$fatal/xs_assert(`__LINE__)/g' $(SIM_TOP_V)

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

RELEASE_V = $(BUILD_DIR)/CPU.v

$(RELEASE_V): clean
	@mkdir -p $(BUILD_DIR)
	sed -i 's/val isSim = true/val isSim = false/' playground/src/noop/common.scala
	mill -i __.test.runMain NoopTop -td $(BUILD_DIR)
	@git log -n 1 >> .__head__
	@git diff >> .__diff__
	@sed -i 's/^/\/\// ' .__head__
	@sed -i 's/^/\/\//' .__diff__
	@cat .__head__ .__diff__ $@ > .__out__
	@mv .__out__ $@
	@rm .__head__ .__diff__
	sed -i 's/val isSim = false/val isSim = true/' playground/src/noop/common.scala
	cat build/*.v > CPU.v

release: clean $(RELEASE_V)

.PHONY: test verilog release sim-verilog help compile bsp idea reformat checkformat clean emu
