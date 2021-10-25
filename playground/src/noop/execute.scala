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
        val rr2ex   = Flipped(new RR2EX)
        val ex2mem  = new EX2MEM
        val d_ex    = Output(new RegForward)
        val bpuUpdate = Output(new BPUUpdate)
        val ex2if   = Output(new ForceJmp)
    })
    dontTouch(io)
    val drop_r = RegInit(false.B)
    drop_r := false.B
    val drop_in = drop_r || io.ex2mem.drop
    io.rr2ex.drop := drop_in
    val alu     = Module(new ALU)
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(VADDR_WIDTH.W))
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val ctrl_r      = RegInit(0.U.asTypeOf(new Ctrl))
    val mem_addr_r  = RegInit(0.U(VADDR_WIDTH.W))
    val mem_data_r  = RegInit(0.U(DATA_WIDTH.W))
    val csr_id_r    = RegInit(0.U(CSR_WIDTH.W))
    val csr_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val dst_r       = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val special_r   = RegInit(0.U(2.W))
    val alu64_r     = RegInit(false.B)

    val valid_r     = RegInit(false.B)    


    val hs_in   = io.rr2ex.ready && io.rr2ex.valid
    val hs_out  = io.ex2mem.ready && io.ex2mem.valid
    val alu64 = io.rr2ex.ctrl.aluWidth === IS_ALU64
    val aluop  = io.rr2ex.ctrl.aluOp
    // alu
    val signed_dr   = !alu64 && ((aluop === alu_DIV) || (aluop === alu_REM))
    val unsigned_dr = !alu64 && ((aluop === alu_DIVU) || (aluop === alu_REMU))
    val val1 = PriorityMux(Seq(
        (signed_dr,     zext32to64(io.rr2ex.rs1_d)),
        (unsigned_dr,   sext32to64(io.rr2ex.rs1_d)),
        (true.B,        io.rr2ex.rs1_d)
    ))
    val is_shift = aluop === alu_SLL || aluop === alu_SRL || aluop === alu_SRA
    val val2 = PriorityMux(Seq(
        (signed_dr,     zext32to64(io.rr2ex.rs2_d)),
        (unsigned_dr,   sext32to64(io.rr2ex.rs2_d)),
        (is_shift,      Mux(alu64, io.rr2ex.rs2_d(5,0), Cat(0.U(1.W), io.rr2ex.rs2_d(4,0)))),
        (true.B,        io.rr2ex.rs2_d)
    ))
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
        special_r   := io.rr2ex.special
        alu64_r     := alu64
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
    when(!io.ex2mem.drop){
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
    }.otherwise{
        valid_r := false.B
        when(state =/= sIdle){
            drop_alu := true.B
        }
    }
    // branch & jmp
    val bpu_r = RegInit(0.U.asTypeOf(new BPUUpdate))
    bpu_r.valid := false.B
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
        (!real_is_target,                   io.rr2ex.pc + 4.U),
        (io.rr2ex.jmp_type === JMP_UNCOND,  io.rr2ex.rs1_d + io.rr2ex.dst_d),
        (true.B,                            io.rr2ex.dst_d)
    ))

    when(hs_in){
        bpu_r.vaddr     := io.rr2ex.pc
        bpu_r.target    := real_target
        bpu_r.is_target := real_is_target
    }
    when(!drop_in){
        when(hs_in && io.rr2ex.jmp_type =/= NO_JMP){
            bpu_r.valid := true.B
        }
        when(hs_in && io.rr2ex.br_next_pc =/= real_target && io.rr2ex.jmp_type =/= NO_JMP){
            forceJmp.seq_pc := real_target
            forceJmp.valid := true.B
            drop_r  := true.B
        }
    }
    io.ex2if.seq_pc := forceJmp.seq_pc
    io.ex2if.valid  := forceJmp.valid & !io.ex2mem.drop
    io.bpuUpdate    := bpu_r

    // data forwading

    io.d_ex.id     := dst_r
    io.d_ex.data   := dst_d_r
    io.d_ex.state  := d_invalid
    when(valid_r){
        io.d_ex.state   := Mux(ctrl_r.dcMode(DC_L_BIT), d_wait, Mux(ctrl_r.writeRegEn, d_valid, d_invalid))
    }.elsewhen(state =/= sIdle){
        io.d_ex.state   := d_wait
    }

    // out
    io.ex2mem.inst      := inst_r
    io.ex2mem.pc        := pc_r
    io.ex2mem.excep     := excep_r
    io.ex2mem.ctrl      := ctrl_r
    io.ex2mem.mem_addr  := mem_addr_r
    io.ex2mem.mem_data  := mem_data_r
    io.ex2mem.csr_id    := csr_id_r
    io.ex2mem.csr_d     := csr_d_r
    io.ex2mem.dst       := dst_r
    io.ex2mem.dst_d     := dst_d_r
    io.ex2mem.special   := special_r
    io.ex2mem.valid     := valid_r
}