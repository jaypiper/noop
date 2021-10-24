package noop.bpu

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.bpu.bpu_config._

class BPUSearch extends Bundle{
    val vaddr       = Input(UInt(VADDR_WIDTH.W))
    val va_valid    = Input(Bool())
    val target      = Output(UInt(VADDR_WIDTH.W))
    val is_target   = Output(Bool())
}

class BPUUpdate extends Bundle{
    val vaddr       = UInt(VADDR_WIDTH.W)
    val target      = UInt(VADDR_WIDTH.W)
    val is_target   = Bool()
    val valid       = Bool()
}

object bpu_config{
    val BTB_ENTRY_NUM = 32
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
    io.search.target := Cat(Fill(VADDR_WIDTH-PADDR_WIDTH ,search_target_r(BTB_TARGET_WIDTH-1)), search_target_r, 0.U(2.W))
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