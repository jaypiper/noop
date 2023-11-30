package noop.memory

import chisel3._
import chisel3.util._
import difftest.{DiffStoreEvent, DifftestModule}
import noop.datapath._
import noop.param.cache_config._
import noop.param.common._
import noop.param.decode_config._
import noop.utils.PipelineConnect

class MemCrossBar extends Module{ // mtime & mtimecmp can be accessed here
    val io = IO(new Bundle{
        val dataRW  = new DcacheRW
        val mmio    = Flipped(new DcacheRW)
        val dcRW    = Flipped(new DcacheRW)
        val icRW    = Flipped(new DcacheRW)
    })
    val inp_mem     = (io.dataRW.req.bits.addr >= "h8000d000".U) && (io.dataRW.req.bits.addr < "h8000e000".U)
    val inp_ic      = in_imem(io.dataRW.req.bits.addr)

    io.mmio.req.bits := io.dataRW.req.bits
    io.dcRW.req.bits := io.dataRW.req.bits
    io.icRW.req.bits := io.dataRW.req.bits

    io.mmio.req.valid := false.B
    io.dcRW.req.valid := false.B
    io.icRW.req.valid := false.B
    io.dataRW.req.ready := false.B

    when(inp_mem){
        io.dcRW.req.valid := io.dataRW.req.valid
        io.dataRW.req.ready := io.dcRW.req.ready
    }.elsewhen(inp_ic){
        io.icRW.req.valid := io.dataRW.req.valid
        io.dataRW.req.ready := io.icRW.req.ready
    }.otherwise{
        io.mmio.req.valid := io.dataRW.req.valid
        io.dataRW.req.ready := io.mmio.req.ready
    }

    val pre_type = RegInit(0.U(2.W))
    when(io.dataRW.req.fire) {
        pre_type := Mux(inp_mem, 1.U, Mux(inp_ic, 2.U, 0.U))
    }

    when(pre_type === 1.U){
        io.dataRW.resp.bits := io.dcRW.resp.bits
        io.dataRW.resp.valid := io.dcRW.resp.valid
    }.elsewhen(pre_type === 0.U){
        io.dataRW.resp.bits := io.mmio.resp.bits
        io.dataRW.resp.valid := io.mmio.resp.valid
    }.elsewhen(pre_type === 2.U) {
        io.dataRW.resp.bits := io.icRW.resp.bits
        io.dataRW.resp.valid := io.icRW.resp.valid
    }.otherwise{
        io.dataRW.resp.bits := 0.U
        io.dataRW.resp.valid := false.B
    }
}

class Memory extends Module{
    val io = IO(new Bundle{
        val df2mem  = Flipped(DecoupledIO(new DF2MEM))
        val mem2df  = Output(new MEM2DF)
        val mem2wb  = ValidIO(new MEM2RB)
        val dataRW  = Flipped(new DcacheRW)
        val d_mem1  = Output(new RegForward)
        val d_mem0  = Output(new RegForward)
    })
    // stage 1
    val s1_out = Wire(Decoupled(new MEM2RB))

    val wmask = MuxLookup(io.df2mem.bits.ctrl.dcMode(1,0), 0.U(DATA_WIDTH.W))(Seq(
        0.U -> "hff".U(DATA_WIDTH.W),
        1.U -> "hffff".U(DATA_WIDTH.W),
        2.U -> "hffffffff".U(DATA_WIDTH.W),
        3.U -> "hffffffffffffffff".U(DATA_WIDTH.W)
    ))
    io.dataRW.req.bits.addr := io.df2mem.bits.mem_addr
    io.dataRW.req.bits.wdata := io.df2mem.bits.mem_data
    io.dataRW.req.bits.wen   := io.df2mem.bits.ctrl.dcMode(DC_S_BIT)
    io.dataRW.req.valid := io.df2mem.valid && s1_out.ready && io.df2mem.bits.ctrl.dcMode =/= mode_NOP
    io.dataRW.req.bits.wmask := wmask
    io.dataRW.req.bits.size := io.df2mem.bits.ctrl.dcMode(1,0)
    io.df2mem.ready := !io.df2mem.valid || io.dataRW.req.ready && s1_out.ready

    io.d_mem0.id := io.df2mem.bits.dst
    io.d_mem0.data := 0.U
    io.d_mem0.state := Mux(io.df2mem.valid && io.df2mem.bits.ctrl.dcMode(DC_L_BIT), d_wait, d_invalid)

    s1_out.valid := io.df2mem.valid && io.dataRW.req.ready
    s1_out.bits.inst := io.df2mem.bits.inst
    s1_out.bits.pc := io.df2mem.bits.pc
    s1_out.bits.excep := io.df2mem.bits.excep
    s1_out.bits.ctrl := io.df2mem.bits.ctrl
    s1_out.bits.csr_id := io.df2mem.bits.csr_id
    s1_out.bits.csr_d := io.df2mem.bits.csr_d
    s1_out.bits.csr_en := io.df2mem.bits.ctrl.writeCSREn
    s1_out.bits.dst := io.df2mem.bits.dst
    s1_out.bits.dst_d := io.df2mem.bits.dst_d
    s1_out.bits.dst_en := io.df2mem.bits.ctrl.writeRegEn
    s1_out.bits.rcsr_id := io.df2mem.bits.rcsr_id
    s1_out.bits.is_mmio := io.df2mem.bits.mem_addr < "h30000000".U
    s1_out.bits.recov := io.df2mem.bits.recov

    // stage 2
    val mem2wb = Wire(Decoupled(new MEM2RB))
    PipelineConnect(s1_out, mem2wb, io.dataRW.resp.valid, false.B)

    // Since Memory has 2-cycle latency, we have to block following ALU instructions if mem is inflight
    // Otherwise, writeback unit will see simultaneous instructions in
    // TODO: In the future, we can let ALU and memory instruction execute simultaneously.
    io.mem2df.membusy := mem2wb.valid && !io.dataRW.resp.valid

    mem2wb.ready := !mem2wb.valid || io.dataRW.resp.valid
    io.mem2wb.valid := mem2wb.valid && io.dataRW.resp.valid
    io.mem2wb.bits := mem2wb.bits

    val data_uint = io.dataRW.resp.bits
    val read_data = MuxLookup(io.mem2wb.bits.ctrl.dcMode, data_uint)(Seq(
        mode_LB -> Cat(Fill(DATA_WIDTH - 8, data_uint(7)), data_uint(7, 0)),
        mode_LH -> Cat(Fill(DATA_WIDTH - 16, data_uint(15)), data_uint(15, 0)),
        mode_LW -> Cat(Fill(DATA_WIDTH - 32, data_uint(31)), data_uint(31, 0))
    ))
    io.mem2wb.bits.dst_d := read_data

    // Data forwarding
    io.d_mem1.id := io.mem2wb.bits.dst
    io.d_mem1.data := Mux(io.mem2wb.bits.ctrl.dcMode(DC_L_BIT), read_data, io.mem2wb.bits.dst_d)
    io.d_mem1.state := Mux(mem2wb.valid,
        Mux(io.mem2wb.bits.ctrl.dcMode(DC_L_BIT),
            Mux(io.dataRW.resp.valid, d_valid, d_wait),
            Mux(io.mem2wb.bits.ctrl.dcMode === mode_NOP && io.mem2wb.bits.ctrl.writeRegEn, d_valid, d_invalid)
        ),
        d_invalid
    )

    if (isSim) {
        val difftest = DifftestModule(new DiffStoreEvent, dontCare = true, delay = 20)
        val is_mem_store = io.dataRW.req.bits.wen && io.dataRW.req.bits.addr >= "h30000000".U
        difftest.valid := io.dataRW.req.fire && is_mem_store
        difftest.addr := Cat(io.dataRW.req.bits.addr(PADDR_WIDTH - 1, 3), 0.U(3.W))
        difftest.data := (io.dataRW.req.bits.wdata & io.dataRW.req.bits.wmask) << Cat(io.dataRW.req.bits.addr(2, 0), 0.U(3.W))
        difftest.mask := MuxLookup(io.df2mem.bits.ctrl.dcMode(1, 0), 0.U(8.W))(Seq(
            0.U -> "h1".U(8.W),
            1.U -> "h3".U(8.W),
            2.U -> "hf".U(8.W),
            3.U -> "hff".U(8.W)
        )) << io.dataRW.req.bits.addr(2, 0)
    }
}