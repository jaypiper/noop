package noop.bpu

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.bpu.bpu_config._
import noop.datapath._
import noop.param.regs_config._
import noop.utils.ReplacementPolicy

class BPUSearch extends Bundle{
    val vaddr       = Input(UInt(PADDR_WIDTH.W))
    val va_valid    = Input(Bool())
    val target      = Output(UInt(PADDR_WIDTH.W))
    val is_target   = Output(Bool())
}

class BPUUpdate extends Bundle{
    val vaddr       = UInt(PADDR_WIDTH.W)
    val target      = UInt(PADDR_WIDTH.W)
    val is_target   = Bool()
    val valid       = Bool()
}

object bpu_config{
    val BTB_ENTRY_NUM = 16
    val INDEX_WIDTH = log2Ceil(BTB_ENTRY_NUM)
    val BTB_ADDR_WIDTH = 39
    val INST_ALIGN  = 2
    val BTB_ORIGIN_WIDTH = BTB_ADDR_WIDTH - INDEX_WIDTH - INST_ALIGN // -1 for compressed insts
    val BTB_TARGET_WIDTH = BTB_ADDR_WIDTH - INST_ALIGN
    val PRED_BIT = 2
}

class SimpleBPU extends Module {
    val io = IO(new Bundle {
        val predict = new PredictIO
        val updateTrace = Input(new UpdateTrace)
    })
    val imm = Wire(SInt(DATA_WIDTH.W))

    val callTrace = RegInit(VecInit(Seq.fill(16)(0.U(PADDR_WIDTH.W))))
    val idx = RegInit(0.U(5.W))

    io.predict.target := io.predict.pc + imm.asUInt
    io.predict.jmp := false.B
    imm := 0.S
    val inst = io.predict.inst
    when(io.predict.inst(6,2) === "b11000".U) { // branch
        imm := Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W)).asSInt
        io.predict.jmp := inst(31) && io.predict.valid
    }.elsewhen(inst(6,2) === "b11011".U) { // jal
        imm := Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W)).asSInt
        io.predict.jmp := io.predict.valid
    }.elsewhen(inst(6,2) === "b11001".U) {
        when(inst(19,15) === 1.U && io.predict.valid) {
            io.predict.jmp := true.B
            io.predict.target := callTrace(idx)
        }
    }.otherwise{ // branch instructions
        io.predict.jmp := false.B
        imm := 0.S
    }
    val rs1 = io.updateTrace.inst(19,15)
    val rd = io.updateTrace.inst(11,7)
    val rs1Link = rs1 === 1.U || rs1 === 5.U
    val rdLink  = rd === 1.U || rd === 5.U
    when(io.updateTrace.valid) {
        when(io.updateTrace.inst(6,2) === "b11011".U && rdLink) {
            idx := idx + 1.U
            callTrace(idx + 1.U) := io.updateTrace.pc + 4.U
        }.elsewhen(io.updateTrace.inst(6,2) === "b11001".U) {
            when(rs1Link && !rdLink) {
                idx := idx - 1.U
            }.elsewhen(!rs1Link && rdLink) {
                callTrace(idx + 1.U) := io.updateTrace.pc + 4.U
                idx := idx + 1.U
            }.elsewhen(rs1Link && rdLink && rs1 =/= rd) {
                callTrace(idx) := io.updateTrace.pc + 4.U
            }.elsewhen(rs1Link && rdLink && rs1 === rd) {
                callTrace(idx + 1.U) := io.updateTrace.pc + 4.U
                idx := idx + 1.U
            }
        }
    }
}

class SimpleBPU2 extends Module {
    val usePLRU = true

    val io = IO(new Bundle {
        val predict = Vec(ISSUE_WIDTH, new PredictIO2)
        val update = Input(new UpdateIO2)
    })
    val btb = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(VecInit(Seq.fill(2)(0.U(PADDR_WIDTH.W)))))) // no valid
    val updatePtr = RegInit(0.U(log2Ceil(BTB_ENTRY_NUM).W))
    val plru = ReplacementPolicy.fromString("plru", BTB_ENTRY_NUM)
    val btb_valid_idx = if (usePLRU) plru.way else updatePtr

    for (predict <- io.predict) {
        val btb_hit_vec = VecInit((0 until BTB_ENTRY_NUM).map(i => btb(i)(0) === predict.pc))
        val btb_hit = btb_hit_vec.asUInt.orR
        val btb_hit_idx = OHToUInt(btb_hit_vec)
        predict.jmp := btb_hit
        predict.target := btb(btb_hit_idx)(1)
        when (predict.v && btb_hit) {
            plru.access(btb_hit_idx)
        }
    }

    val btb_update_vec = VecInit((0 until BTB_ENTRY_NUM).map(i => btb(i)(0) === io.update.pc))
    val btb_update_hit = btb_update_vec.asUInt.orR
    val btb_update_idx = OHToUInt(btb_update_vec)
    when (io.update.needUpdate) {
        when(btb_update_hit) {
            plru.access(btb_update_idx)
            btb(btb_update_idx)(0) := io.update.pc
            btb(btb_update_idx)(1) := io.update.target
        }.otherwise {
            updatePtr := updatePtr + 1.U
            plru.access(btb_valid_idx)
            btb(btb_valid_idx)(0) := io.update.pc
            btb(btb_valid_idx)(1) := io.update.target
        }
    }
}