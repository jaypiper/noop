#!/bin/bash

output_path="./test_output"

program_without_para=(
    "amoadd_d-riscv64-nemu"
    "amoadd_w-riscv64-nemu"
    "amoand_d-riscv64-nemu"
    "to-lower-case-riscv64-mycpu-rv64gc"
    "wanshu-riscv64-mycpu-rv64gc"
    "bubble-sort-riscv64-mycpu-rv64gc"
    "bit-riscv64-mycpu-rv64gc"
    "pascal-riscv64-mycpu-rv64gc"
    "add-longlong-riscv64-mycpu-rv64gc"
    "shift-riscv64-mycpu-rv64gc"
    "max-riscv64-mycpu-rv64gc"
    "if-else-riscv64-mycpu-rv64gc"
    "leap-year-riscv64-mycpu-rv64gc"
    "div-riscv64-mycpu-rv64gc"
    "recursion-riscv64-mycpu-rv64gc"
    "mul-longlong-riscv64-mycpu-rv64gc"
    "switch-riscv64-mycpu-rv64gc"
    "mov-c-riscv64-mycpu-rv64gc"
    "string-riscv64-mycpu-rv64gc"
    "unalign-riscv64-mycpu-rv64gc"
    "movsx-riscv64-mycpu-rv64gc"
    "goldbach-riscv64-mycpu-rv64gc"
    "quick-sort-riscv64-mycpu-rv64gc"
    "prime-riscv64-mycpu-rv64gc"
    "select-sort-riscv64-mycpu-rv64gc"
    "fact-riscv64-mycpu-rv64gc"
    "dummy-riscv64-mycpu-rv64gc"
    "shuixianhua-riscv64-mycpu-rv64gc"
    "load-store-riscv64-mycpu-rv64gc"
    "add-riscv64-mycpu-rv64gc"
    "hello-str-riscv64-mycpu-rv64gc"
    "sub-longlong-riscv64-mycpu-rv64gc"
    "min3-riscv64-mycpu-rv64gc"
    "sum-riscv64-mycpu-rv64gc"
    "fib-riscv64-mycpu-rv64gc"
    "matrix-mul-riscv64-mycpu-rv64gc"
    "microbench-riscv64-mycpu-test-rv64gc"
    "bbl-rv64gc"
    "bbl-busybox"
)

num_without_para=${#program_without_para[*]}
running_num=0

make clean
make verilog
make compile-verilator SIM=1 DIFF=1 TRACE=1 FLASH=0

# 0:dead 1:running 2:fail 3:pass
for ((i=0; i < ${num_without_para}; i++))
do
    bin=${program_without_para[i]}
    if test -e "./bin/${bin}.bin"
    then
        echo -e "testing ${bin}.bin ..."
        ./obj_dir/$1 $2/${bin}.bin &> ${output_path}/${bin}.txt &
        running_num=`expr ${running_num} + 1`
        program_without_para_state[i]=1
    else
        echo -e "program ${bin}.bin does not exist"
        program_without_para_state[i]=0
    fi
done

# xv6_testcases=(16 17 19 21 22 25 26 29 32 36 45 46 55)
# xv6_testnum=${#xv6_testcases[*]}
xv6_testnum=59
xv6_running_num=0
xv6_usertest_name=(
    "preempt"       "copyin"        "copyout"       "openiput"  "forkforkfork"
    "killstatus"    "sbrkbugs"      "kernmem"       "sbrkfail"  "stacktest"
    "copyinstr1"    "copyinstr2"    "copyinstr3"    "rwsbrk"    "truncate1"
    "truncate2"     "truncate3"     "reparent2"     "pgbug"     "badarg"
    "reparent"      "manywrites"    "twochildren"   "forkfork"  "argptest"
    "createdelete"  "linkunlink"    "linktest"      "unlinkread" "concreate"
    "subdir"        "fourfiles"     "sharedfd"      "dirtest"   "exectest"
    "bigargtest"    "bigwrite"      "bsstest"       "sbrkbasic" "sbrkarg"
    "sbrklast"      "sbrk8000"      "validatetest"  "opentest"  "writetest"
    "writebig"      "createtest"    "exitiput"      "iput"      "mem"
    "pipe1"         "rmdot"         "fourteen"      "bigfile"   "dirfile"
    "iref"          "forktest"      "execout"       "exitwait"  "bigdir"
)

if test -e "./bin/kernel.bin"
then
    xv6_running_num=${xv6_testnum}
    for ((i=0; i < ${xv6_testnum}; i++))
    do
        id=$i
        # id=${xv6_testcases[i]}
        echo -e "testing xv6-${id}-${xv6_usertest_name[id]} ..."
        ./obj_dir/$1 $2/kernel.bin ${id} &> ${output_path}/kernel${id}-${xv6_usertest_name[id]}.txt&
        xv6_state[i]=1
    done
else
    echo -e "program xv6(kernel.bin) does not exist"
fi


running_num=`expr ${running_num} + ${xv6_running_num}`

finish_num=0
pass_num=0
fail_num=0
while ((${finish_num} < ${running_num}))
do
    for ((i=0; i < ${num_without_para}; i++))
    do
        if ((${program_without_para_state[i]} == 1))
        then
            bin=${program_without_para[i]}
            if (grep 'HIT BAD TRAP' ${output_path}/${bin}.txt)
            then
                finish_num=`expr ${finish_num} + 1`
                fail_num=`expr ${fail_num} + 1`
                program_without_para_state[i]=2
                echo -e "${bin} failed!! (pass ${pass_num} fail ${fail_num} / total ${running_num})"
            elif (grep 'HIT GOOD TRAP' ${output_path}/${bin}.txt)
            then
                finish_num=`expr ${finish_num} + 1`
                pass_num=`expr ${pass_num} + 1`
                program_without_para_state[i]=3
                echo -e "${bin} passed!! (pass ${pass_num} fail ${fail_num} / total ${running_num})"
            fi
        fi
    done
    if ((${xv6_running_num} != 0))
    then
        for ((i=0; i < ${xv6_testnum}; i++))
        do
            # id=${xv6_testcases[i]}
            id=$i
            if ((${xv6_state[i]} == 1))
            then
                if (grep 'HIT BAD TRAP' ${output_path}/kernel${id}-${xv6_usertest_name[i]}.txt)
                then
                    finish_num=`expr ${finish_num} + 1`
                    fail_num=`expr ${fail_num} + 1`
                    xv6_state[i]=2
                    echo -e "xv6-${id}-${xv6_usertest_name[id]} failed!! (pass ${pass_num} fail ${fail_num} / total ${running_num})"
                elif (grep 'HIT GOOD TRAP' ${output_path}/kernel${id}-${xv6_usertest_name[id]}.txt)
                then
                    finish_num=`expr ${finish_num} + 1`
                    pass_num=`expr ${pass_num} + 1`
                    xv6_state[i]=3
                    echo -e "xv6-${id}-${xv6_usertest_name[id]} passed!! (pass ${pass_num} fail ${fail_num} / total ${running_num})"
                fi
            fi
        done
    fi
done
