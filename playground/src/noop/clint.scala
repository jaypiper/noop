package noop.clint

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.datapath._
import clint_config._

object clint_config{
    val MTIME = "h200bff8".U(PADDR_WIDTH.W)
    val MTIMECMP = "h2004000".U(PADDR_WIDTH.W)
}

class CLINT extends Module{
    val io = IO(new Bundle{
        val rw    = new DataRWD
        val intr  = Output(Bool())
    })
    val mtime = RegInit(0.U(DATA_WIDTH.W))
    val mtimecmp = RegInit(0.U(DATA_WIDTH.W))
    val count = RegInit(0.U(2.W))
    when(count === 0.U){
        mtime := mtime + 10.U
    }
    io.intr := mtime > mtimecmp
    io.rw.rdata := 0.U
    when(io.rw.addr === MTIME){
        io.rw.rdata    := mtime
        when(io.rw.wvalid){
            mtime := io.rw.wdata
        }
    }
    when(io.rw.addr === MTIMECMP){
        io.rw.rdata    := mtimecmp
        when(io.rw.wvalid){
            mtimecmp := io.rw.wdata
        }
    }
    io.rw.rvalid := true.B

}