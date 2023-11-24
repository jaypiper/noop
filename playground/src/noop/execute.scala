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
        val df2ex       = Flipped(DecoupledIO(new DF2EX))
        val ex2df       = Output(new EX2DF)
        val ex2wb       = DecoupledIO(new MEM2RB)
        val d_ex0       = Output(new RegForward)
        val ex2if       = Output(new ForceJmp)
        val updateBPU = Output(new UpdateIO2)
    })
    val drop_r = RegInit(false.B)
    drop_r := false.B
    io.ex2df.drop   := drop_r

    val alu     = Module(new ALU)

    val sIdle :: sWaitAlu :: Nil = Enum(2)
    val state = RegInit(sIdle)
    when(io.df2ex.fire && !alu.io.valid) {
        state := sWaitAlu
    }.elsewhen(state === sWaitAlu && alu.io.valid) {
        state := sIdle
    }

    val hs_in   = io.df2ex.ready && io.df2ex.valid
    val alu64 = io.df2ex.bits.ctrl.aluWidth === IS_ALU64
    val aluop  = io.df2ex.bits.ctrl.aluOp

    val val1 = io.df2ex.bits.rs1_d
    val val2 = io.df2ex.bits.rs2_d

    alu.io.alu_op   := aluop
    alu.io.val1     := val1
    alu.io.val2     := val2
    alu.io.alu64    := alu64
    alu.io.en       := io.df2ex.valid && state === sIdle
    val alu_out = Mux(alu64, alu.io.out(63, 0), sext32to64(alu.io.out))
    val wdata   = PriorityMux(Seq(
        (io.df2ex.bits.ctrl.dcMode(DC_S_BIT),    io.df2ex.bits.dst_d),
        (io.df2ex.bits.ctrl.writeCSREn,          io.df2ex.bits.rs2_d),
        (true.B,                            alu_out)
    ))

    io.df2ex.ready := !io.df2ex.valid || alu.io.valid
    io.ex2df.exBusy := state === sWaitAlu

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

    when(!drop_r){
        when(hs_in && !io.df2ex.bits.excep.en && io.df2ex.bits.jmp_type =/= NO_JMP && real_target =/= io.df2ex.bits.nextPC){
            drop_r  := true.B
        }
    }

    val is_jmp = io.ex2wb.valid && !io.df2ex.bits.excep.en && io.df2ex.bits.jmp_type =/= NO_JMP
    val jmp_target_r = RegEnable(real_target, is_jmp)
    io.updateBPU.valid := RegNext(is_jmp)
    io.updateBPU.pc := RegEnable(io.ex2wb.bits.pc, io.ex2wb.valid)
    io.updateBPU.target := jmp_target_r

    val force_jump = is_jmp && real_target =/= io.df2ex.bits.nextPC
    io.ex2if.valid  := RegNext(force_jump)
    io.ex2if.seq_pc := jmp_target_r

    // data forwarding
    io.d_ex0.id := io.df2ex.bits.dst
    io.d_ex0.data := wdata
    io.d_ex0.state := Mux(io.df2ex.valid && io.df2ex.bits.ctrl.writeRegEn,
        Mux(alu.io.valid, d_valid, d_wait),
        d_invalid
    )

    // out
    io.ex2wb.valid := io.df2ex.valid && alu.io.valid
    io.ex2wb.bits.inst := io.df2ex.bits.inst
    io.ex2wb.bits.pc := io.df2ex.bits.pc
    io.ex2wb.bits.excep := io.df2ex.bits.excep
    io.ex2wb.bits.excep.pc := Mux(io.df2ex.bits.excep.cause(63), io.df2ex.bits.nextPC, io.df2ex.bits.excep.pc)
    io.ex2wb.bits.ctrl := io.df2ex.bits.ctrl
    io.ex2wb.bits.csr_id := io.df2ex.bits.rs2
    io.ex2wb.bits.csr_d := alu_out
    io.ex2wb.bits.csr_en := io.df2ex.bits.ctrl.writeCSREn
    io.ex2wb.bits.dst := io.df2ex.bits.dst
    io.ex2wb.bits.dst_d := Mux(state === sWaitAlu && alu.io.valid, alu_out, wdata)
    io.ex2wb.bits.dst_en := io.df2ex.bits.ctrl.writeRegEn
    io.ex2wb.bits.rcsr_id := io.df2ex.bits.rcsr_id
    io.ex2wb.bits.is_mmio := false.B
    io.ex2wb.bits.recov := io.df2ex.bits.recov
}