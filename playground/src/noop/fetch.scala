package noop.fetch

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.bpu._
import noop.tlb._
import noop.datapath._

class FetchCrossBar extends Module{
    val io = IO(new Bundle{
        val instIO = new IcacheRead
        val icRead = Flipped(new IcacheRead)
        val flashRead = Flipped(new DcacheRW)
    })
    val pre_mem = RegInit(false.B)
    val inp_mem = io.instIO.addr(PADDR_WIDTH-1)
    io.flashRead.addr   := io.instIO.addr
    io.flashRead.wdata  := 0.U; io.flashRead.amo := 0.U
    io.flashRead.dc_mode := mode_NOP
    io.icRead.addr      := io.instIO.addr
    io.icRead.arvalid   := false.B
    io.instIO.ready     := false.B
    when(io.instIO.arvalid){
        pre_mem := io.instIO.addr(PADDR_WIDTH-1)
        when(inp_mem){
            io.icRead.arvalid := true.B
            io.instIO.ready := io.icRead.ready
        }.otherwise{
            io.flashRead.dc_mode := mode_LWU
            io.instIO.ready := io.flashRead.ready
        }
    }
    io.instIO.inst      := 0.U
    io.instIO.rvalid    := false.B
    when(pre_mem){
        io.instIO.inst  := io.icRead.inst
        io.instIO.rvalid := io.icRead.rvalid
    }.otherwise{
        io.instIO.inst  := io.flashRead.rdata
        io.instIO.rvalid := io.flashRead.rvalid
    }

}

class FetchIO extends Bundle{
    val bpuSearch   = Flipped(new BPUSearch)
    val instRead    = Flipped(new IcacheRead)
    val va2pa       = Flipped(new VA2PA)
    val reg2if      = Input(new ForceJmp)
    val wb2if       = Input(new ForceJmp)
    val recov       = Input(Bool())
    val intr_in     = Flipped(new RaiseIntr)
    val branchFail  = Input(new ForceJmp)
    val if2id       = new IF2ID
}

class Fetch extends Module{
    val io = IO(new FetchIO)
    val pc = RegInit(PC_START)

    val drop1_r = RegInit(false.B)
    val drop2_r = RegInit(false.B)
    val drop3_r = RegInit(false.B)
    val stall1_r = RegInit(false.B)
    val stall2_r = RegInit(false.B)
    val stall3_r = RegInit(false.B)
    val recov1_r = RegInit(false.B)
    val recov2_r = RegInit(false.B)
    val recov3_r = RegInit(false.B)
    drop1_r := false.B; drop2_r := false.B; drop3_r := false.B;
    stall1_r := false.B; stall2_r := false.B; stall3_r := false.B
    def stall_pipe1(){
        drop1_r := true.B;  stall1_r := true.B; recov1_r := true.B
    }
    def stall_pipe2(){
        drop2_r := true.B;  stall2_r := true.B; recov2_r := true.B
    }
    def stall_pipe3(){
        drop3_r := true.B;  stall3_r := true.B; recov3_r := true.B
    }
    val drop3_in = drop3_r || io.if2id.drop
    val drop2_in = drop2_r || drop3_in
    val drop1_in = drop1_r || drop2_in
    val stall3_in = (stall3_r && !io.if2id.drop) || io.if2id.stall
    val stall2_in = (stall2_r && !drop3_in) || stall3_in
    val stall1_in = (stall1_r && !drop2_in) || stall2_in

    val sIdle :: sStall :: Nil = Enum(2)
    val state = RegInit(sIdle)
    switch(state){
        is(sIdle){
            when(stall1_in){
                state := sStall
            }
        }
        is(sStall){
            when((drop1_in && !stall1_in) || io.recov){
                state := sIdle
            }
        }
    }
// stage 1
    val pc1_r = RegInit(0.U(VADDR_WIDTH.W))
    val br_next_pc1_r = RegInit(0.U(VADDR_WIDTH.W))
    val excep1_r    = RegInit(0.U.asTypeOf(new Exception))
    excep1_r.en     := io.intr_in.en
    excep1_r.tval   := io.intr_in.cause
    val handshake12 = Wire(Bool())
    val handshake23 = Wire(Bool())

    val cur_pc = PriorityMux(Seq(
        // (io.branchFail.valid)
        (!handshake12,              pc),
        (io.bpuSearch.is_target,    io.bpuSearch.target),
        (handshake12,               pc + 4.U),
        (true.B,                    pc)
    ))
    val next_pc = PriorityMux(Seq(
            (io.reg2if.valid,               io.reg2if.seq_pc),
            (io.wb2if.valid,                io.wb2if.seq_pc),
            (io.intr_in.en,                 cur_pc),
            (io.branchFail.valid,           io.branchFail.seq_pc),
            // (handshake12,                   cur_pc + 4.U),
            (true.B,                        cur_pc)))
    pc := next_pc
    pc1_r := cur_pc
    br_next_pc1_r := next_pc
    val if_working = !drop1_in && state === sIdle
    // bpu
    io.bpuSearch.vaddr := cur_pc
    io.bpuSearch.va_valid := if_working
    // tlb
    io.va2pa.vaddr := cur_pc
    io.va2pa.vvalid := if_working && !io.intr_in.en
    io.va2pa.m_type := MEM_FETCH

// stage 2
    val pc2_r       = RegInit(0.U(VADDR_WIDTH.W))
    val br_next_pc2_r  = RegInit(0.U(VADDR_WIDTH.W))
    val paddr2_r    = RegInit(0.U(PADDR_WIDTH.W))
    val valid2_r    = RegInit(false.B)
    val excep2_r    = RegInit(0.U.asTypeOf(new Exception))
    val is_target2_r = RegInit(false.B)
    val target2_r   = RegInit(0.U(VADDR_WIDTH.W))

    val reset_tlb   = RegInit(false.B)
    when(io.va2pa.pvalid || io.va2pa.tlb_excep.en){
        reset_tlb   := false.B
    }

    val tlb_inp_valid   = !reset_tlb && (io.va2pa.pvalid || io.va2pa.tlb_excep.en)

    handshake12 := false.B
    when(!drop2_in){
        when(valid2_r && !handshake23){
            handshake12 := false.B
        }.elsewhen(tlb_inp_valid && state === sIdle){
            handshake12 := true.B
        }
    }
    when(!drop3_in){
        when(handshake12){
            valid2_r        := true.B
            pc2_r           := pc1_r
            br_next_pc2_r   := br_next_pc1_r
            is_target2_r    := io.bpuSearch.is_target
            target2_r       := io.bpuSearch.target
            recov2_r        := recov1_r
        }.elsewhen(handshake23){
            valid2_r := false.B
        }
        when(!handshake12){
        }.elsewhen(io.va2pa.pvalid){
            paddr2_r    := io.va2pa.paddr
            excep2_r.en    := false.B
        }.elsewhen(io.va2pa.tlb_excep.en){
            excep2_r.pc    := pc1_r
            excep2_r.en    := true.B
            excep2_r.tval  := io.va2pa.tlb_excep.tval
            excep2_r.cause := io.va2pa.tlb_excep.cause
            stall_pipe2()
        }
    }.otherwise{
        valid2_r := false.B
        reset_tlb := !(io.va2pa.pvalid || io.va2pa.tlb_excep.en)
    }
    io.instRead.addr := Mux(handshake12, io.va2pa.paddr, paddr2_r)
    val cur_excep_en = Mux(handshake12, io.va2pa.tlb_excep.en, excep2_r.en)
    io.instRead.arvalid := (handshake12 || valid2_r) && !io.if2id.drop && !cur_excep_en
// stage 3
    val pc3_r           = RegInit(0.U(VADDR_WIDTH.W))
    val br_next_pc3_r   = RegInit(0.U(VADDR_WIDTH.W))
    val valid3_r        = RegInit(false.B)
    val excep3_r        = RegInit(0.U.asTypeOf(new Exception))
    val is_target3_r    = RegInit(false.B)
    val target3_r       = RegInit(0.U(VADDR_WIDTH.W))
    val inst_r          = RegInit(0.U(INST_WIDTH.W))

    val reset_ic    = RegInit(false.B)
    when(io.instRead.rvalid){
        reset_ic := false.B
    }
    val handshakeOut = io.if2id.ready && io.if2id.valid
    handshake23 := false.B
    when(!drop3_in){
        when(valid3_r && !handshakeOut){
            handshake23 := false.B
        }.elsewhen(excep2_r.en && valid2_r){
            handshake23 := true.B
            inst_r  := 0.U
            excep3_r := excep2_r
        }.elsewhen(valid2_r && io.instRead.rvalid && !reset_ic){
            handshake23 := true.B
            inst_r := io.instRead.inst
        }
    }
    when(!io.if2id.drop){
        when(handshake23){
            valid3_r := true.B
            pc3_r := pc2_r
            br_next_pc3_r := br_next_pc2_r
            excep3_r := excep2_r
            is_target3_r := is_target2_r
            target3_r   := target2_r
            recov3_r    := recov2_r
        }.elsewhen(handshakeOut){
            valid3_r := false.B
        }
    }.otherwise{
        valid3_r := false.B
        reset_ic := reset_ic || (valid2_r && !excep2_r.en && !io.instRead.rvalid)
    }

    io.if2id.inst       := inst_r
    io.if2id.pc         := pc3_r
    io.if2id.br_next_pc := br_next_pc3_r
    io.if2id.excep      := excep3_r
    io.if2id.is_target  := is_target3_r
    io.if2id.target     := target3_r
    io.if2id.valid      := valid3_r
    io.if2id.recov      := recov3_r
}