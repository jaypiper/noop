#!/bin/bash
set -e
set -x

for filename in ./bin/riscv-tests/*.bin; do
  ./build/emu -F $filename --diff ./bin/riscv64-nemu-interpreter-so
done

