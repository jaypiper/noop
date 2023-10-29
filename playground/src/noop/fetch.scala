package noop.fetch

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.bpu._
import noop.datapath._


class FetchIO extends Bundle{
    val instRead    = Flipped(new IcacheRead)
    val reg2if      = Input(new ForceJmp)
    val wb2if       = Input(new ForceJmp)
    val recov       = Input(Bool())
    // val intr_in     = Input(new RaiseIntr)
    val branchFail  = Input(new ForceJmp)
    val if2id       = new IF2ID
}

class Fetch extends Module{
    val io = IO(new FetchIO)
    val pc = RegInit(PC_START)

    val drop1_r = RegInit(false.B)
    val stall1_r = RegInit(false.B)
    val recov1_r = RegInit(false.B)
    drop1_r := false.B;
    stall1_r := false.B;
    def stall_pipe1(){
        drop1_r := true.B;  stall1_r := true.B; recov1_r := true.B
    }
    val drop_in = io.if2id.drop
    val stall_in = io.if2id.stall

    val sIdle :: sStall :: Nil = Enum(2)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            when(stall_in){
                state := sStall
            }
        }
        is(sStall){
            when((drop_in && !stall_in) || io.recov){
                state := sIdle
            }
        }
    }

    val hs1         = io.instRead.arvalid && io.instRead.ready
    val hs_out      = io.if2id.ready && io.if2id.valid

    val pc_r           = RegInit(0.U(VADDR_WIDTH.W))
    val valid_r        = RegInit(false.B)
    val inst_r          = RegInit(0.U(INST_WIDTH.W))
    val inst_valid_r    = RegInit(false.B)

    val next_pc = PriorityMux(Seq(
            (io.reg2if.valid,               io.reg2if.seq_pc),
            (io.wb2if.valid,                io.wb2if.seq_pc),
            (io.branchFail.valid,           io.branchFail.seq_pc),
            (hs_out,                        pc + 4.U),
            (true.B,                        pc)))
    pc := next_pc    

    io.instRead.addr := next_pc
    io.instRead.arvalid := state === sIdle && !valid_r || hs_out

    when(hs1) {
        pc_r := next_pc
        valid_r := true.B
    }.elsewhen(hs_out) {
        valid_r := false.B
    }
    when (io.instRead.rvalid) {
        inst_r := io.instRead.inst
    }

    when(!drop_in) {
        when(hs_out) {
            inst_valid_r := false.B
        } .elsewhen(io.instRead.rvalid) {
            inst_valid_r := true.B
            inst_r := io.instRead.inst
        }
        when(hs1) {
            pc_r := next_pc
            valid_r := true.B
        }.elsewhen(hs_out) {
            valid_r := false.B
        }
    } .otherwise {
        inst_valid_r := false.B
        valid_r := false.B
    }

    io.if2id.inst       := Mux(inst_valid_r, inst_r, io.instRead.inst)
    io.if2id.pc         := pc_r
    io.if2id.excep      := 0.U.asTypeOf(new Exception)
    io.if2id.valid      := !drop_in && valid_r && (io.instRead.rvalid || inst_valid_r)
    io.if2id.recov      := false.B
}