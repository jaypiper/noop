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

class Ram_bw extends Module{
    val io = IO(new Ram_bwIO)
    val ram = Module(new S011HD1P_X32Y2D128_BW)
    io.rdata := ram.io.Q
    ram.io.CLK := clock
    ram.io.CEN := ~io.cen
    ram.io.WEN := ~io.wen
    ram.io.A := io.addr
    ram.io.D := io.wdata
    ram.io.BWEN := ~io.mask
}

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

class S011HD1P_X32Y2D128_BW extends BlackBox with HasBlackBoxPath{
    val io = IO(new Bundle{
        val Q    = Output(UInt(128.W))
        val CLK  = Input(Clock())
        val CEN  = Input(Bool())
        val WEN  = Input(Bool())
        val BWEN = Input(UInt(128.W))
        val A    = Input(UInt(6.W))
        val D    = Input(UInt(128.W))
    })
    addPath("playground/src/ram/S011HD1P_X32Y2D128_BW.v")
}