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
import noop.tlb._

class MemCrossBar extends Module{ // mtime & mtimecmp can be accessed here
    val io = IO(new Bundle{
        val dataRW  = new DcacheRW
        val mmio    = Flipped(new DcacheRW)
        val dcRW    = Flipped(new DcacheRW)
        val clintIO = Flipped(new DataRWD)
    })
    val pre_type    = RegInit(0.U(2.W))
    val time_r      = RegInit(0.U(DATA_WIDTH.W))
    val time_valid  = RegInit(false.B)
    val is_clint    = io.dataRW.addr === MTIME || io.dataRW.addr === MTIMECMP
    val inp_mem     = io.dataRW.addr(PADDR_WIDTH-1)
    io.mmio.addr    := io.dataRW.addr
    io.mmio.wdata   := io.dataRW.wdata
    io.dcRW.addr    := io.dataRW.addr
    io.dcRW.wdata   := io.dataRW.wdata
    io.dcRW.amo     := io.dataRW.amo
    io.clintIO.addr    := io.dataRW.addr
    io.clintIO.wdata   := io.dataRW.wdata
    io.dcRW.dc_mode := mode_NOP
    io.mmio.dc_mode := mode_NOP
    io.mmio.amo     := io.dataRW.amo
    io.dataRW.ready := false.B
    io.clintIO.wvalid := false.B
    when(io.dataRW.dc_mode =/= mode_NOP){ // 0: mmio, 1: mem, 2: clint
        when(is_clint){
            pre_type            := 2.U
            io.clintIO.wvalid   := io.dataRW.dc_mode(DC_S_BIT)
            time_r              := io.clintIO.rdata
            time_valid          := true.B
        }.elsewhen(inp_mem){
            pre_type        := 1.U
            io.dcRW.dc_mode := io.dataRW.dc_mode
            io.dataRW.ready := io.dcRW.ready
        }.otherwise{
            pre_type        := 0.U
            io.mmio.dc_mode := io.dataRW.dc_mode
            io.dataRW.ready := io.mmio.ready
        }
    }
    when(pre_type === 2.U && time_valid){
        io.dataRW.rdata     := time_r
        io.dataRW.rvalid    := true.B
        time_valid          := false.B
    }.elsewhen(pre_type === 1.U){
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
        val va2pa   = Flipped(new VA2PA)
        val d_mem1  = Output(new RegForward)
        val d_mem2  = Output(new RegForward)
        val d_mem3  = Output(new RegForward)
    })
    val drop1_r = RegInit(false.B)
    val drop2_r = RegInit(false.B)
    val drop3_r = RegInit(false.B)
    val stall1_r = RegInit(false.B)
    val stall2_r = RegInit(false.B)
    val stall3_r = RegInit(false.B)
    drop2_r := false.B; drop1_r := false.B; drop3_r := false.B;
    stall1_r := false.B; stall2_r := false.B; stall3_r := false.B
    val drop3_in    = drop3_r || io.mem2rb.drop
    val drop2_in    = drop2_r || drop3_in
    val drop1_in    = drop1_r || drop2_in
    val stall3_in = (stall3_r && !io.mem2rb.drop) || io.mem2rb.stall
    val stall2_in = (stall2_r && !drop3_in) || stall3_in
    val stall1_in = (stall1_r && !drop2_in) || stall2_in
    io.ex2mem.drop  := drop1_in
    io.ex2mem.stall := stall1_in
// stage 1
    val inst1_r     = RegInit(0.U(INST_WIDTH.W))
    val pc1_r       = RegInit(0.U(VADDR_WIDTH.W))
    val excep1_r    = RegInit(0.U.asTypeOf(new Exception))
    val ctrl1_r     = RegInit(0.U.asTypeOf(new Ctrl))   // necessary ??
    val mem_addr1_r = RegInit(0.U(VADDR_WIDTH.W))
    val mem_data1_r = RegInit(0.U(DATA_WIDTH.W))
    val dst1_r      = RegInit(0.U(REG_WIDTH.W))
    val dst_d1_r    = RegInit(0.U(DATA_WIDTH.W))
    val dst_en1_r   = RegInit(false.B)
    val csr_id1_r   = RegInit(0.U(CSR_WIDTH.W))
    val csr_d1_r    = RegInit(0.U(DATA_WIDTH.W))
    val csr_en1_r   = RegInit(false.B)
    val special1_r  = RegInit(0.U(2.W))
    val indi1_r     = RegInit(0.U(INDI_WIDTH.W))
    val recov1_r    = RegInit(false.B)

    val valid1_r    = RegInit(false.B)

    def stall_pipe1()={
        drop1_r := true.B; stall1_r := true.B; recov1_r := true.B
    }

    val is_tlb_r    = RegInit(false.B)
    val drop_tlb    = RegInit(false.B)

    val hs_in   = io.ex2mem.ready && io.ex2mem.valid
    val hs1     = Wire(Bool())
    val hs2     = Wire(Bool())
    val hs_out  = io.mem2rb.ready && io.mem2rb.ready
    hs1 := false.B; hs2 := false.B
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
        ctrl1_r     := io.ex2mem.ctrl
        indi1_r     := io.ex2mem.indi
        special1_r  := io.ex2mem.special
        recov1_r    := io.ex2mem.recov
    }
    val access_tlb  = io.ex2mem.ctrl.dcMode =/= mode_NOP
    io.va2pa.vaddr  := Mux(hs_in, io.ex2mem.mem_addr, mem_addr1_r)
    io.va2pa.vvalid := !drop1_in && is_tlb_r && !hs1
    val cur_mode    = Mux(hs_in, io.ex2mem.ctrl.dcMode, ctrl1_r.dcMode)
    io.va2pa.m_type := Mux(cur_mode(DC_L_BIT), MEM_LOAD, MEM_STORE)
    io.ex2mem.ready := false.B
    when(!drop1_in){
        when(valid1_r && !hs1){
        }.elsewhen(io.ex2mem.valid){
            io.ex2mem.ready := true.B
        }
    }
    when(!drop2_in){
        when(hs_in){
            valid1_r := true.B
            is_tlb_r := access_tlb
            io.va2pa.vvalid := access_tlb
        }.elsewhen(hs1){
            valid1_r := false.B
            is_tlb_r := false.B
        }
    }.otherwise{
        valid1_r := false.B
        drop_tlb := is_tlb_r && !io.va2pa.pvalid // next pa will be dropped
    }
    io.d_mem1.id   := dst1_r
    io.d_mem1.data := dst_d1_r
    when(!dst_en1_r){
        io.d_mem1.state := d_invalid
    }.elsewhen(valid1_r && !(ctrl1_r.dcMode(DC_L_BIT) || indi1_r(INDI_SC_BIT))){
        io.d_mem1.state := d_valid
    }.elsewhen(valid1_r){
        io.d_mem1.state := d_wait
    }.otherwise{
        io.d_mem1.state := d_invalid
    }
    // stage 2 (dcache)
    val inst2_r     = RegInit(0.U(INST_WIDTH.W))
    val pc2_r       = RegInit(0.U(VADDR_WIDTH.W))
    val excep2_r    = RegInit(0.U.asTypeOf(new Exception))
    val ctrl2_r     = RegInit(0.U.asTypeOf(new Ctrl))
    val mem_data2_r = RegInit(0.U(DATA_WIDTH.W))
    val dst2_r      = RegInit(0.U(REG_WIDTH.W))
    val dst_d2_r    = RegInit(0.U(DATA_WIDTH.W))
    val dst_en2_r   = RegInit(false.B)
    val csr_id2_r   = RegInit(0.U(CSR_WIDTH.W))
    val csr_d2_r    = RegInit(0.U(DATA_WIDTH.W))
    val csr_en2_r   = RegInit(false.B)
    val special2_r  = RegInit(0.U(2.W))
    val paddr2_r    = RegInit(0.U(PADDR_WIDTH.W))
    val indi2_r     = RegInit(0.U(INDI_WIDTH.W))
    val recov2_r    = RegInit(false.B)
    val valid2_r    = RegInit(false.B)
    val dc_hs_r     = RegInit(false.B)

    def stall_pipe2()={
        drop2_r := true.B; stall2_r := true.B; recov2_r := true.B
    }

    val lr_addr_r   = RegInit(0.U(PADDR_WIDTH.W))
    val lr_valid_r  = RegInit(false.B)

    when(io.va2pa.pvalid && drop_tlb){
        drop_tlb := false.B
    }

    when(hs1){
        inst2_r     := inst1_r
        pc2_r       := pc1_r
        excep2_r    := excep1_r
        mem_data2_r := mem_data1_r
        ctrl2_r     := ctrl1_r
        dst2_r      := dst1_r
        dst_d2_r    := dst_d1_r
        dst_en2_r   := dst_en1_r
        csr_id2_r   := csr_id1_r
        csr_d2_r    := csr_d1_r
        csr_en2_r   := csr_en1_r
        special2_r  := special1_r
        indi2_r     := indi1_r
        recov2_r    := recov1_r
        paddr2_r    := io.va2pa.paddr
        when(indi1_r(INDI_LR_BIT)){
            lr_valid_r  := true.B
            lr_addr_r   := io.va2pa.paddr
        }
        when(excep1_r.etype =/= 0.U){
            lr_valid_r  := false.B
        }
    }
    val sc_valid    = io.va2pa.paddr === lr_addr_r && lr_valid_r
    val is_dc_r     = RegInit(false.B)
    val drop_dc     = RegInit(false.B)
    io.dataRW.dc_mode := mode_NOP
    io.dataRW.addr    := Mux(hs1, io.va2pa.paddr, paddr2_r)
    io.dataRW.wdata   := Mux(hs1, mem_data1_r, mem_data2_r)
    io.dataRW.amo     := Mux(hs1, inst1_r(31, 27), inst2_r(31,27))
    val inp_tlb_valid2 = io.va2pa.pvalid || io.va2pa.tlb_excep.en

    when(hs1){
        when(indi1_r(INDI_SC_BIT) && sc_valid){
            io.dataRW.dc_mode := ctrl1_r.dcMode
            dst_en2_r := true.B
            dst_d2_r  := 0.U
        }.elsewhen(indi1_r(INDI_SC_BIT)){
            ctrl2_r.dcMode  := mode_NOP
            dst_en2_r := true.B
            dst_d2_r  := 1.U
        }.otherwise{
            io.dataRW.dc_mode := ctrl1_r.dcMode
        }
    }.otherwise{
        io.dataRW.dc_mode := Mux(valid2_r && !dc_hs_r, ctrl2_r.dcMode, mode_NOP)
    }

    val tlb_valid2  = !is_tlb_r || (inp_tlb_valid2 && !drop_tlb)
    val dc_hs = io.dataRW.dc_mode =/= mode_NOP && io.dataRW.ready
    when(dc_hs){    // need to access dcache later 
        dc_hs_r := true.B
    }
    when(!drop2_in){
        when(valid2_r && !hs2){
        }.elsewhen(valid1_r && tlb_valid2){
            hs1 := true.B
        }
    }
    when(!drop3_in){
        when(hs1){
            valid2_r := true.B
            when(io.va2pa.tlb_excep.en){
                excep2_r.cause  := io.va2pa.tlb_excep.cause
                excep2_r.tval   := io.va2pa.tlb_excep.tval
                excep2_r.en     := true.B
                excep2_r.pc     := pc1_r
                excep2_r.etype  := 0.U
                ctrl2_r     := 0.U.asTypeOf(new Ctrl)
                is_dc_r     := false.B
                dst_en2_r   := false.B
                csr_en2_r   := false.B
                stall_pipe2()
            }.elsewhen(ctrl1_r.dcMode =/= mode_NOP){
                is_dc_r := true.B
                dc_hs_r := dc_hs
            }.otherwise{
                is_dc_r := false.B
            }
        }.elsewhen(hs2){
            valid2_r := false.B
            dc_hs_r  := false.B
        }
    }.otherwise{
        valid2_r := false.B
        drop_dc  := is_dc_r && !io.dataRW.rvalid
    }

    io.d_mem2.id   := dst2_r
    io.d_mem2.data := dst_d2_r
    when(!dst_en2_r){
        io.d_mem2.state := d_invalid
    }.elsewhen(valid2_r && !is_dc_r){
        io.d_mem2.state := d_valid
    }.elsewhen(valid2_r){
        io.d_mem2.state := d_wait
    }.otherwise{
        io.d_mem2.state := d_invalid
    }

    val inst3_r     = RegInit(0.U(INST_WIDTH.W))
    val pc3_r       = RegInit(0.U(VADDR_WIDTH.W))
    val excep3_r    = RegInit(0.U.asTypeOf(new Exception))
    val dst3_r      = RegInit(0.U(REG_WIDTH.W))
    val dst_d3_r    = RegInit(0.U(DATA_WIDTH.W))
    val dst_en3_r   = RegInit(false.B)
    val csr_id3_r   = RegInit(0.U(CSR_WIDTH.W))
    val csr_d3_r    = RegInit(0.U(DATA_WIDTH.W))
    val csr_en3_r   = RegInit(false.B)
    val special3_r  = RegInit(0.U(2.W))
    val is_mmio_r   = RegInit(false.B)
    val recov3_r    = RegInit(false.B)
    val valid3_r    = RegInit(false.B)

    def stall_pipe3()={
        drop3_r := true.B; stall3_r := true.B; recov3_r := true.B;
    }

    when(hs2){
        inst3_r     := inst2_r
        pc3_r       := pc2_r
        excep3_r    := excep2_r
        dst3_r      := dst2_r
        dst_d3_r    := dst_d2_r
        dst_en3_r   := dst_en2_r
        csr_id3_r   := csr_id2_r
        csr_d3_r    := csr_d2_r
        csr_en3_r   := csr_en2_r
        special3_r  := special2_r
        recov3_r    := recov2_r
        is_mmio_r   := ctrl2_r.dcMode =/= mode_NOP && paddr2_r < "h80000000".U
    }
    when(io.dataRW.rvalid){
        drop_dc := false.B
    }
    val dc_valid3 = !is_dc_r || (io.dataRW.rvalid && !drop_dc)
    when(!drop3_in){
        when(valid3_r && !hs_out){
        }.elsewhen(valid2_r && dc_valid3){
            hs2 := true.B
        }
    }
    when(!io.mem2rb.drop){
         when(hs2){
            valid3_r := true.B
            when(is_dc_r){
                dst_d3_r := io.dataRW.rdata
            }
        }.elsewhen(hs_out){
            valid3_r := false.B
        }
    }.otherwise{
        valid3_r := false.B
    }
    io.mem2rb.inst      := inst3_r
    io.mem2rb.pc        := pc3_r
    io.mem2rb.excep     := excep3_r
    io.mem2rb.csr_id    := csr_id3_r
    io.mem2rb.csr_d     := csr_d3_r
    io.mem2rb.csr_en    := csr_en3_r
    io.mem2rb.dst       := dst3_r
    io.mem2rb.dst_d     := dst_d3_r
    io.mem2rb.dst_en    := dst_en3_r
    io.mem2rb.special   := special3_r
    io.mem2rb.is_mmio   := is_mmio_r
    io.mem2rb.recov     := recov3_r
    io.mem2rb.valid     := valid3_r

    io.d_mem3.id   := dst3_r
    io.d_mem3.data := dst_d3_r
    when(valid3_r && dst_en3_r){
        io.d_mem3.state := d_valid
    }.otherwise{
        io.d_mem3.state := d_invalid
    }
}