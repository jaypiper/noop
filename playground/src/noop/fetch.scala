package noop.fetch

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.bpu._
import noop.datapath._

class FetchCrossBar extends Module{
    val io = IO(new Bundle{
        val instIO = new IcacheRead
        val icRead = Flipped(new IcacheRead)
        val flashRead = Flipped(new DcacheRW)
    })
    val pre_mem = RegInit(false.B)
    val sMem :: sFlash :: Nil = Enum(2)
    val state = RegInit(sMem)
    val memNum = RegInit(0.U(2.W))
    val flashNum = RegInit(0.U(2.W))
    val inp_mem = (io.instIO.addr >= "h80000000".U) && (io.instIO.addr < "h80008000".U)
    io.flashRead.addr   := io.instIO.addr
    io.flashRead.wdata  := 0.U;
    io.flashRead.wen    := false.B
    io.flashRead.wmask  := 0.U
    io.flashRead.size      := 2.U
    io.flashRead.avalid := false.B
    io.icRead.addr      := io.instIO.addr
    io.icRead.arvalid   := false.B
    io.instIO.ready     := false.B
    io.icRead.rready := io.instIO.rready
    io.instIO.inst      := 0.U
    io.instIO.rvalid    := false.B
    val hs_in = io.instIO.ready && io.instIO.arvalid
    val hs_out = io.instIO.rready && io.instIO.rvalid
    when (hs_in && !hs_out) {
        memNum := memNum + inp_mem
    }.elsewhen(!hs_in && hs_out) {
        memNum := memNum - (state === sMem)
    }
    when(hs_in && !io.instIO.rvalid) {
        flashNum := flashNum + !inp_mem
    }.elsewhen(!hs_in && io.instIO.rvalid) {
        flashNum := 0.U
    }
    switch(state) {
        is(sMem) {
            when (!inp_mem && io.instIO.arvalid) {
                when(memNum === 0.U) {
                    state := sFlash
                }
            }.otherwise {
                io.instIO.ready := io.icRead.ready
                io.icRead.arvalid := io.instIO.arvalid
            }
            io.instIO.inst  := io.icRead.inst
            io.instIO.rvalid := io.icRead.rvalid
        }
        is(sFlash) {
            when (inp_mem && io.instIO.arvalid) {
                when(flashNum === 0.U) {
                    state := sMem
                }
            }.otherwise {
                io.flashRead.avalid := io.instIO.arvalid
                io.instIO.ready := io.flashRead.ready
            }
            io.instIO.inst  := io.flashRead.rdata
            io.instIO.rvalid := io.flashRead.rvalid
        }
    }
}

class FetchIO extends Bundle{
    val instRead    = Flipped(new IcacheRead)
    val reg2if      = Input(new ForceJmp)
    val wb2if       = Input(new ForceJmp)
    val recov       = Input(Bool())
    // val intr_in     = Input(new RaiseIntr)
    val branchFail  = Input(new ForceJmp)
    val if2id       = new IF2ID
    val bp          = Flipped(new PredictIO2)
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

    val pc_r           = RegInit(0.U(PADDR_WIDTH.W))
    val valid_r        = RegInit(false.B)

    val pc2_r = RegInit(0.U(PADDR_WIDTH.W))
    val nextPC2_r = RegInit(0.U(PADDR_WIDTH.W))
    val inst2_r = RegInit(0.U(INST_WIDTH.W))
    val inst_valid2_r = RegInit(false.B)
    val valid2_r = RegInit(false.B)
    val hs2 = Wire(Bool())

    val next_pc = PriorityMux(Seq(
            (io.reg2if.valid,               io.reg2if.seq_pc),
            (io.wb2if.valid,                io.wb2if.seq_pc),
            (io.branchFail.valid,           io.branchFail.seq_pc),
            (io.bp.jmp && hs1,                     io.bp.target),
            (hs1,                        pc + 4.U),
            (true.B,                        pc)))
    pc := next_pc    

    io.bp.pc := pc
    io.bp.valid := hs1

    io.instRead.addr := pc
    io.instRead.arvalid := state === sIdle && (!valid_r || hs2)

    when(!drop_in) {
        when(hs1) {
            pc_r := pc
            valid_r := true.B
        }.elsewhen(hs2) {
            valid_r := false.B
        }
    } .otherwise {
        valid_r := false.B
    }

    hs2 := false.B
    val hs_iram = io.instRead.rvalid && io.instRead.rready
    when(!drop_in){
        when(hs2){
            pc2_r := pc_r
            nextPC2_r := pc
            valid2_r := valid_r
        }.elsewhen(hs_out) {
            valid2_r := false.B
        }
        when(hs_out || !valid2_r) {
            hs2 := valid_r
        }.elsewhen(valid2_r) {
            hs2 := false.B
        }
        when(hs_out) {
            inst_valid2_r := inst_valid2_r && hs_iram
            when(hs_iram) {
                inst2_r := io.instRead.inst
            }
        }.elsewhen(hs_iram) {
            inst_valid2_r := valid2_r
            inst2_r := io.instRead.inst
        }
    }.otherwise {
        inst_valid2_r := false.B
        valid2_r := false.B
    }
    io.instRead.rready := !inst_valid2_r || hs_out
    io.if2id.inst := Mux(inst_valid2_r, inst2_r, io.instRead.inst)
    io.if2id.pc := pc2_r
    io.if2id.valid := valid2_r && (inst_valid2_r || io.instRead.rvalid)
    io.if2id.recov := false.B  // TODO: remove
    io.if2id.nextPC := nextPC2_r
}