package noop.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._
import ram._
import axi.axi_config._

class ICache extends Module{
    val io = IO(new Bundle{
        val icPort      = new IcacheRead
        val icMem       = new DcacheRW
    })
    val iram = Module(new IRAM)
    val mem_valid_r = RegInit(false.B)
    val ic_valid_r = RegInit(false.B)
    val size_r = RegInit(0.U(2.W))
    val offset_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))

    val ic_valid2_r = RegInit(false.B)
    val offset2_r = RegInit(0.U(ICACHE_OFFEST_WIDTH.W))
    val data2_r = RegInit(0.U(64.W))
    val hs_out = io.icPort.rready && io.icPort.rvalid
    val hs_in = io.icPort.ready && io.icPort.arvalid
    mem_valid_r := io.icMem.avalid
    // offset_r := Mux(io.icMem.avalid, io.icMem.addr, io.icPort.addr)(ICACHE_OFFEST_WIDTH-1, 0)
    size_r := io.icMem.size
    when(hs_out || !ic_valid2_r) {
        ic_valid2_r := ic_valid_r
        offset2_r := offset_r
        data2_r := iram.io.rdata
    }
    when(io.icMem.avalid) {
        offset_r := io.icMem.addr
    }
    when(hs_in) {
        ic_valid_r := io.icPort.arvalid
        offset_r := io.icPort.addr(ICACHE_OFFEST_WIDTH-1, 0)
    }.elsewhen(!ic_valid2_r || hs_out) {
        ic_valid_r := false.B
    }

    iram.io.cen := io.icPort.arvalid || io.icMem.avalid
    iram.io.wen := io.icMem.wen && io.icMem.avalid
    iram.io.addr := Mux(io.icMem.avalid, io.icMem.addr, io.icPort.addr)(ICACHE_IDX_START, ICACHE_IDX_END)

    iram.io.wdata := MuxLookup(io.icMem.size, 0.U, List(
	(0.U -> Fill(8, io.icMem.wdata(7,0))),
	(1.U -> Fill(4, io.icMem.wdata(15,0))),
	(2.U -> Fill(2, io.icMem.wdata(31,0))),
	(3.U -> io.icMem.wdata)		
    ))
    iram.io.wmask := MuxLookup(io.icMem.size, 0.U, List(
       (0.U -> VecInit((0 to 7).map(i => (0x1 << i).U))(io.icMem.addr(2,0))), // sb
       (1.U -> VecInit((0 to 3).map(_ * 2).map(i => (0x3 << i).U))(io.icMem.addr(2,1))), //sh
       (2.U -> VecInit((0 to 1).map(_ * 4).map(i => (0xf << i).U))(io.icMem.addr(2))), // sw
       (3.U -> Fill(8, 1.U(1.W)))
    ) )

    io.icMem.rvalid := mem_valid_r
    io.icMem.ready := true.B
    io.icMem.rdata := 0.U

    io.icPort.rvalid := ic_valid2_r
    io.icPort.ready := !ic_valid2_r || io.icPort.rready
    io.icPort.inst := data2_r
}
