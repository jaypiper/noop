package noop.decode

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._
import noop.param.cache_config._

class Forwarding extends Module{
    val io = IO(new Bundle{
        val id2df = Flipped(DecoupledIO(new ID2DF))
        val df2id = Output(new PipelineBackCtrl)
        val df2ex = DecoupledIO(new DF2EX)
        val ex2df = Input(new EX2DF)
        val df2mem = DecoupledIO(new DF2MEM)
        val mem2df = Input(new MEM2DF)
        val d_ex0    = Input(new RegForward)
        val d_ex    = Input(new RegForward)
        val d_mem0  = Input(new RegForward)
        val d_mem1  = Input(new RegForward)
        val rs1Read = Flipped(new RegRead)
        val rs2Read = Flipped(new RegRead)
        val csrRead = Flipped(new CSRRead)
    })
    val drop_r      = RegInit(false.B)
    drop_r          := false.B
    val stall_r     = RegInit(false.B)
    stall_r         := false.B
    def stall_pipe() = {
        drop_r := true.B;   stall_r := true.B;  recov_r := true.B
    }
    io.df2id.drop   := io.ex2df.drop || drop_r
    io.df2id.stall  := (stall_r && !io.ex2df.drop) || io.ex2df.stall
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(PADDR_WIDTH.W))
    val nextPC_r    = RegInit(0.U(PADDR_WIDTH.W))
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val ctrl_r      = RegInit(0.U.asTypeOf(new Ctrl))
    val rs1_r       = RegInit(0.U(REG_WIDTH.W))
    val rrs1_r      = RegInit(false.B)
    val rs1_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val rs2_r       = RegInit(0.U(CSR_WIDTH.W))
    val rrs2_r      = RegInit(false.B)
    val rs2_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val dst_r       = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val jmp_type_r  = RegInit(0.U(JMP_WIDTH.W))
    val rcsr_id_r   = RegInit(0.U(CSR_WIDTH.W))
    val recov_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)

    val sIdle :: sWait :: Nil = Enum(2)
    val state = RegInit(sIdle)
    val hs_in   = io.id2df.ready && io.id2df.valid
    val hs_out  = (io.df2ex.ready && io.df2ex.valid) || (io.df2mem.ready && io.df2mem.valid)

    val rs1_wait    = Wire(Bool())
    val rs1_data    = Wire(UInt(DATA_WIDTH.W))
    val rs1_valid   = Wire(Bool())
    val rs2_wait    = Wire(Bool())
    val rs2_data    = Wire(UInt(DATA_WIDTH.W))
    val rs2_valid   = Wire(Bool())
    rs1_valid := false.B;   rs1_wait    := false.B; rs1_data := 0.U
    rs2_valid := false.B;   rs2_wait    := false.B; rs2_data := 0.U
    val cur_rs1     = Mux(hs_in, io.id2df.bits.rs1, rs1_r)
    val cur_rrs1    = Mux(hs_in, io.id2df.bits.rrs1, rrs1_r)
    val cur_rs2     = Mux(hs_in, io.id2df.bits.rs2, rs2_r)
    val cur_rrs2    = Mux(hs_in, io.id2df.bits.rrs2, rrs2_r)
    when(cur_rrs1){
        // rs1 state
        when(cur_rs1 === 0.U){
            rs1_wait := false.B
            rs1_valid := true.B
        }.elsewhen((cur_rs1 === io.d_ex0.id) && (io.d_ex0.state =/= d_invalid)){
            when(io.d_ex0.state === d_valid){
                rs1_data := io.d_ex0.data
                rs1_valid := true.B
            }.otherwise{
                rs1_wait := true.B
            }
        }.elsewhen((cur_rs1 === io.d_mem0.id) && (io.d_mem0.state =/= d_invalid)){
            when(io.d_mem0.state === d_valid){
                rs1_data := io.d_mem0.data
                rs1_valid := true.B
            }.otherwise{
                rs1_wait := true.B
            }
        }.elsewhen((cur_rs1 === io.d_ex.id) && (io.d_ex.state =/= d_invalid)){
            when(io.d_ex.state === d_valid){
                rs1_data := io.d_ex.data
                rs1_valid := true.B
            }.otherwise{
                rs1_wait := true.B
            }
        }.elsewhen((cur_rs1 === io.d_mem1.id) && (io.d_mem1.state =/= d_invalid)){
            when(io.d_mem1.state === d_valid){
                rs1_data := io.d_mem1.data
                rs1_valid := true.B
            }.otherwise{
                rs1_wait := true.B
            }
        }.otherwise {
            rs1_data := io.rs1Read.data
            rs1_valid := true.B
        }
    }
    when(cur_rrs2){
        // rs2 state
        when(cur_rs2 === 0.U){
            rs2_wait := false.B
            rs2_valid := true.B
        }.elsewhen((cur_rs2 === io.d_ex0.id) && (io.d_ex0.state =/= d_invalid)){
            when(io.d_ex0.state === d_valid){
                rs2_data := io.d_ex0.data
                rs2_valid := true.B
            }.otherwise{
                rs2_wait := true.B
            }
        }.elsewhen((cur_rs2 === io.d_mem0.id) && (io.d_mem0.state =/= d_invalid)){
            when(io.d_mem0.state === d_valid){
                rs2_data := io.d_mem0.data
                rs2_valid := true.B
            }.otherwise{
                rs2_wait := true.B
            }
        }.elsewhen((cur_rs2 === io.d_ex.id) && (io.d_ex.state =/= d_invalid)){
            when(io.d_ex.state === d_valid){
                rs2_data := io.d_ex.data
                rs2_valid := true.B
            }.otherwise{
                rs2_wait := true.B
            }
        }.elsewhen((cur_rs2 === io.d_mem1.id) && (io.d_mem1.state =/= d_invalid)){
            when(io.d_mem1.state === d_valid){
                rs2_data := io.d_mem1.data
                rs2_valid := true.B
            }.otherwise{
                rs2_wait := true.B
            }
        }.otherwise {
            rs2_data := io.rs2Read.data
            rs2_valid := true.B
        }
    }

    when(hs_in){
        inst_r      := io.id2df.bits.inst
        pc_r        := io.id2df.bits.pc
        nextPC_r    := io.id2df.bits.nextPC
        excep_r     := io.id2df.bits.excep
        ctrl_r      := io.id2df.bits.ctrl
        rs1_r       := io.id2df.bits.rs1
        rrs1_r      := io.id2df.bits.rrs1
        rs1_d_r     := io.id2df.bits.rs1_d
        rs2_r       := io.id2df.bits.rs2
        rrs2_r      := io.id2df.bits.rrs2
        rs2_d_r     := io.id2df.bits.rs2_d
        dst_r       := io.id2df.bits.dst
        dst_d_r     := io.id2df.bits.dst_d
        jmp_type_r  := io.id2df.bits.jmp_type
        rcsr_id_r   := Mux(io.id2df.bits.ctrl.writeCSREn, io.id2df.bits.rs2, 0.U)
        recov_r     := io.id2df.bits.recov
        when(io.id2df.bits.ctrl.writeCSREn && io.csrRead.is_err){ // illegal instruction
            excep_r.cause   := CAUSE_ILLEGAL_INSTRUCTION.U
            excep_r.tval    := io.df2ex.bits.inst
            excep_r.en      := true.B
            excep_r.pc      := io.df2ex.bits.pc
            excep_r.etype   := 0.U
            stall_pipe()
            ctrl_r      := 0.U.asTypeOf(new Ctrl)
            jmp_type_r  := 0.U
        }
    }

    when(hs_in || (state =/= sIdle)){
        when(rs1_valid && cur_rrs1){
            rrs1_r  := false.B
            rs1_d_r := rs1_data
        }
        when(rs2_valid && cur_rrs2){
            rrs2_r  := false.B
            rs2_d_r := rs2_data
        }
    }
    when(hs_in && io.id2df.bits.ctrl.writeCSREn) {
        rs2_d_r := io.csrRead.data
    }

    io.id2df.ready := !io.id2df.valid || !(valid_r || state =/= sIdle) || hs_out

    // 1) flush; 2) rs1 and rs2 are ready
    when (io.ex2df.drop || drop_r || state === sWait && !rs1_wait && !rs2_wait) {
        state := sIdle
    // either rs1 or rs2 is not ready
    }.elsewhen(state === sIdle && hs_in && (rs1_wait || rs2_wait)) {
        state := sWait
    }

    val out_is_ready = !io.mem2df.membusy && ctrl_r.dcMode === mode_NOP && io.df2ex.ready ||
      !io.ex2df.exBusy && ctrl_r.dcMode =/= mode_NOP && io.df2mem.ready
    val in_is_ready = !(valid_r || state =/= sIdle) || out_is_ready
    // valid_r: we have a ready instruction for the next stage
    when(io.ex2df.drop || drop_r) {
        valid_r := false.B
    }.elsewhen((io.id2df.valid && in_is_ready || state === sWait) && !rs1_wait && !rs2_wait) {
        valid_r := true.B
    }.elsewhen(out_is_ready) {
        valid_r := false.B
    }

    io.rs1Read.id := io.id2df.bits.rs1
    io.rs2Read.id := io.id2df.bits.rs2(4,0)
    io.csrRead.id := io.id2df.bits.rs2//TODO

    val drop_in = io.ex2df.drop || io.mem2df.drop

    io.df2ex.bits.inst       := inst_r
    io.df2ex.bits.pc         := pc_r
    io.df2ex.bits.nextPC     := nextPC_r
    io.df2ex.bits.excep      := excep_r
    io.df2ex.bits.ctrl       := ctrl_r
    io.df2ex.bits.rs1        := rs1_r
    io.df2ex.bits.rs1_d      := rs1_d_r
    io.df2ex.bits.rs2        := rs2_r
    io.df2ex.bits.rs2_d      := rs2_d_r
    io.df2ex.bits.dst        := dst_r
    io.df2ex.bits.dst_d      := dst_d_r
    io.df2ex.bits.jmp_type   := jmp_type_r
    io.df2ex.bits.rcsr_id    := rcsr_id_r
    io.df2ex.bits.recov      := recov_r
    io.df2ex.valid           := valid_r && !drop_in && !io.mem2df.membusy && ctrl_r.dcMode === mode_NOP

    io.df2mem.bits.inst      := inst_r
    io.df2mem.bits.pc        := pc_r
    io.df2mem.bits.excep     := 0.U.asTypeOf(new Exception) // TODO: remove
    io.df2mem.bits.ctrl      := ctrl_r
    io.df2mem.bits.mem_addr  := rs1_d_r + dst_d_r
    io.df2mem.bits.mem_data  := rs2_d_r
    io.df2mem.bits.csr_id    := 0.U
    io.df2mem.bits.csr_d     := 0.U
    io.df2mem.bits.dst       := dst_r
    io.df2mem.bits.dst_d     := 0.U
    io.df2mem.bits.rcsr_id   := 0.U
    io.df2mem.bits.recov     := recov_r
    io.df2mem.valid          := valid_r && !drop_in && !io.ex2df.exBusy && ctrl_r.dcMode =/= mode_NOP
}