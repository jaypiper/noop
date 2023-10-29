package noop.decode

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._

class Forwarding extends Module{
    val io = IO(new Bundle{
        val id2df = Flipped(new ID2DF)
        val df2rr = new DF2RR
        val d_rr    = Input(new RegForward)
        val d_ex    = Input(new RegForward)
        val d_mem1  = Input(new RegForward)
        // val d_mem2  = Input(new RegForward)
        // val d_mem3  = Input(new RegForward)
    })
    val drop_r      = RegInit(false.B)
    val stall_r     = RegInit(false.B)
    drop_r := false.B
    io.id2df.drop   := drop_r || io.df2rr.drop
    io.id2df.stall  := (stall_r && !io.df2rr.drop) || io.df2rr.stall
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(VADDR_WIDTH.W))
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
    val jmp_type_r  = RegInit(0.U(2.W))
    val special_r   = RegInit(0.U(2.W))
    val swap_r      = RegInit(0.U(SWAP_WIDTH.W))
    val recov_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)

    val pre_dst     = RegInit(0.U(REG_WIDTH.W))
    val pre_wr      = RegInit(false.B)

    val sIdle :: sWait :: Nil = Enum(2)
    val state = RegInit(sIdle)
    val hs_in   = io.id2df.ready && io.id2df.valid
    val hs_out  = io.df2rr.ready && io.df2rr.valid

    val rs1_wait    = Wire(Bool())
    val rs1_data    = Wire(UInt(DATA_WIDTH.W))
    val rs1_valid   = Wire(Bool())
    val rs2_wait    = Wire(Bool())
    val rs2_data    = Wire(UInt(DATA_WIDTH.W))
    val rs2_valid   = Wire(Bool())
    rs1_valid := false.B;   rs1_wait    := false.B; rs1_data := 0.U
    rs2_valid := false.B;   rs2_wait    := false.B; rs2_data := 0.U
    val cur_rs1     = Mux(hs_in, io.id2df.rs1, rs1_r)
    val cur_rrs1    = Mux(hs_in, io.id2df.rrs1, rrs1_r)
    val cur_rs2     = Mux(hs_in, io.id2df.rs2, rs2_r)
    val cur_rrs2    = Mux(hs_in, io.id2df.rrs2, rrs2_r)
    when(cur_rrs1){
        // rs1 state
        when(cur_rs1 === 0.U){
            rs1_wait := false.B
        }.elsewhen(valid_r && pre_wr &&cur_rs1=== pre_dst){
            rs1_wait := true.B
        }.elsewhen((cur_rs1 === io.d_rr.id) && (io.d_rr.state =/= d_invalid)){
            when(io.d_rr.state === d_valid){
                rs1_data := io.d_rr.data
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
        }
    }
    when(cur_rrs2){
        // rs2 state
        when(cur_rs2 === 0.U){
            rs2_wait := false.B
        }.elsewhen(valid_r && pre_wr && cur_rs2=== pre_dst){
            rs2_wait := true.B
        }.elsewhen((cur_rs2 === io.d_rr.id) && (io.d_rr.state =/= d_invalid)){
            when(io.d_rr.state === d_valid){
                rs2_data := io.d_rr.data
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
        }
    }

    when(hs_in){
        inst_r      := io.id2df.inst
        pc_r        := io.id2df.pc
        excep_r     := io.id2df.excep
        ctrl_r      := io.id2df.ctrl
        rs1_r       := io.id2df.rs1
        rrs1_r      := io.id2df.rrs1
        rs1_d_r     := io.id2df.rs1_d
        rs2_r       := io.id2df.rs2
        rrs2_r      := io.id2df.rrs2
        rs2_d_r     := io.id2df.rs2_d
        dst_r       := io.id2df.dst
        dst_d_r     := io.id2df.dst_d
        jmp_type_r  := io.id2df.jmp_type
        special_r   := io.id2df.special
        swap_r      := io.id2df.swap
        recov_r     := io.id2df.recov
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

    when(hs_in){
        pre_dst := io.id2df.dst
        pre_wr  := io.id2df.ctrl.writeRegEn
    }.elsewhen(hs_out){
        pre_wr  := false.B
    }


    io.id2df.ready := false.B
    when(!io.df2rr.drop){
        when((valid_r || state =/= sIdle) && !hs_out){
        }.elsewhen(io.id2df.valid){
            io.id2df.ready := true.B
        }

        when(state === sIdle){
            when(hs_in && (rs1_wait || rs2_wait)){
                state := sWait
                valid_r := false.B
            }.elsewhen(hs_in){
                valid_r := true.B
            }.elsewhen(hs_out){
                valid_r := false.B
            }
        }
        when(state === sWait){
            when(!(rs1_wait) && !rs2_wait){
                state := sIdle
                valid_r := true.B
            }
        }
        
    }.otherwise{
        state       := sIdle
        valid_r     := false.B
    }
    io.df2rr.inst       := inst_r
    io.df2rr.pc         := pc_r
    io.df2rr.excep      := excep_r
    io.df2rr.ctrl       := ctrl_r
    io.df2rr.rs1        := rs1_r
    io.df2rr.rrs1       := rrs1_r
    io.df2rr.rs1_d      := rs1_d_r
    io.df2rr.rs2        := rs2_r
    io.df2rr.rrs2       := rrs2_r
    io.df2rr.rs2_d      := rs2_d_r
    io.df2rr.dst        := dst_r
    io.df2rr.dst_d      := dst_d_r
    io.df2rr.jmp_type   := jmp_type_r
    io.df2rr.special    := special_r
    io.df2rr.swap       := swap_r
    io.df2rr.recov      := recov_r
    io.df2rr.valid      := valid_r
}