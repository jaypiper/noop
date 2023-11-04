package noop.execute

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.noop_tools._
import noop.param.decode_config._
import noop.param.cache_config._
import noop.datapath._
import noop.bpu._
import noop.alu._

class Execute extends Module{
    val io = IO(new Bundle{
        val rr2ex       = Flipped(new DF2EX)
        val ex2wb       = new MEM2RB
        val d_ex0       = Output(new RegForward)
        val d_ex        = Output(new RegForward)
        val ex2if       = Output(new ForceJmp)
        val updateNextPc = Input(new ForceJmp)
    })
    val drop_r = RegInit(false.B)
    val stall_r = RegInit(false.B)
    drop_r := false.B;  stall_r := false.B
    val drop_in = drop_r
    io.rr2ex.drop   := drop_in
    io.rr2ex.stall  := io.ex2wb.stall || stall_r
    val alu     = Module(new ALU)
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(PADDR_WIDTH.W))
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val ctrl_r      = RegInit(0.U.asTypeOf(new Ctrl))
    val mem_addr_r  = RegInit(0.U(PADDR_WIDTH.W))
    val mem_data_r  = RegInit(0.U(DATA_WIDTH.W))
    val csr_id_r    = RegInit(0.U(CSR_WIDTH.W))
    val csr_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val dst_r       = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val rcsr_id_r   = RegInit(0.U(CSR_WIDTH.W))
    val alu64_r     = RegInit(false.B)
    val next_pc_r   = RegInit(0.U(PADDR_WIDTH.W))  // for intr; updated by branch
    val recov_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)    

    val hs_in   = io.rr2ex.ready && io.rr2ex.valid
    val hs_out  = io.ex2wb.ready && io.ex2wb.valid
    val alu64 = io.rr2ex.ctrl.aluWidth === IS_ALU64
    val aluop  = io.rr2ex.ctrl.aluOp

    val val1 = io.rr2ex.rs1_d
    val val2 = io.rr2ex.rs2_d

    alu.io.alu_op   := aluop
    alu.io.val1     := val1
    alu.io.val2     := val2
    alu.io.alu64    := alu64
    alu.io.en       := false.B
    val cur_alu64   = Mux(hs_in, alu64, alu64_r)
    val alu_out = Mux(cur_alu64, alu.io.out(63, 0), sext32to64(alu.io.out))
    val wdata   = PriorityMux(Seq(
        (io.rr2ex.ctrl.dcMode(DC_S_BIT),    io.rr2ex.dst_d),
        (io.rr2ex.ctrl.writeCSREn,          io.rr2ex.rs2_d),
        (true.B,                            alu_out)
    ))

    when(io.updateNextPc.valid){
        next_pc_r := io.updateNextPc.seq_pc
    }

    when(hs_in){
        inst_r      := io.rr2ex.inst
        pc_r        := io.rr2ex.pc
        excep_r     := io.rr2ex.excep
        ctrl_r      := io.rr2ex.ctrl
        mem_addr_r  := alu_out
        mem_data_r  := wdata
        csr_id_r    := io.rr2ex.rs2
        csr_d_r     := alu_out
        dst_r       := io.rr2ex.dst
        dst_d_r     := wdata
        rcsr_id_r   := io.rr2ex.rcsr_id
        alu64_r     := alu64
        recov_r     := io.rr2ex.recov
        when(io.rr2ex.excep.cause(63)){
            excep_r.pc := next_pc_r
        }

    }
    io.rr2ex.ready  := false.B
    val sIdle :: sWaitAlu :: Nil = Enum(2)
    val state = RegInit(sIdle)
    val drop_alu = RegInit(false.B)
    when(!drop_in){
        when((valid_r || state =/= sIdle) && !hs_out){
        }.elsewhen(io.rr2ex.valid){
            io.rr2ex.ready  := true.B
            alu.io.en       := true.B
        }
    }

    when(state === sIdle){
        when(hs_in && !alu.io.valid){
            valid_r := false.B
            state := sWaitAlu
        }.elsewhen(hs_in){
            valid_r := true.B
        }.elsewhen(hs_out){
            valid_r := false.B
        }
    }
    when(state === sWaitAlu){
        when(alu.io.valid){
            state       := sIdle
            dst_d_r     := alu_out
            valid_r     := !drop_alu
            drop_alu    := false.B
        }
    }
    // branch & jmp
    val branchAlu = Module(new BranchALU)
    val forceJmp = RegInit(0.U.asTypeOf(new ForceJmp))
    forceJmp.valid := false.B
    branchAlu.io.val1   := val1
    branchAlu.io.val2   := val2
    branchAlu.io.brType := io.rr2ex.ctrl.brType
    val real_is_target = MuxLookup(io.rr2ex.jmp_type, false.B, Seq(
        JMP_UNCOND  -> true.B,
        JMP_COND    -> branchAlu.io.is_jmp
    ))
    val real_target = PriorityMux(Seq(
        (io.rr2ex.jmp_type === JMP_CSR,     io.rr2ex.rs2_d),
        (!real_is_target,                   io.rr2ex.pc + Mux(io.rr2ex.inst(1,0) === 3.U, 4.U, 2.U)),
        (io.rr2ex.jmp_type === JMP_UNCOND,  io.rr2ex.rs1_d + io.rr2ex.dst_d),
        (true.B,                            io.rr2ex.dst_d)
    ))

    when(hs_in){
        next_pc_r       := real_target      // for intr
    }
    val branchMissCounter = RegInit(0.U(DATA_WIDTH.W))
    val branchCounter = RegInit(0.U(DATA_WIDTH.W))
    dontTouch(branchMissCounter)
    dontTouch(branchCounter)
    when(!drop_in){
        when(hs_in && !io.rr2ex.excep.en && io.rr2ex.jmp_type =/= NO_JMP && real_target =/= io.rr2ex.nextPC){
            forceJmp.seq_pc := real_target
            forceJmp.valid := true.B
            drop_r  := true.B
            branchMissCounter := branchMissCounter + 1.U
            // printf("failed pc=%x inst=%x next=%x real=%x\n", io.rr2ex.pc, io.rr2ex.inst, io.rr2ex.nextPC, real_target)
        }
        when(hs_in && !io.rr2ex.excep.en && io.rr2ex.jmp_type =/= NO_JMP) {
            branchCounter := branchCounter + 1.U
        }
    }
    io.ex2if.seq_pc := forceJmp.seq_pc
    io.ex2if.valid  := forceJmp.valid

    // data forwading

    io.d_ex.id     := dst_r
    io.d_ex.data   := dst_d_r
    io.d_ex.state  := d_invalid
    io.d_ex0.id := io.rr2ex.dst
    io.d_ex0.data :=  wdata
    io.d_ex0.state := d_invalid
    when(hs_in && alu.io.valid) {
        io.d_ex0.state := Mux(io.rr2ex.ctrl.dcMode(DC_L_BIT), d_wait, Mux(io.rr2ex.ctrl.writeRegEn, d_valid, d_invalid))
    }.elsewhen(hs_in) {
        io.d_ex0.state := d_wait
    }
    when(valid_r){
        io.d_ex.state   := Mux(ctrl_r.dcMode(DC_L_BIT), d_wait, Mux(ctrl_r.writeRegEn, d_valid, d_invalid))
    }.elsewhen(state =/= sIdle){
        io.d_ex.state   := d_wait
    }

    // out
    io.ex2wb.inst      := inst_r
    io.ex2wb.pc        := pc_r
    io.ex2wb.excep     := excep_r
    io.ex2wb.csr_id     := csr_id_r
    io.ex2wb.csr_d      := csr_d_r
    io.ex2wb.csr_en     := ctrl_r.writeCSREn
    io.ex2wb.dst        := dst_r
    io.ex2wb.dst_d      := dst_d_r
    io.ex2wb.dst_en     := ctrl_r.writeRegEn
    io.ex2wb.rcsr_id   := rcsr_id_r
    io.ex2wb.is_mmio   := false.B
    io.ex2wb.valid     := valid_r
    io.ex2wb.recov     := recov_r
}