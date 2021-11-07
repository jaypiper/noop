package noop.plic

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.datapath._
import plic_config._

object plic_config{
    val PLIC_BASE       = "hc000000".U(PADDR_WIDTH.W)
    val PLIC_PRIORITY   = "hc000004".U(PADDR_WIDTH.W)
    val PLIC_PENDING    = "hc001000".U(PADDR_WIDTH.W)
    val PLIC_ENABLE     = "hc002000".U(PADDR_WIDTH.W)
    val PLIC_THRESHOLD  = "hc200000".U(PADDR_WIDTH.W)
    val PLIC_CLAIM      = "hc200004".U(PADDR_WIDTH.W)

    def set_pending(pending: UInt, idx: UInt, value: UInt) = {
        val bit_idx = idx(5,0)
        val mask = 1.U << bit_idx
        (pending & ~mask) | ((value << bit_idx) & mask)
    }
}

class Plic extends Module{
    val io = IO(new Bundle{
        val intr_in1 = Input(Bool())
        val intr_out_m = Output(new Intr)
        val intr_out_s = Output(new Intr)
        val rw = new PlicRW
    })
    val priority = RegInit(0.U(32.W))
    val pending = RegInit(0.U(32.W))
    val intr_enable = RegInit(0.U(32.W))
    val threshold = RegInit(0.U(32.W))
    val claim = RegInit(0.U(32.W))

    val clear_r = RegInit(false.B)
    clear_r := false.B

    io.intr_out_s.raise := pending(1) && priority >= threshold
    io.intr_out_s.clear := clear_r
    io.intr_out_m.raise := pending(1) && priority >= threshold
    io.intr_out_m.clear := clear_r
    io.rw.rdata := 0.U; io.rw.rvalid := true.B
    when(io.intr_in1){
        pending := set_pending(pending, 1.U, 1.U)
    }
    when(io.intr_out_m.raise){
        claim := 1.U
    }

    when(io.rw.addr === PLIC_PRIORITY){
        io.rw.rdata := priority
        when(io.rw.wvalid){
            priority := io.rw.wdata
        }
    }
    when(io.rw.addr === PLIC_ENABLE){
        io.rw.rdata := intr_enable
        when(io.rw.wvalid){
            intr_enable := io.rw.wdata
        }
    }
    when(io.rw.addr === PLIC_CLAIM){
        io.rw.rdata := claim
        when(io.rw.arvalid){
            pending := set_pending(pending, claim, 0.U)
            clear_r := true.B
        }
        when(io.rw.wvalid){
            claim := io.rw.wdata
        }
    }
    when(io.rw.addr === PLIC_THRESHOLD){
        io.rw.rdata := threshold
        when(io.rw.wvalid){
            threshold := io.rw.wdata
        }
    }

}