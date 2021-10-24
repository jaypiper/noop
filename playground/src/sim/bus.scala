package sim

import chisel3._
import chisel3.util._
import axi._
import noop.datapath._
import noop.cpu.CPU_AXI_IO

class TransAXI extends Module{
    val io = IO(new Bundle{
        val raw_axi = Flipped(new CPU_AXI_IO)
        val bun_axi = new AxiMaster
    })


    io.raw_axi.awready       := io.bun_axi.wa.ready
    io.bun_axi.wa.valid      := io.raw_axi.awvalid
    io.bun_axi.wa.bits.addr  := io.raw_axi.awaddr
    io.bun_axi.wa.bits.id    := io.raw_axi.awid
    io.bun_axi.wa.bits.len   := io.raw_axi.awlen
    io.bun_axi.wa.bits.size  := io.raw_axi.awsize
    io.bun_axi.wa.bits.burst := io.raw_axi.awburst

    io.raw_axi.wready        := io.bun_axi.wd.ready
    io.bun_axi.wd.valid      := io.raw_axi.wvalid
    io.bun_axi.wd.bits.data  := io.raw_axi.wdata
    io.bun_axi.wd.bits.strb  := io.raw_axi.wstrb
    io.bun_axi.wd.bits.last  := io.raw_axi.wlast

    io.bun_axi.wr.ready      := io.raw_axi.bready
    io.raw_axi.bvalid        := io.bun_axi.wr.valid
    io.raw_axi.bresp         := io.bun_axi.wr.bits.resp
    io.raw_axi.bid           := io.bun_axi.wr.bits.id

    io.raw_axi.arready       := io.bun_axi.ra.ready
    io.bun_axi.ra.valid      := io.raw_axi.arvalid
    io.bun_axi.ra.bits.addr  := io.raw_axi.araddr
    io.bun_axi.ra.bits.id    := io.raw_axi.arid
    io.bun_axi.ra.bits.len   := io.raw_axi.arlen
    io.bun_axi.ra.bits.size  := io.raw_axi.arsize
    io.bun_axi.ra.bits.burst := io.raw_axi.arburst

    io.bun_axi.rd.ready      := io.raw_axi.rready
    io.raw_axi.rvalid        := io.bun_axi.rd.valid
    io.raw_axi.rresp         := io.bun_axi.rd.bits.resp
    io.raw_axi.rdata         := io.bun_axi.rd.bits.data
    io.raw_axi.rlast         := io.bun_axi.rd.bits.last
    io.raw_axi.rid           := io.bun_axi.rd.bits.id

}