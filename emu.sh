export NOOP_HOME=~/program/rvcpu
make verilog && \
       	cp build/SimTop.v ~/program/rvcpu/vsrc && \
	make -C $NOOP_HOME clean -j20 && \
	make -C $NOOP_HOME emu -j20 && \
	$NOOP_HOME/build/emu --no-diff -b 0 -e 0 -i ~/ics2020/am-kernels/benchmarks/microbench/build/microbench-riscv64-mycpu-rv64imfd.bin
