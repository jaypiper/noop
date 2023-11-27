package noop.execute

import chisel3._
import chisel3.util._
import noop.alu._
import noop.datapath._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.param.noop_tools._
import noop.utils.PipelineNext

class Execute extends Module{
    val io = IO(new Bundle{
        val df2ex       = Flipped(DecoupledIO(new DF2EX))
        val flushIn     = Input(Bool())
        val flushOut    = Output(Bool())
        val ex2wb       = ValidIO(new MEM2RB)
        val d_ex0       = Output(new RegForward)
        val d_ex1       = Output(new RegForward)
        val ex2if       = Output(new ForceJmp)
        val updateBPU = Output(new UpdateIO2)
    })

    val alu     = Module(new ALU)

    val alu64 = io.df2ex.bits.ctrl.aluWidth === IS_ALU64
    val aluop  = io.df2ex.bits.ctrl.aluOp

    val val1 = io.df2ex.bits.rs1_d
    val val2 = io.df2ex.bits.rs2_d

    alu.io.alu_op   := aluop
    alu.io.val1     := val1
    alu.io.val2     := val2
    alu.io.alu64    := alu64
    alu.io.en       := io.df2ex.valid
    val alu_out = Mux(alu64, alu.io.out(63, 0), sext32to64(alu.io.out))
    val wdata   = PriorityMux(Seq(
        (io.df2ex.bits.ctrl.dcMode(DC_S_BIT),    io.df2ex.bits.dst_d),
        (io.df2ex.bits.ctrl.writeCSREn,          io.df2ex.bits.rs2_d),
        (true.B,                            alu_out)
    ))

    io.df2ex.ready := !io.df2ex.valid || alu.io.valid

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
    val ex2wb = Wire(ValidIO(new MEM2RB))
    ex2wb.valid := io.df2ex.valid && alu.io.valid
    ex2wb.bits.inst := io.df2ex.bits.inst
    ex2wb.bits.pc := io.df2ex.bits.pc
    ex2wb.bits.excep := io.df2ex.bits.excep
    ex2wb.bits.excep.pc := Mux(io.df2ex.bits.excep.cause(63), io.df2ex.bits.nextPC, io.df2ex.bits.excep.pc)
    ex2wb.bits.ctrl := io.df2ex.bits.ctrl
    ex2wb.bits.csr_id := io.df2ex.bits.rs2
    ex2wb.bits.csr_d := alu_out
    ex2wb.bits.csr_en := io.df2ex.bits.ctrl.writeCSREn
    ex2wb.bits.dst := io.df2ex.bits.dst
    ex2wb.bits.dst_d := wdata
    ex2wb.bits.dst_en := io.df2ex.bits.ctrl.writeRegEn
    ex2wb.bits.rcsr_id := io.df2ex.bits.rcsr_id
    ex2wb.bits.is_mmio := false.B
    ex2wb.bits.recov := io.df2ex.bits.recov

    // data forwarding
    io.d_ex0.id := ex2wb.bits.dst
    io.d_ex0.data := ex2wb.bits.dst_d
    io.d_ex0.state := Mux(io.df2ex.valid && io.df2ex.bits.ctrl.writeRegEn,
        Mux(alu.io.valid, d_valid, d_wait),
        d_invalid
    )

    // flush
    val is_jmp = ex2wb.valid && !io.df2ex.bits.excep.en && io.df2ex.bits.jmp_type =/= NO_JMP
    val jmp_mispred = real_target =/= io.df2ex.bits.nextPC
    val jmp_target_r = RegEnable(real_target, is_jmp)
    io.updateBPU.valid := RegNext(is_jmp)
    io.updateBPU.mispred := RegEnable(jmp_mispred, is_jmp)
    io.updateBPU.pc := RegEnable(ex2wb.bits.pc, is_jmp)
    io.updateBPU.target := jmp_target_r

    io.flushOut := is_jmp && jmp_mispred
    io.ex2if.valid  := RegNext(io.flushOut)
    io.ex2if.seq_pc := jmp_target_r

    // out
    io.ex2wb := PipelineNext(ex2wb, io.flushIn)

    // data forwarding
    io.d_ex1.id := io.ex2wb.bits.dst
    io.d_ex1.data := io.ex2wb.bits.dst_d
    io.d_ex1.state := Mux(io.ex2wb.valid && io.ex2wb.bits.ctrl.writeRegEn, d_valid, d_invalid)
}