package ram

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._

class RamIO extends Bundle{
    val cen     = Input(Bool())
    val wen     = Input(Bool())
    val addr    = Input(UInt(RAM_ADDR_WIDTH.W))
    val rdata   = Output(UInt(RAM_DATA_WIDTH.W))
    val wdata   = Input(UInt(RAM_DATA_WIDTH.W))
    def init() = {
        cen := false.B
        wen := false.B
        addr := 0.U
        wdata := 0.U
    }
}

class Ram extends Module{
    val io = IO(new RamIO)
    val ram = Module(new S011HD1P_X32Y2D128)
    io.rdata := ram.io.Q
    ram.io.CLK := clock
    ram.io.CEN := ~io.cen
    ram.io.WEN := ~io.wen
    ram.io.A := io.addr
    ram.io.D := io.wdata
}

class Ram_bwIO extends Bundle{
    val cen     = Input(Bool())
    val wen     = Input(Bool())
    val addr    = Input(UInt(RAM_ADDR_WIDTH.W))
    val rdata   = Output(UInt(RAM_DATA_WIDTH.W))
    val wdata   = Input(UInt(RAM_DATA_WIDTH.W))
    val mask    = Input(UInt(RAM_DATA_WIDTH.W))
    def init() = {
        cen     := false.B
        wen     := false.B
        addr    := 0.U
        wdata   := 0.U
        mask    := 0.U
    }
}

// class Ram_bw extends Module{
//     val io = IO(new Ram_bwIO)
//     val ram = Module(new S011HD1P_X32Y2D128_BW)
//     io.rdata := ram.io.Q
//     ram.io.CLK := clock
//     ram.io.CEN := ~io.cen
//     ram.io.WEN := ~io.wen
//     ram.io.A := io.addr
//     ram.io.D := io.wdata
//     ram.io.BWEN := ~io.mask
// }

class S011HD1P_X32Y2D128 extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val Q = Output(UInt(128.W))
        val CLK = Input(Clock())
        val CEN = Input(Bool())
        val WEN = Input(Bool())
        val A = Input(UInt(6.W))
        val D = Input(UInt(128.W))
    })
    addPath("playground/src/ram/S011HD1P_X32Y2D128.v")
}

// class S011HD1P_X32Y2D128_BW extends BlackBox with HasBlackBoxPath{
//     val io = IO(new Bundle{
//         val Q    = Output(UInt(128.W))
//         val CLK  = Input(Clock())
//         val CEN  = Input(Bool())
//         val WEN  = Input(Bool())
//         val BWEN = Input(UInt(128.W))
//         val A    = Input(UInt(6.W))
//         val D    = Input(UInt(128.W))
//     })
//     addPath("playground/src/ram/S011HD1P_X32Y2D128_BW.v")
// }

class TS5N28HPCPLVTA512X64M4FW extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val CLK = Input(Clock())
        val CEB = Input(Bool())
        val WEB = Input(Bool())
        val A = Input(UInt(9.W))
        val D = Input(UInt(64.W))
        val BWEB = Input(UInt(64.W))
        val Q = Output(UInt(64.W))
    })
    addPath("playground/src/ram/TS5N28HPCPLVTA512X64M4FW.v")
}

class IRAM extends Module {
    val io = IO(new Bundle {
        val cen = Input(Bool())
        val wen = Input(Bool())
        val addr = Input(UInt(ICACHE_IDX_WIDTH.W))
        val wdata = Input(UInt(64.W))
        val rdata = Output(UInt(64.W))
        val wmask = Input(UInt(64.W))
    })
    val data = VecInit(Seq.fill(IRAM_NUM)(Module(new TS5N28HPCPLVTA512X64M4FW).io))
    val select = io.addr(ICACHE_IDX_WIDTH-1, ICACHE_IDX_WIDTH - log2Floor(IRAM_NUM))
    val select_r = RegInit(0.U(log2Floor(IRAM_NUM).W))
    select_r := select
    for (i <- 0 until IRAM_NUM) {
        data(i).CLK := clock
        data(i).CEB := ~((select === i.U) & io.cen)
        data(i).WEB := ~io.wen
        data(i).A := io.addr(8, 0)
        data(i).D := io.wdata
        data(i).BWEB := ~io.wmask
    }
    io.rdata := data(select_r).Q
}

class DRAM extends Module {
    val io = IO(new Bundle {
        val cen = Input(Bool())
        val wen = Input(Bool())
        val addr = Input(UInt(DCACHE_IDX_WIDTH.W))
        val wdata = Input(UInt(64.W))
        val rdata = Output(UInt(64.W))
        val wmask = Input(UInt(64.W))
    })
    val data = VecInit(Seq.fill(DRAM_NUM)(Module(new TS5N28HPCPLVTA512X64M4FW).io))
    val select = io.addr(DCACHE_IDX_WIDTH-1, DCACHE_IDX_WIDTH - log2Floor(DRAM_NUM))
    val select_r = RegInit(0.U(log2Floor(DRAM_NUM).W))
    select_r := select
    for (i <- 0 until DRAM_NUM) {
        data(i).CLK := clock
        data(i).CEB := ~((select === i.U) & io.cen)
        data(i).WEB := ~io.wen
        data(i).A := io.addr(8, 0)
        data(i).D := io.wdata
        data(i).BWEB := ~io.wmask
    }
    io.rdata := data(select_r).Q
}