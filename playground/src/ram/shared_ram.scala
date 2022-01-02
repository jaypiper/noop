package shared_ram

import chisel3._
import chisel3.util._
import chisel3.util.random._
import SharedRamParams._
import ram._

object SharedRamParams{
    val SR_ADDR_WIDTH = 6
    val SR_DATA_BYTES = 16
    val SR_DATA_WIDTH = SR_DATA_BYTES * 8
    // val SR_RAM_NUM = 4
    val RAM_ADDR_WIDTH = 6
}

class RamSelector extends Module{
    val ID_WIDTH = 2
    val USER_NUM = 1 << ID_WIDTH
    val io = IO(new Bundle{
        val id = Input(UInt(ID_WIDTH.W))
        val srio00 = new SharedRamIO
        val srio01 = new SharedRamIO
        val srio02 = new SharedRamIO
        val srio03 = new SharedRamIO
        val srio04 = new SharedRamIO
        val srio05 = new SharedRamIO
        val srio06 = new SharedRamIO
        val srio07 = new SharedRamIO
        val select0 = Flipped(new SharedRamIO)
        val select1 = Flipped(new SharedRamIO)
        val select2 = Flipped(new SharedRamIO)
        val select3 = Flipped(new SharedRamIO)
        val select4 = Flipped(new SharedRamIO)
        val select5 = Flipped(new SharedRamIO)
        val select6 = Flipped(new SharedRamIO)
        val select7 = Flipped(new SharedRamIO)
    })
    io.srio00.init_out()
    io.srio01.init_out()
    io.srio02.init_out()
    io.srio03.init_out()
    io.srio04.init_out()
    io.srio05.init_out()
    io.srio06.init_out()
    io.srio07.init_out()
    io.select0.init_in()
    io.select1.init_in()
    io.select2.init_in()
    io.select3.init_in()
    io.select4.init_in()
    io.select5.init_in()
    io.select6.init_in()
    io.select7.init_in()
    when(io.id === 0.U){
        io.select0 <> io.srio00
        io.select1 <> io.srio01
        io.select2 <> io.srio02
        io.select3 <> io.srio03
        io.select4 <> io.srio04
        io.select5 <> io.srio05
        io.select6 <> io.srio06
        io.select7 <> io.srio07
    }
    // io.selected <> MuxLookup(io.id, io.srio0, Seq(
    //     0.U -> io.srio0
    // ))

}

class SharedRamIO extends Bundle{
    val addr = Input(UInt(SR_ADDR_WIDTH.W))
    val cen = Input(Bool())
    val wen = Input(Bool())
    val wmask = Input(UInt(SR_DATA_WIDTH.W))
    val wdata = Input(UInt(SR_DATA_WIDTH.W))
    val rdata = Output(UInt(SR_DATA_WIDTH.W))
    def init_in() = {
        addr := 0.U
        cen := 0.U
        wen := 0.U
        wmask := 0.U
        wdata := 0.U
    }
    def init_out() = {
        rdata := 0.U
    }
}

class SharedRam extends Module{
    val io = IO(new SharedRamIO)

    val ram = Module(new S011HD1P_X32Y2D128_BW)

    ram.io.CLK := clock
    ram.io.A := io.addr(RAM_ADDR_WIDTH - 1,0)
    ram.io.BWEN := io.wmask
    ram.io.CEN := io.cen
    ram.io.WEN := io.wen
    ram.io.D := io.wdata
    io.rdata := ram.io.Q

}