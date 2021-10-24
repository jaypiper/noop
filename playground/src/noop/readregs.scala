package noop.decode

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class ReadRegs extends Module{
    val io = IO(new Bundle{
        val df2rr = Flipped(new DF2RR)
        val rr2ex = new RR2EX
        val rs1Read = Flipped(new RegRead)
        val rs2Read = Flipped(new RegRead)
        val csrRead = Flipped(new CSRRead)
        val d_rr    = Output(new RegForward)
    })
    dontTouch(io)
    val drop_r      = RegInit(false.B)
    drop_r := false.B
    io.df2rr.drop := io.rr2ex.drop || drop_r
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(VADDR_WIDTH.W))
    val next_pc_r   = RegInit(0.U(VADDR_WIDTH.W))
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val is_target_r = RegInit(false.B)
    val target_r    = RegInit(0.U(VADDR_WIDTH.W))
    val ctrl_r      = RegInit(0.U.asTypeOf(new Ctrl))
    val rs1_r       = RegInit(0.U(REG_WIDTH.W))
    val rs1_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val rs2_r       = RegInit(0.U(CSR_WIDTH.W))
    val rs2_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val dst_r       = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val jmp_type_r  = RegInit(0.U(2.W))
    val special_r   = RegInit(0.U(2.W))

    val valid_r     = RegInit(false.B)

    val hs_in   = io.df2rr.ready && io.df2rr.valid
    val hs_out  = io.rr2ex.ready && io.rr2ex.valid

    io.rs1Read.id := io.df2rr.rs1
    io.rs2Read.id := io.df2rr.rs2(4,0)
    io.csrRead.id := io.df2rr.rs2
    val rs1_bef = Mux(io.df2rr.rrs1, io.rs1Read.data, io.df2rr.rs1_d)
    val rs2_bef = Mux(io.df2rr.ctrl.writeCSREn, io.csrRead.data, Mux(io.df2rr.rrs2, io.rs2Read.data, io.df2rr.rs2_d))
    val dst_bef = io.df2rr.dst_d
    when(hs_in){
        inst_r      := io.df2rr.inst
        pc_r        := io.df2rr.pc
        next_pc_r   := io.df2rr.next_pc
        excep_r     := io.df2rr.excep
        is_target_r := io.df2rr.is_target
        target_r    := io.df2rr.target
        ctrl_r      := io.df2rr.ctrl
        rs1_r       := io.df2rr.rs1
        rs1_d_r     := rs1_bef
        rs2_r       := io.df2rr.rs2
        rs2_d_r     := Mux(io.df2rr.swap === SWAP_2d, dst_bef, rs2_bef)
        dst_r       := io.df2rr.dst
        dst_d_r     := Mux(io.df2rr.swap === SWAP_2d, rs2_bef, dst_bef)
        jmp_type_r  := io.df2rr.jmp_type
        special_r   := io.df2rr.special
    }
    io.df2rr.ready := false.B
    when(!io.df2rr.drop){
        when(valid_r && !hs_out){
        }.elsewhen(io.df2rr.valid){
            io.df2rr.ready := true.B
        }
        when(hs_in){
            valid_r := true.B
        }.elsewhen(hs_out){
            valid_r := false.B
        }
    }.otherwise{
        valid_r := false.B
    }
    io.rr2ex.inst       := inst_r
    io.rr2ex.pc         := pc_r
    io.rr2ex.next_pc    := next_pc_r
    io.rr2ex.excep      := excep_r
    io.rr2ex.is_target  := is_target_r
    io.rr2ex.target     := target_r
    io.rr2ex.ctrl       := ctrl_r
    io.rr2ex.rs1        := rs1_r
    io.rr2ex.rs1_d      := rs1_d_r
    io.rr2ex.rs2        := rs2_r
    io.rr2ex.rs2_d      := rs2_d_r
    io.rr2ex.dst        := dst_r
    io.rr2ex.dst_d      := dst_d_r
    io.rr2ex.jmp_type   := jmp_type_r
    io.rr2ex.special    := special_r
    io.rr2ex.valid      := valid_r

    io.d_rr.id      := dst_r
    io.d_rr.data    := dst_d_r
    io.d_rr.state   := Mux(valid_r, d_wait, d_invalid)
}