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

class RF1FCIC_1024X32M8WM8 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val Q = Output(UInt(32.W))
        val CLK = Input(Clock())
        val CEN = Input(Bool())
        val GWEN = Input(Bool())
        val WEN = Input(UInt(4.W))
        val A = Input(UInt(10.W))
        val D = Input(UInt(32.W))
        val EMA = Input(UInt(3.W))
        val EMAW = Input(UInt(2.W))
        val EMAS = Input(Bool())
        val RET1N = Input(Bool())
        val SO = Output(UInt(2.W))
        val SI = Input(UInt(2.W))
        val SE = Input(Bool())
        val DFTRAMBYP = Input(Bool())
    })
    addPath("playground/src/ram/RF1FCIC_1024X32M8WM8.v")
}

class RF1FCIC_1024X32M8WM8_SIM extends Module {
    val io = IO(new Bundle {
        val Q = Output(UInt(32.W))
        val CLK = Input(Clock())
        val CEN = Input(Bool())
        val GWEN = Input(Bool())
        val WEN = Input(UInt(4.W))
        val A = Input(UInt(10.W))
        val D = Input(UInt(32.W))
    })
    val ram = Module(new RF1FCIC_1024X32M8WM8)
    ram.io.EMA := 0.U
    ram.io.EMAW := 0.U
    ram.io.EMAS := false.B
    ram.io.RET1N := true.B
    ram.io.DFTRAMBYP := false.B
    ram.io.SI := 0.U
    ram.io.SE := false.B
    io.Q := ram.io.Q
    ram.io.CLK := io.CLK
    ram.io.CEN := io.CEN
    ram.io.GWEN := io.GWEN
    ram.io.WEN := io.WEN
    ram.io.A := io.A
    ram.io.D := io.D
}

class RF1FCIC_512X4M8WM1 extends BlackBox with HasBlackBoxPath {
    val io = IO(new Bundle {
        val Q = Output(UInt(4.W))
        val CLK = Input(Clock())
        val CEN = Input(Bool())
        val GWEN = Input(Bool())
        val WEN = Input(UInt(4.W))
        val A = Input(UInt(9.W))
        val D = Input(UInt(4.W))
        val EMA = Input(UInt(3.W))
        val EMAW = Input(UInt(2.W))
        val EMAS = Input(Bool())
        val RET1N = Input(Bool())
        val SO = Output(UInt(2.W))
        val SI = Input(UInt(2.W))
        val SE = Input(Bool())
        val DFTRAMBYP = Input(Bool())
    })
    addPath("playground/src/ram/RF1FCIC_512X4M8WM1.v")
}

class RF1FCIC_512X4M8WM1_SIM extends Module {
    val io = IO(new Bundle {
        val Q = Output(UInt(4.W))
        val CLK = Input(Clock())
        val CEN = Input(Bool())
        val GWEN = Input(Bool())
        val WEN = Input(UInt(4.W))
        val A = Input(UInt(9.W))
        val D = Input(UInt(4.W))
    })
    val ram = Module(new RF1FCIC_512X4M8WM1)
    ram.io.EMA := 0.U
    ram.io.EMAW := 0.U
    ram.io.EMAS := false.B
    ram.io.RET1N := true.B
    ram.io.DFTRAMBYP := false.B
    ram.io.SI := 0.U
    ram.io.SE := false.B
    io.Q := ram.io.Q
    ram.io.CLK := io.CLK
    ram.io.CEN := io.CEN
    ram.io.GWEN := io.GWEN
    ram.io.WEN := io.WEN
    ram.io.A := io.A
    ram.io.D := io.D
}

class IRAM extends Module {
    val io = IO(new Bundle {
        val cen = Input(Bool())
        val wen = Input(Bool())
        val addr = Input(UInt(ICACHE_IDX_WIDTH.W))
        val wdata = Input(UInt(32.W))
        val rdata = Output(UInt(32.W))
        val wmask = Input(UInt(4.W))
    })
if(SRAM) {
    val data = VecInit(Seq.fill(IRAM_NUM)(Module(new RF1FCIC_1024X32M8WM8_SIM).io))
    val select = io.addr(ICACHE_IDX_WIDTH - 1, ICACHE_IDX_WIDTH - log2Floor(IRAM_NUM))
    val select_r = RegInit(0.U(log2Floor(IRAM_NUM).W))
    select_r := select
    for (i <- 0 until IRAM_NUM) {
        data(i).CLK := clock
        data(i).CEN := ~((select === i.U) & io.cen)
        data(i).GWEN := ~io.wen
        data(i).A := io.addr(9, 0)
        data(i).D := io.wdata
        data(i).WEN := ~io.wmask
    }
    io.rdata := data(select_r).Q
} else {
    val data = Mem(32, UInt(64.W))
    val data_r = RegNext(data(io.addr(4,0)))
    when(io.cen && io.wen) {
        data(io.addr(4, 0)) := io.wdata
    }

    io.rdata := data_r
}

}

class DRAM extends Module {
    val io = IO(new Bundle {
        val cen = Input(Bool())
        val wen = Input(Bool())
        val addr = Input(UInt(DCACHE_IDX_WIDTH.W))
        val wdata = Input(UInt(64.W))
        val rdata = Output(UInt(64.W))
        val wmask = Input(UInt(8.W))
    })
    val data = VecInit(Seq.fill(16)(Module(new RF1FCIC_512X4M8WM1_SIM).io))
    for (j <- 0 until 16) {
        data(j).CLK := clock
        data(j).CEN := ~io.cen
        data(j).GWEN := ~io.wen
        data(j).A := io.addr(8, 0)
        data(j).D := io.wdata(3 + j*4, j*4)
        data(j).WEN := Fill(4, ~io.wmask(j/2))
    }
    io.rdata := Cat((0 until 16).reverse.map(i => data(i).Q))

}