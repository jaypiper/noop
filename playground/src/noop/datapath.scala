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
    val id      = Input(UInt(REG_WIDTH.W))
    val data    = Input(UInt(DATA_WIDTH.W))
    val en      = Input(Bool())
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

class Exception_BASIC extends Bundle{
    val cause   = UInt(DATA_WIDTH.W)
    val tval    = UInt(DATA_WIDTH.W)
    val en      = Bool()
}

class Exception extends Exception_BASIC{
    val pc      = UInt(VADDR_WIDTH.W)
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

class DcacheRW extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val rdata   = Output(UInt(DATA_WIDTH.W))
    val rvalid  = Output(Bool())
    val wdata   = Input(UInt(DATA_WIDTH.W))
    val dc_mode = Input(UInt(DC_MODE_WIDTH.W))
    val ready   = Output(Bool())
}

class IcacheRead extends Bundle{
    val addr    = Input(UInt(PADDR_WIDTH.W))
    val inst    = Output(UInt(INST_WIDTH.W))
    val arvalid = Input(Bool())
    val ready   = Output(Bool())
    val rvalid  = Output(Bool())
}

class RaiseIntr extends Bundle{
    val en      = Output(Bool())
    val cause   = Output(UInt(DATA_WIDTH.W))
}

class BranchInfo extends Bundle{
    val seq_pc = Output(UInt(VADDR_WIDTH.W))
    val is_jmp = Output(Bool())
}

class ForceJmp extends Bundle{
    val seq_pc = UInt(VADDR_WIDTH.W)
    val valid  = Bool()
}

class IF2ID extends Bundle{
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(DATA_WIDTH.W))
    val next_pc = Output(UInt(DATA_WIDTH.W))
    val excep   = Output(new Exception)
    val is_target = Output(Bool())
    val target  = Output(UInt(VADDR_WIDTH.W))
    val drop    = Input(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
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
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(DATA_WIDTH.W))
    val next_pc = Output(UInt(DATA_WIDTH.W))
    val excep   = Output(new Exception)
    val is_target = Output(Bool())
    val target  = Output(UInt(VADDR_WIDTH.W))
    val ctrl    = Output(new Ctrl)
    val rs1     = Output(UInt(REG_WIDTH.W))
    val rrs1    = Output(Bool())
    val rs1_d   = Output(UInt(DATA_WIDTH.W))
    val rs2     = Output(UInt(CSR_WIDTH.W))
    val rrs2    = Output(Bool())
    val rs2_d   = Output(UInt(DATA_WIDTH.W))
    val dst     = Output(UInt(REG_WIDTH.W)) // if write-csr, the index is in rs2
    val dst_d   = Output(UInt(DATA_WIDTH.W))
    val jmp_type = Output(UInt(2.W))
    val special = Output(UInt(2.W))
    val swap    = Output(UInt(2.W))
    val drop    = Input(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
}

class RegForward extends Bundle{
    val id      = UInt(REG_WIDTH.W)
    val data    = UInt(DATA_WIDTH.W)
    val state   = UInt(2.W)
}

class DF2RR extends ID2DF{
    
}

class RR2EX extends Bundle{
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(DATA_WIDTH.W))
    val next_pc = Output(UInt(DATA_WIDTH.W))
    val excep   = Output(new Exception)
    val is_target = Output(Bool())
    val target  = Output(UInt(VADDR_WIDTH.W))
    val ctrl    = Output(new Ctrl)
    val rs1     = Output(UInt(REG_WIDTH.W))
    val rs1_d   = Output(UInt(DATA_WIDTH.W))
    val rs2     = Output(UInt(CSR_WIDTH.W))
    val rs2_d   = Output(UInt(DATA_WIDTH.W))
    val dst     = Output(UInt(REG_WIDTH.W))
    val dst_d   = Output(UInt(DATA_WIDTH.W))
    val jmp_type = Output(UInt(2.W))
    val special = Output(UInt(2.W))
    val drop    = Input(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
}

class EX2MEM extends Bundle{
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(DATA_WIDTH.W))
    val next_pc = Output(UInt(DATA_WIDTH.W))
    val excep   = Output(new Exception)
    val ctrl    = Output(new Ctrl)
    val mem_addr = Output(UInt(VADDR_WIDTH.W))
    val mem_data = Output(UInt(DATA_WIDTH.W))
    val csr_id  = Output(UInt(CSR_WIDTH.W))
    val csr_d   = Output(UInt(DATA_WIDTH.W))
    val dst     = Output(UInt(REG_WIDTH.W))
    val dst_d   = Output(UInt(DATA_WIDTH.W))
    val special = Output(UInt(2.W))
    val drop    = Input(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
}

class MEM2RB extends Bundle{
    val inst    = Output(UInt(INST_WIDTH.W))
    val pc      = Output(UInt(DATA_WIDTH.W))
    val next_pc = Output(UInt(DATA_WIDTH.W))
    val excep   = Output(new Exception)
    val csr_id  = Output(UInt(CSR_WIDTH.W))
    val csr_d   = Output(UInt(DATA_WIDTH.W))
    val csr_en  = Output(Bool())
    val dst     = Output(UInt(REG_WIDTH.W))
    val dst_d   = Output(UInt(DATA_WIDTH.W))
    val dst_en  = Output(Bool())
    val special = Output(UInt(2.W))
    val drop    = Input(Bool())
    val valid   = Output(Bool())
    val ready   = Input(Bool())
}
