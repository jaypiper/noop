package noop.datapath

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._

class RegRead extends Bundle{
    val id      = Input(UInt(REG_WIDTH.W))
    val data   = Output(UInt(DATA_WIDTH.W))
}

class RegWrite extends Bundle{
    val id      = UInt(REG_WIDTH.W)
    val data    = UInt(DATA_WIDTH.W)
    val en      = Bool()
}

class CSRRead extends Bundle{
    val id      = Input(UInt(CSR_WIDTH.W))
    val data    = Output(UInt(DATA_WIDTH.W))
    val is_err  = Output(Bool())
}

class CSRWrite extends Bundle{
    val id      = Input(UInt(CSR_WIDTH.W))
    val data    = Input(UInt(DATA_WIDTH.W))
    val en      = Input(Bool())
}

class MmuState extends Bundle{
    val priv    = UInt(2.W)
    val mstatus = UInt(DATA_WIDTH.W)
    val satp    = UInt(DATA_WIDTH.W)
}

class IdState extends Bundle{
    val priv    = UInt(2.W)
}

class Exception_BASIC extends Bundle{
    val cause   = UInt(DATA_WIDTH.W)
    val tval    = UInt(DATA_WIDTH.W)
    val en      = Bool()
}

class Exception extends Exception_BASIC{
    val pc      = UInt(PADDR_WIDTH.W)
    val etype   = UInt(2.W)
}

class DataRead extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val rdata   = Output(UInt(DATA_WIDTH.W))
    val rvalid = Output(Bool())
}

class DataWrite extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val wdata   = Input(UInt(DATA_WIDTH.W))
}

class DataRW extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val rdata   = Output(UInt(DATA_WIDTH.W))
    val rvalid  = Output(Bool())
    val wdata   = Input(UInt(DATA_WIDTH.W))
}

class DataRWD extends DataRW{
    val wvalid = Input(Bool())
}

class PlicRW extends DataRWD{
    val arvalid = Input(Bool())
}

class DcacheRW extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val rdata   = Output(UInt(DATA_WIDTH.W))
    val rvalid  = Output(Bool())
    val wdata   = Input(UInt(DATA_WIDTH.W))
    val wen     = Input(Bool())
    val avalid  = Input(Bool())
    // val dc_mode = Input(UInt(DC_MODE_WIDTH.W))
    val wmask   = Input(UInt(DATA_WIDTH.W))
    val size    = Input(UInt(3.W))
    // val amo     = Input(UInt(AMO_WIDTH.W))
    val ready   = Output(Bool())
}

class IcacheRead extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val inst    = Output(UInt(DATA_WIDTH.W))
    val arvalid = Input(Bool())
    val ready   = Output(Bool())
    val rvalid  = Output(Bool())
    val rready  = Input(Bool())
}

class RaiseIntr extends Bundle{
    val en      = Output(Bool())
    val cause   = Output(UInt(DATA_WIDTH.W))
}

class Intr extends Bundle{
    val raise   = Bool()
    val clear   = Bool()
}

class BranchInfo extends Bundle{
    val seq_pc = Output(UInt(PADDR_WIDTH.W))
    val is_jmp = Output(Bool())
}

class ForceJmp extends Bundle{
    val seq_pc = UInt(PADDR_WIDTH.W)
    val valid  = Bool()
}

class IF2ID extends Bundle{
    val inst    = UInt(INST_WIDTH.W)
    val pc      = UInt(PADDR_WIDTH.W)
    val nextPC  = UInt(PADDR_WIDTH.W)
    val recov   = Bool()
}

class PipelineBackCtrl extends Bundle{
    val flush    = Bool()
    val stall   = Bool()
}

class Ctrl extends Bundle{
    val aluOp       = UInt(ALUOP_WIDTH.W)
    val aluWidth    = UInt(1.W)
    val dcMode      = UInt(DC_MODE_WIDTH.W)
    val writeRegEn  = Bool()
    val writeCSREn  = Bool()
    val brType      = UInt(3.W)
}


class ID2DF extends Bundle{
    val inst    = UInt(INST_WIDTH.W)
    val pc      = UInt(PADDR_WIDTH.W)
    val nextPC  = UInt(PADDR_WIDTH.W)
    val excep   = new Exception
    val ctrl    = new Ctrl
    val rs1     = UInt(REG_WIDTH.W)
    val rrs1    = Bool()
    val rs1_d   = UInt(DATA_WIDTH.W)
    val rs2     = UInt(CSR_WIDTH.W)
    val rrs2    = Bool()
    val rs2_d   = UInt(DATA_WIDTH.W)
    val dst     = UInt(REG_WIDTH.W) // if write-csr, the index is in rs2
    val dst_d   = UInt(DATA_WIDTH.W)
    val jmp_type = UInt(JMP_WIDTH.W)
    val recov   = Bool()
}

class RegForward extends Bundle{
    val id      = UInt(REG_WIDTH.W)
    val data    = UInt(DATA_WIDTH.W)
    val state   = UInt(2.W)
}

class DF2RR extends ID2DF{
    
}

class DF2EX extends Bundle{
    val inst    = UInt(INST_WIDTH.W)
    val pc      = UInt(PADDR_WIDTH.W)
    val nextPC  = UInt(PADDR_WIDTH.W)
    val excep   = new Exception
    val ctrl    = new Ctrl
    val rs1     = UInt(REG_WIDTH.W)
    val rs1_d   = UInt(DATA_WIDTH.W)
    val rs2     = UInt(CSR_WIDTH.W)
    val rs2_d   = UInt(DATA_WIDTH.W)
    val dst     = UInt(REG_WIDTH.W)
    val dst_d   = UInt(DATA_WIDTH.W)
    val rcsr_id = UInt(CSR_WIDTH.W)
    val jmp_type = UInt(JMP_WIDTH.W)
    val recov   = Bool()
}

class EX2DF extends Bundle {
    val drop = Bool()
}

class EX2MEM extends Bundle{
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(PADDR_WIDTH.W))
    val excep   = Output(new Exception)
    val ctrl    = Output(new Ctrl)
    val mem_addr = Output(UInt(PADDR_WIDTH.W))
    val mem_data = Output(UInt(DATA_WIDTH.W))
    val csr_id  = Output(UInt(CSR_WIDTH.W))
    val csr_d   = Output(UInt(DATA_WIDTH.W))
    val dst     = Output(UInt(REG_WIDTH.W))
    val dst_d   = Output(UInt(DATA_WIDTH.W))
    val rcsr_id = Output(UInt(CSR_WIDTH.W))
    val drop    = Input(Bool())
    val stall   = Input(Bool())
    val recov   = Output(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
}

class DF2MEM extends Bundle{
    val inst    = UInt(INST_WIDTH.W)
    val pc      = UInt(PADDR_WIDTH.W)
    val excep   = new Exception
    val ctrl    = new Ctrl
    val mem_addr = UInt(PADDR_WIDTH.W)
    val mem_data = UInt(DATA_WIDTH.W)
    val csr_id  = UInt(CSR_WIDTH.W)
    val csr_d   = UInt(DATA_WIDTH.W)
    val dst     = UInt(REG_WIDTH.W)
    val dst_d   = UInt(DATA_WIDTH.W)
    val rcsr_id = UInt(CSR_WIDTH.W)
    val recov   = Bool()
}

class MEM2DF extends Bundle {
    val membusy = Bool()
}

class MEM2RB extends Bundle{
    val inst    = UInt(INST_WIDTH.W)
    val pc      = UInt(PADDR_WIDTH.W)
    val excep   = new Exception
    val ctrl    = new Ctrl
    val csr_id  = UInt(CSR_WIDTH.W)
    val csr_d   = UInt(DATA_WIDTH.W)
    val csr_en  = Bool()
    val dst     = UInt(REG_WIDTH.W)
    val dst_d   = UInt(DATA_WIDTH.W)
    val dst_en  = Bool()
    val rcsr_id = UInt(CSR_WIDTH.W)
    val is_mmio = Bool()
    val recov   = Bool()
}

class PredictIO extends Bundle {
    val inst = Input(UInt(INST_WIDTH.W))
    val pc = Input(UInt(PADDR_WIDTH.W))
    val valid = Input(Bool())
    val target = Output(UInt(PADDR_WIDTH.W))
    val jmp = Output(Bool())
}

class PredictIO2 extends Bundle {
    val pc = Input(UInt(PADDR_WIDTH.W))
    val target = Output(UInt(PADDR_WIDTH.W))
    val jmp = Output(Bool())
}

class UpdateIO2 extends Bundle {
    val pc = UInt(PADDR_WIDTH.W)
    val valid = Bool()
    val mispred = Bool()
    val target = UInt(PADDR_WIDTH.W)

    def needUpdate: Bool = valid && mispred
}

class UpdateTrace extends Bundle {
    val valid = Bool()
    val inst = UInt(INST_WIDTH.W)
    val pc = UInt(PADDR_WIDTH.W)
}
