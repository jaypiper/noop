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
            io.flashRead.dc_mode := mode_LD
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
    val instRead    = Flipped(new IcacheRead)
    val va2pa       = Flipped(new VA2PA)
    val reg2if      = Input(new ForceJmp)
    val wb2if       = Input(new ForceJmp)
    val recov       = Input(Bool())
    val intr_in     = Input(new RaiseIntr)
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
    val is_flash = RegInit(false.B)
    val excep1_r    = RegInit(0.U.asTypeOf(new Exception))
    val valid1_r    = RegInit(false.B)
    val hs_in       = state === sIdle && !drop1_in
    val hs1         = Wire(Bool())
    val hs2         = Wire(Bool())

    val cur_pc = PriorityMux(Seq(
        (hs1,                       pc + 8.U),
        (true.B,                    pc)
    ))
    val next_pc = PriorityMux(Seq(
            (io.reg2if.valid,               io.reg2if.seq_pc),
            (io.wb2if.valid,                io.wb2if.seq_pc),
            (io.intr_in.en,                 cur_pc),
            (io.branchFail.valid,           io.branchFail.seq_pc),
            (true.B,                        cur_pc)))
    pc := next_pc
    pc1_r := cur_pc
    //intr
    when(hs_in){
        excep1_r.en     := io.intr_in.en
        excep1_r.pc     := 0.U          // pc will be set in execute stage
        excep1_r.cause  := io.intr_in.cause
        excep1_r.tval   := 0.U
        excep1_r.etype  := 0.U
        when(io.intr_in.en){
            stall_pipe1()
        }.otherwise{
            recov1_r := false.B
        }
    }
    when(!drop2_in){
        when(hs_in){
            valid1_r := true.B
        }.elsewhen(hs1){
            valid1_r := false.B
        }
    }.otherwise{
        valid1_r := false.B
    }

    // tlb
    io.va2pa.vaddr := cur_pc
    io.va2pa.vvalid := hs_in && !io.intr_in.en
    io.va2pa.m_type := MEM_FETCH

// stage 2
    val pc2_r       = RegInit(0.U(VADDR_WIDTH.W))
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

    hs1 := false.B
    when(!drop2_in){
        when(valid2_r && !hs2){
            hs1 := false.B
        }.elsewhen(tlb_inp_valid || excep1_r.en){
            hs1 := valid1_r
        }
    }
    when(!drop3_in){
        when(hs1){
            valid2_r        := true.B
            pc2_r           := pc1_r
            recov2_r        := recov1_r
        }.elsewhen(hs2){
            valid2_r := false.B
        }
        when(!hs1){
        }.elsewhen(io.va2pa.pvalid){
            paddr2_r    := io.va2pa.paddr
            excep2_r.en    := false.B
        }.elsewhen(io.va2pa.tlb_excep.en){
            excep2_r.pc    := pc1_r
            excep2_r.en    := true.B
            excep2_r.tval  := io.va2pa.tlb_excep.tval
            excep2_r.cause := io.va2pa.tlb_excep.cause
            stall_pipe2()
        }.otherwise{
            excep2_r    := excep1_r
        }
    }.otherwise{
        valid2_r := false.B
        reset_tlb := !(io.va2pa.pvalid || io.va2pa.tlb_excep.en)
    }
    io.instRead.addr := Mux(hs1, io.va2pa.paddr, paddr2_r)
    val cur_excep_en = Mux(hs1, io.va2pa.tlb_excep.en || excep1_r.en, excep2_r.en)
    io.instRead.arvalid := (hs1 || valid2_r) && !io.if2id.drop && !cur_excep_en
// stage 3
    val pc3_r           = RegInit(0.U(VADDR_WIDTH.W))
    val valid3_r        = RegInit(false.B)
    val excep3_r        = RegInit(0.U.asTypeOf(new Exception))
    val next_pc_r       = RegInit(0.U(VADDR_WIDTH.W))
    val wait_jmp_pc     = RegInit(true.B)
    val inst_buf        = RegInit(0.U(128.W))
    val buf_start_pc    = RegInit(0.U(VADDR_WIDTH.W))
    val buf_bitmap      = RegInit(0.U(2.W))
    val excep_buf       = RegInit(0.U.asTypeOf(new Exception))
    val inst_r          = RegInit(0.U(INST_WIDTH.W))
    val next_inst_buf   = Wire(UInt(128.W))
    val next_buf_bitmap = Wire(UInt(2.W))
    val reset_ic    = RegInit(false.B)
    when(io.instRead.rvalid){
        reset_ic := false.B
    }
    hs2 := false.B
    val buf_offset = (next_pc_r - buf_start_pc)
    val hs_out = io.if2id.ready && io.if2id.valid
    next_inst_buf := inst_buf
    next_buf_bitmap := buf_bitmap
    when(!drop3_in){
        when(buf_bitmap === 3.U || excep_buf.en){        // buf full
        }.elsewhen(excep2_r.en && valid2_r){
            excep_buf := excep2_r
            hs2 := true.B
        }.elsewhen(valid2_r && io.instRead.rvalid && !reset_ic){
            hs2 := true.B
            when(buf_bitmap(0)){
                next_inst_buf := Cat(io.instRead.inst, inst_buf(63,0))
                next_buf_bitmap := 3.U
            }.otherwise{
                next_inst_buf := Cat(0.U(64.W), io.instRead.inst)
                next_buf_bitmap := 1.U
                buf_start_pc := Cat(pc2_r(63,3), 0.U(3.W))
                when(wait_jmp_pc){
                    next_pc_r   := pc2_r
                    wait_jmp_pc := false.B
                }
            }
        }
        buf_bitmap := next_buf_bitmap
        inst_buf := next_inst_buf
    }.otherwise{
        buf_bitmap := 0.U
        excep_buf.en := false.B
        reset_ic := reset_ic || (valid2_r && !excep2_r.en && !io.instRead.rvalid)
        wait_jmp_pc := true.B
    }
    val inst_valid = Wire(Bool())
    val top_inst = Wire(UInt(INST_WIDTH.W))
    val top_inst32 = inst_buf >> Cat(buf_offset(2,0), 0.U(3.W))
    inst_valid := false.B; top_inst := 0.U
    when(buf_bitmap === 3.U){
        inst_valid := true.B
        top_inst := Mux(top_inst32(1,0) === 3.U, top_inst32, Cat(0.U(16.W), top_inst32(15,0)))
    }.elsewhen(buf_bitmap === 1.U){
        when(buf_offset === 6.U && top_inst32(1,0) =/= 3.U){
            inst_valid := true.B
            top_inst := Cat(0.U(16.W), top_inst32(15,0))
        }.elsewhen(buf_offset <= 4.U){
            inst_valid := true.B
            top_inst := Mux(top_inst32(1,0) === 3.U, top_inst32, Cat(0.U(16.W), top_inst32(15,0)))
        }
    }

    when(!io.if2id.drop){
        when((inst_valid || excep_buf.en) && (!valid3_r || hs_out)){
            valid3_r    := true.B
            excep3_r    := Mux(inst_valid, 0.U.asTypeOf(new Exception), excep_buf)
            inst_r      := Mux(inst_valid, top_inst, 0.U)
            pc3_r       := next_pc_r
            val next_pc_w = next_pc_r + Mux(top_inst(1,0) === 3.U, 4.U, 2.U)
            next_pc_r   := next_pc_w
            when((next_pc_w - buf_start_pc) >= 8.U && buf_bitmap =/= 0.U){
                buf_start_pc := buf_start_pc + 8.U
                inst_buf := Cat(0.U(64.W), next_inst_buf(127,64))
                buf_bitmap := Cat(0.U(1.W), next_buf_bitmap(1))
            }
            when(!inst_valid){
                excep_buf.en := false.B
                stall_pipe3()
            }.otherwise{
                recov3_r := false.B
            }
        }.elsewhen(hs_out){
            valid3_r := false.B
        }
    }.otherwise{
        valid3_r := false.B
    }

    io.if2id.inst       := inst_r
    io.if2id.pc         := pc3_r
    io.if2id.excep      := excep3_r
    io.if2id.valid      := valid3_r
    io.if2id.recov      := recov3_r
}