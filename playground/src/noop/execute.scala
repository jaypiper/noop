package noop.execute

import chisel3._
import chisel3.util._
import noop.alu._
import noop.datapath._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.param.noop_tools._
import noop.utils.PipelineConnect

class Execute extends Module{
    val io = IO(new Bundle{
        val df2ex       = Flipped(DecoupledIO(new DF2EX))
        val flushIn     = Input(Bool())
        val blockIn     = Input(Bool())
        val flushOut    = Output(Bool())
        val ex2wb       = ValidIO(new MEM2RB)
        val d_ex0       = Output(new RegForward)
        val d_ex1       = Output(new RegForward)
        val ex2if       = Output(new ForceJmp)
        val updateBPU = Output(new UpdateIO2)
    })

    val s0_out = Wire(DecoupledIO(new MEM2RB))

    val alu = Module(new ALU)
    val mul = Module(new MUL)

    val alu64 = io.df2ex.bits.ctrl.aluWidth === IS_ALU64
    val aluop  = io.df2ex.bits.ctrl.aluOp

    val val1 = io.df2ex.bits.rs1_d
    val val2 = io.df2ex.bits.rs2_d

    alu.io.alu_op   := aluop
    alu.io.val1     := val1
    alu.io.val2     := val2
    alu.io.alu64    := alu64
    val alu_out = Mux(alu64, alu.io.out(63, 0), sext32to64(alu.io.out))

    val is_mul = aluop === alu_MUL
    mul.io.a := val1
    mul.io.b := val2
    mul.io.en := io.df2ex.valid && is_mul && s0_out.ready

    val wdata = PriorityMux(Seq(
        (io.df2ex.bits.ctrl.dcMode(DC_S_BIT), io.df2ex.bits.dst_d),
        (io.df2ex.bits.ctrl.writeCSREn, io.df2ex.bits.rs2_d),
        (true.B, alu_out)
    ))

    io.df2ex.ready := !io.df2ex.valid || s0_out.ready

    // branch & jmp
    val branchAlu = Module(new BranchALU)
    branchAlu.io.val1   := val1
    branchAlu.io.val2   := val2
    branchAlu.io.brType := io.df2ex.bits.ctrl.brType
    val real_is_target = MuxLookup(io.df2ex.bits.jmp_type, false.B, Seq(
        JMP_PC  -> true.B,
        JMP_REG  -> true.B,
        JMP_COND    -> branchAlu.io.is_jmp
    ))
    val real_target = PriorityMux(Seq(
        (io.df2ex.bits.jmp_type === JMP_CSR,     io.df2ex.bits.rs2_d),
        (!real_is_target,                   io.df2ex.bits.pc + Mux(io.df2ex.bits.inst(1,0) === 3.U, 4.U, 2.U)),
        (io.df2ex.bits.jmp_type === JMP_REG,  io.df2ex.bits.rs1_d + io.df2ex.bits.dst_d),
        (true.B,                            io.df2ex.bits.pc + io.df2ex.bits.dst_d)
    ))

    // pipeline
    s0_out.valid := io.df2ex.valid
    s0_out.bits.inst := io.df2ex.bits.inst
    s0_out.bits.pc := io.df2ex.bits.pc
    s0_out.bits.excep := io.df2ex.bits.excep
    s0_out.bits.excep.pc := Mux(io.df2ex.bits.excep.cause(63), io.df2ex.bits.nextPC, io.df2ex.bits.excep.pc)
    s0_out.bits.ctrl := io.df2ex.bits.ctrl
    s0_out.bits.csr_id := io.df2ex.bits.rs2
    s0_out.bits.csr_d := alu_out
    s0_out.bits.csr_en := io.df2ex.bits.ctrl.writeCSREn
    s0_out.bits.dst := io.df2ex.bits.dst
    s0_out.bits.dst_d := wdata
    s0_out.bits.dst_en := io.df2ex.bits.ctrl.writeRegEn
    s0_out.bits.rcsr_id := io.df2ex.bits.rcsr_id
    s0_out.bits.is_mmio := false.B
    s0_out.bits.recov := io.df2ex.bits.recov

    // data forwarding
    io.d_ex0.id := s0_out.bits.dst
    io.d_ex0.data := s0_out.bits.dst_d
    io.d_ex0.state := Mux(io.df2ex.valid && io.df2ex.bits.ctrl.writeRegEn,
        Mux(is_mul, d_wait, d_valid),
        d_invalid
    )

    // flush
    val is_jmp = s0_out.valid && !io.df2ex.bits.excep.en && io.df2ex.bits.jmp_type =/= NO_JMP
    val jmp_mispred = real_target =/= io.df2ex.bits.nextPC
    val jmp_target_r = RegEnable(real_target, is_jmp)
    io.updateBPU.valid := RegNext(is_jmp)
    io.updateBPU.mispred := RegEnable(jmp_mispred, is_jmp)
    io.updateBPU.pc := RegEnable(s0_out.bits.pc, is_jmp)
    io.updateBPU.target := jmp_target_r

    io.flushOut := is_jmp && jmp_mispred
    io.ex2if.valid  := io.flushOut
    io.ex2if.seq_pc := real_target

    // out
    val s1_in = Wire(DecoupledIO(new MEM2RB))
    val s1_bits = PipelineConnect(s0_out, s1_in, s1_in.ready, io.flushIn).get
    val s1_is_mul = RegEnable(is_mul, s0_out.fire)
    val s1_mul_data_valid = RegInit(false.B)
    val s1_mul_data = sext32to64(mul.io.out.bits)
    when (s1_in.valid && s1_is_mul && mul.io.out.valid && io.blockIn) {
        s1_mul_data_valid := true.B
        s1_bits.dst_d := s1_mul_data
    }.elsewhen(s1_in.ready) {
        s1_mul_data_valid := false.B
    }

    when (s1_is_mul && mul.io.out.valid && io.blockIn) {
        s1_bits.dst_d := s1_mul_data
    }

    val data_valid = !s1_is_mul || mul.io.out.valid || s1_mul_data_valid
    s1_in.ready := !s1_in.valid || data_valid && !io.blockIn
    io.ex2wb.valid := s1_in.valid && data_valid && !io.blockIn
    io.ex2wb.bits := s1_in.bits
    when (s1_is_mul && mul.io.out.valid) {
        io.ex2wb.bits.dst_d := s1_mul_data
    }

    // data forwarding
    io.d_ex1.id := io.ex2wb.bits.dst
    io.d_ex1.data := io.ex2wb.bits.dst_d
    io.d_ex1.state := Mux(s1_in.valid && s1_in.bits.ctrl.writeRegEn,
        Mux(data_valid, d_valid, d_wait),
        d_invalid
    )
}