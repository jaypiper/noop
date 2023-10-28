package noop.memory

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.clint._
import noop.clint.clint_config._
import noop.datapath._
import noop.plic.plic_config._

class MemCrossBar extends Module{ // mtime & mtimecmp can be accessed here
    val io = IO(new Bundle{
        val dataRW  = new DcacheRW
        val mmio    = Flipped(new DcacheRW)
        val dcRW    = Flipped(new DcacheRW)
    })
    val pre_type    = RegInit(0.U(2.W))
    val data_r      = RegInit(0.U(DATA_WIDTH.W))
    val inp_mem     = io.dataRW.addr(PADDR_WIDTH-1)
    io.mmio.addr    := io.dataRW.addr
    io.mmio.wdata   := io.dataRW.wdata
    io.mmio.wmask   := io.dataRW.wmask
    io.mmio.wen   := io.dataRW.wen
    io.dcRW.addr    := io.dataRW.addr
    io.dcRW.wdata   := io.dataRW.wdata
    io.dcRW.wmask   := io.dataRW.wmask
    io.dcRW.wen   := io.dataRW.wen

    io.dcRW.avalid := false.B
    io.mmio.avalid := false.B

    io.dataRW.ready := false.B

    when(io.dataRW.avalid){
        when(inp_mem){
            pre_type        := 1.U
            io.dcRW.avalid := true.B
            io.dataRW.ready := io.dcRW.ready
        }.otherwise{
            pre_type        := 0.U
            io.mmio.avalid := true.B
            io.dataRW.ready := io.mmio.ready
        }
    }
    when(pre_type === 1.U){
        io.dataRW.rdata     := io.dcRW.rdata
        io.dataRW.rvalid    := io.dcRW.rvalid
    }.elsewhen(pre_type === 0.U){
        io.dataRW.rdata     := io.mmio.rdata
        io.dataRW.rvalid    := io.mmio.rvalid
    }.otherwise{
        io.dataRW.rdata     := 0.U
        io.dataRW.rvalid    := false.B
    }
}

class Memory extends Module{
    val io = IO(new Bundle{
        val ex2mem  = Flipped(new EX2MEM)
        val mem2rb  = new MEM2RB
        val dataRW    = Flipped(new DcacheRW)
        val d_mem1  = Output(new RegForward)
    })
    io.ex2mem.drop  := false.B
    io.ex2mem.stall := false.B
// stage 1
    val inst1_r     = RegInit(0.U(INST_WIDTH.W))
    val pc1_r       = RegInit(0.U(VADDR_WIDTH.W))
    val excep1_r    = RegInit(0.U.asTypeOf(new Exception))
    val ctrl1_r     = RegInit(0.U.asTypeOf(new Ctrl))
    val mem_addr1_r = RegInit(0.U(VADDR_WIDTH.W))
    val mem_data1_r = RegInit(0.U(DATA_WIDTH.W))
    val dst1_r      = RegInit(0.U(REG_WIDTH.W))
    val dst_d1_r    = RegInit(0.U(DATA_WIDTH.W))
    val dst_en1_r   = RegInit(false.B)
    val csr_id1_r   = RegInit(0.U(CSR_WIDTH.W))
    val csr_d1_r    = RegInit(0.U(DATA_WIDTH.W))
    val csr_en1_r   = RegInit(false.B)
    val rcsr_id1_r  = RegInit(0.U(CSR_WIDTH.W))
    val special1_r  = RegInit(0.U(2.W))
    val indi1_r     = RegInit(0.U(INDI_WIDTH.W))
    val recov1_r    = RegInit(false.B)
    val valid1_r    = RegInit(false.B)

    val hs_in   = io.ex2mem.ready && io.ex2mem.valid
    val hs1     = Wire(Bool())
    val hs_out  = io.mem2rb.ready && io.mem2rb.valid
    hs1 := false.B
    when(hs_in){
        inst1_r     := io.ex2mem.inst
        pc1_r       := io.ex2mem.pc
        excep1_r    := io.ex2mem.excep
        ctrl1_r     := io.ex2mem.ctrl
        mem_addr1_r := io.ex2mem.mem_addr
        mem_data1_r := io.ex2mem.mem_data
        dst1_r      := io.ex2mem.dst
        dst_d1_r    := io.ex2mem.dst_d
        dst_en1_r   := io.ex2mem.ctrl.writeRegEn
        csr_id1_r   := io.ex2mem.csr_id
        csr_d1_r    := io.ex2mem.csr_d
        csr_en1_r   := io.ex2mem.ctrl.writeCSREn
        rcsr_id1_r  := io.ex2mem.rcsr_id
        indi1_r     := io.ex2mem.indi
        special1_r  := io.ex2mem.special
        recov1_r    := io.ex2mem.recov
        valid1_r    := true.B
    } .elsewhen(hs1) {
        valid1_r := false.B
    }

    val curMode = Mux(hs_in, io.ex2mem.ctrl.dcMode, ctrl1_r.dcMode)
    io.dataRW.avalid := curMode =/= mode_NOP
    io.dataRW.addr := Mux(hs_in, io.ex2mem.mem_addr, mem_addr1_r)
    io.dataRW.wdata := Mux(hs_in, io.ex2mem.mem_data, mem_data1_r)
    io.dataRW.wen   := curMode(DC_S_BIT)
    io.dataRW.wmask := MuxLookup(curMode(1,0), 0.U(DATA_WIDTH), Seq(
        0.U -> "hff".U(DATA_WIDTH.W),
        1.U -> "hffff".U(DATA_WIDTH.W),
        2.U -> "hffffffff".U(DATA_WIDTH.W),
        3.U -> "hffffffffffffffff".U(DATA_WIDTH.W)
    ))
    io.ex2mem.ready := false.B

    io.d_mem1.id := dst1_r
    io.d_mem1.data := Mux(ctrl1_r.dcMode(DC_L_BIT), io.dataRW.rdata, dst_d1_r)
    io.d_mem1.state := d_invalid
    when(valid1_r) {
        when(ctrl1_r.dcMode(DC_L_BIT)) {
            io.d_mem1.state := Mux(io.dataRW.rvalid, d_valid, d_wait)
        }.elsewhen(ctrl1_r.dcMode === mode_NOP) {
            io.d_mem1.state := d_valid
        }
    }

    when(valid1_r && !hs_out){
    }.elsewhen(io.ex2mem.valid){
        io.ex2mem.ready := true.B
    }
    val stage1_is_excep = excep1_r.en
    
    hs1 := io.dataRW.avalid && io.dataRW.ready

    val inst_r     = RegInit(0.U(INST_WIDTH.W))
    val pc_r       = RegInit(0.U(VADDR_WIDTH.W))
    val excep_r    = RegInit(0.U.asTypeOf(new Exception))
    val dst_r      = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r    = RegInit(0.U(DATA_WIDTH.W))
    val dst_en_r   = RegInit(false.B)
    val csr_id_r   = RegInit(0.U(CSR_WIDTH.W))
    val csr_d_r    = RegInit(0.U(DATA_WIDTH.W))
    val csr_en_r   = RegInit(false.B)
    val rcsr_id_r  = RegInit(0.U(CSR_WIDTH.W))
    val special_r  = RegInit(0.U(2.W))
    val is_mmio_r   = RegInit(false.B)
    val recov_r    = RegInit(false.B)
    val valid_r    = RegInit(false.B)

    when(hs1){
        inst_r     := inst1_r
        pc_r       := pc1_r
        excep_r    := excep1_r
        dst_r      := dst1_r
        dst_d_r    := dst_d1_r
        dst_en_r   := dst_en1_r
        csr_id_r   := csr_id1_r
        csr_d_r    := csr_d1_r
        csr_en_r   := csr_en1_r
        rcsr_id_r  := rcsr_id1_r
        special_r  := special1_r
        is_mmio_r  := ctrl1_r.dcMode =/= mode_NOP && mem_addr1_r < "h80000000".U
    }

    val dc_valid3 = ctrl1_r.dcMode =/= mode_NOP || io.dataRW.rvalid


    io.mem2rb.inst      := inst_r
    io.mem2rb.pc        := pc_r
    io.mem2rb.excep     := excep_r
    io.mem2rb.csr_id    := csr_id_r
    io.mem2rb.csr_d     := csr_d_r
    io.mem2rb.csr_en    := csr_en_r
    io.mem2rb.dst       := dst_r
    io.mem2rb.dst_d     := dst_d_r
    io.mem2rb.dst_en    := dst_en_r
    io.mem2rb.rcsr_id   := rcsr_id_r
    io.mem2rb.special   := special_r
    io.mem2rb.is_mmio   := is_mmio_r
    io.mem2rb.recov     := recov_r
    io.mem2rb.valid     := valid_r

}