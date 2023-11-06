package noop.bpu

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.bpu.bpu_config._
import noop.datapath._
import noop.param.regs_config._

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

class BPU extends Module{
    val io = IO(new Bundle{
        val search = new BPUSearch
        val update = Input(new BPUUpdate)
    })
    val origin = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(0.U(BTB_ORIGIN_WIDTH.W))))
    val target = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(0.U(BTB_TARGET_WIDTH.W))))
    val predictor = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(1.U(PRED_BIT.W))))
    val valid  = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(false.B)))

    def update_predictor(pre_val: UInt, is_target: Bool) = {
        MuxLookup(pre_val, 1.U(PRED_BIT.W), Seq(
            0.U   -> Mux(is_target, 1.U, 0.U),
            1.U   -> Mux(is_target, 2.U, 0.U),
            2.U   -> Mux(is_target, 3.U, 1.U),
            3.U   -> Mux(is_target, 3.U, 2.U)
        ))
    }
    def new_pridictor(is_target: Bool) = {
        Mux(is_target, 2.U, 1.U)
    }

    when(io.update.valid){
        val update_idx = io.update.vaddr(INDEX_WIDTH+INST_ALIGN-1, INST_ALIGN)
        val update_origin = io.update.vaddr(BTB_ADDR_WIDTH-1, INDEX_WIDTH+INST_ALIGN)
        val update_target = io.update.target(BTB_ADDR_WIDTH-1, INST_ALIGN)
        origin(update_idx) := update_origin
        target(update_idx) := update_target
        when(update_origin =/= origin(update_idx)){
            predictor(update_idx) := new_pridictor(io.update.is_target)
        }.otherwise{
            predictor(update_idx) := update_predictor(predictor(update_idx), io.update.is_target)
        }
        valid(update_idx) := true.B
    }

    val search_is_target_r  = RegInit(false.B)
    val search_target_r     = RegInit(0.U(BTB_TARGET_WIDTH.W))
    io.search.is_target := search_is_target_r
    io.search.target := Cat(Fill(PADDR_WIDTH-PADDR_WIDTH ,search_target_r(BTB_TARGET_WIDTH-1)), search_target_r, 0.U(2.W))
    when(io.search.va_valid){
        val search_idx = io.search.vaddr(INDEX_WIDTH+INST_ALIGN-1, INST_ALIGN)
        val search_origin = io.search.vaddr(BTB_ADDR_WIDTH-1, INDEX_WIDTH+INST_ALIGN)
        when(search_origin === origin(search_idx) && valid(search_idx)){
            search_is_target_r  := predictor(search_idx)(1)
            search_target_r     := target(search_idx)
        }.otherwise{
            search_is_target_r := false.B
        }
    }.otherwise{
        search_is_target_r := false.B
    }
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
    val io = IO(new Bundle {
        val predict = new PredictIO2
        val update = Input(new UpdateIO2)
    })
    val btb = RegInit(VecInit(Seq.fill(BTB_ENTRY_NUM)(VecInit(Seq.fill(2)(0.U(PADDR_WIDTH.W)))))) // no valid
    val btb_valid_idx = RegInit(0.U(log2Ceil(BTB_ENTRY_NUM).W))
    val btb_hit_vec = VecInit((0 until BTB_ENTRY_NUM).map(i => btb(i)(0) === io.predict.pc))
    val btb_hit = btb_hit_vec.asUInt().orR
    val btb_hit_idx = OHToUInt(btb_hit_vec)
    val btb_update_vec = VecInit((0 until BTB_ENTRY_NUM).map(i => btb(i)(0) === io.update.pc))
    val btb_update_hit = btb_update_vec.asUInt().orR
    val btb_update_idx = OHToUInt(btb_update_vec)
    io.predict.jmp := io.predict.valid && btb_hit
    io.predict.target := btb(btb_hit_idx)(1)
    when (io.update.valid) {
        when(btb_update_hit) {
            btb(btb_update_idx)(0) := io.update.pc
            btb(btb_update_idx)(1) := io.update.target
        }.otherwise {
            btb_valid_idx := btb_valid_idx + 1.U
            btb(btb_valid_idx)(0) := io.update.pc
            btb(btb_valid_idx)(1) := io.update.target

        }
    }
}