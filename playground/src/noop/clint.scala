package noop.clint

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.datapath._
import clint_config._

object clint_config{
    val MTIME = "h200bff8".U(PADDR_WIDTH.W)
    val MTIMECMP = "h2004000".U(PADDR_WIDTH.W)
    val IPI = "h2000000".U(PADDR_WIDTH.W)
}

class CLINT extends Module{
    val io = IO(new Bundle{
        val rw    = new DataRWD
        val intr  = Output(new Intr)
        val intr_msip = Output(new Intr)
    })
    val mtime = RegInit(0.U(DATA_WIDTH.W))
    val mtimecmp = RegInit(0.U(DATA_WIDTH.W))
    val ipi = RegInit(0.U(DATA_WIDTH.W))
    val count = RegInit(0.U(2.W))
    val clear_r = RegInit(false.B)
    clear_r := false.B
    count := count + 1.U
    when(count === 0.U){
        mtime := mtime + 1.U
    }
    io.intr.raise := mtime > mtimecmp
    io.intr.clear := clear_r
    io.intr_msip   := 0.U.asTypeOf(new Intr)
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
            clear_r := true.B
        }
    }
    when(io.rw.addr === IPI){
        io.rw.rdata    := ipi
        when(io.rw.wvalid){
            ipi := io.rw.wdata
            io.intr_msip.raise := io.rw.wdata(0)
            io.intr_msip.clear := !io.rw.wdata(0)
        }
    }
    io.rw.rvalid := true.B

}