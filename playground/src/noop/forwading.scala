package noop.decode

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class Forwarding extends Module{
    val io = IO(new Bundle{
        val id2df = Flipped(DecoupledIO(new ID2DF))
        val df2id = Output(new PipelineBackCtrl)
        val df2dp = DecoupledIO(new DF2EX)
        val dp2df = Input(new PipelineBackCtrl)
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
        drop_r := true.B;   stall_r := true.B
    }
    io.df2id.drop   := io.dp2df.drop || drop_r
    io.df2id.stall  := (stall_r && !io.dp2df.drop) || io.dp2df.stall

    val hs_in   = io.id2df.ready && io.id2df.valid
    val hs_out  = io.df2dp.ready && io.df2dp.valid

    val rs1_data    = Wire(UInt(DATA_WIDTH.W))
    val rs1_valid   = Wire(Bool())
    val rs2_data    = Wire(UInt(DATA_WIDTH.W))
    val rs2_valid   = Wire(Bool())
    rs1_valid := false.B;   rs1_data := 0.U
    rs2_valid := false.B;   rs2_data := 0.U

    // rs1 state
    when(io.id2df.bits.rs1 === 0.U){
        rs1_valid := true.B
    }.elsewhen((io.id2df.bits.rs1 === io.d_ex0.id) && (io.d_ex0.state =/= d_invalid)){
        when(io.d_ex0.state === d_valid){
            rs1_data := io.d_ex0.data
            rs1_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs1 === io.d_mem0.id) && (io.d_mem0.state =/= d_invalid)){
        when(io.d_mem0.state === d_valid){
            rs1_data := io.d_mem0.data
            rs1_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs1 === io.d_ex.id) && (io.d_ex.state =/= d_invalid)){
        when(io.d_ex.state === d_valid){
            rs1_data := io.d_ex.data
            rs1_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs1 === io.d_mem1.id) && (io.d_mem1.state =/= d_invalid)){
        when(io.d_mem1.state === d_valid){
            rs1_data := io.d_mem1.data
            rs1_valid := true.B
        }
    }.otherwise {
        rs1_data := io.rs1Read.data
        rs1_valid := true.B
    }
    // rs2 state
    when(io.id2df.bits.rs2 === 0.U){
        rs2_valid := true.B
    }.elsewhen((io.id2df.bits.rs2 === io.d_ex0.id) && (io.d_ex0.state =/= d_invalid)){
        when(io.d_ex0.state === d_valid){
            rs2_data := io.d_ex0.data
            rs2_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs2 === io.d_mem0.id) && (io.d_mem0.state =/= d_invalid)){
        when(io.d_mem0.state === d_valid){
            rs2_data := io.d_mem0.data
            rs2_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs2 === io.d_ex.id) && (io.d_ex.state =/= d_invalid)){
        when(io.d_ex.state === d_valid){
            rs2_data := io.d_ex.data
            rs2_valid := true.B
        }
    }.elsewhen((io.id2df.bits.rs2 === io.d_mem1.id) && (io.d_mem1.state =/= d_invalid)){
        when(io.d_mem1.state === d_valid){
            rs2_data := io.d_mem1.data
            rs2_valid := true.B
        }
    }.otherwise {
        rs2_data := io.rs2Read.data
        rs2_valid := true.B
    }

    when (io.df2dp.fire) {
        when (io.id2df.bits.ctrl.writeCSREn && io.csrRead.is_err) { // illegal instruction
            stall_pipe()
        }
    }

    val rs_ready = (rs1_valid || !io.id2df.bits.rrs1) && (rs2_valid || !io.id2df.bits.rrs2)
    io.id2df.ready := !io.id2df.valid || rs_ready && io.df2dp.ready

    io.rs1Read.id := io.id2df.bits.rs1
    io.rs2Read.id := io.id2df.bits.rs2(4,0)
    io.csrRead.id := io.id2df.bits.rs2//TODO

    io.df2dp.valid := io.id2df.valid && rs_ready
    io.df2dp.bits.inst := io.id2df.bits.inst
    io.df2dp.bits.pc := io.id2df.bits.pc
    io.df2dp.bits.nextPC := io.id2df.bits.nextPC
    io.df2dp.bits.excep := io.id2df.bits.excep
    io.df2dp.bits.ctrl := io.id2df.bits.ctrl
    io.df2dp.bits.rs1 := io.id2df.bits.rs1
    io.df2dp.bits.rs1_d := io.id2df.bits.rs1_d
    io.df2dp.bits.rs2 := io.id2df.bits.rs2
    io.df2dp.bits.rs2_d := io.id2df.bits.rs2_d
    io.df2dp.bits.dst := io.id2df.bits.dst
    io.df2dp.bits.dst_d := io.id2df.bits.dst_d
    io.df2dp.bits.rcsr_id := Mux(io.id2df.bits.ctrl.writeCSREn, io.id2df.bits.rs2, 0.U)
    io.df2dp.bits.jmp_type := io.id2df.bits.jmp_type
    io.df2dp.bits.recov := io.id2df.bits.recov
    when (io.id2df.bits.ctrl.writeCSREn && io.csrRead.is_err) { // illegal instruction
        io.df2dp.bits.excep.cause := CAUSE_ILLEGAL_INSTRUCTION.U
        io.df2dp.bits.excep.tval := io.id2df.bits.inst
        io.df2dp.bits.excep.en := true.B
        io.df2dp.bits.excep.pc := io.id2df.bits.pc
        io.df2dp.bits.excep.etype := 0.U
        io.df2dp.bits.ctrl := 0.U.asTypeOf(new Ctrl)
        io.df2dp.bits.jmp_type := 0.U
    }
    when (rs1_valid && io.id2df.bits.rrs1) {
        io.df2dp.bits.rs1_d := rs1_data
    }
    when (rs2_valid && io.id2df.bits.rrs2) {
        io.df2dp.bits.rs2_d := rs2_data
    }
    when (io.id2df.bits.ctrl.writeCSREn) {
        io.df2dp.bits.rs2_d := io.csrRead.data
    }
}
