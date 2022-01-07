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

    io.raw_axi.arprot       := 0.U
    io.raw_axi.buser        := 0.U
    io.raw_axi.ruser        := 0.U

}

class TransAXI_R extends Module{
    val io = IO(new Bundle{
        val raw_axi = new AxiSlave
        val bun_axi = new CPU_AXI_IO
    })


    io.bun_axi.awready       := io.raw_axi.wa.ready
    io.raw_axi.wa.valid      := io.bun_axi.awvalid
    io.raw_axi.wa.bits.addr  := io.bun_axi.awaddr
    io.raw_axi.wa.bits.id    := io.bun_axi.awid
    io.raw_axi.wa.bits.len   := io.bun_axi.awlen
    io.raw_axi.wa.bits.size  := io.bun_axi.awsize
    io.raw_axi.wa.bits.burst := io.bun_axi.awburst

    io.bun_axi.wready        := io.raw_axi.wd.ready
    io.raw_axi.wd.valid      := io.bun_axi.wvalid
    io.raw_axi.wd.bits.data  := io.bun_axi.wdata
    io.raw_axi.wd.bits.strb  := io.bun_axi.wstrb
    io.raw_axi.wd.bits.last  := io.bun_axi.wlast

    io.raw_axi.wr.ready      := io.bun_axi.bready
    io.bun_axi.bvalid        := io.raw_axi.wr.valid
    io.bun_axi.bresp         := io.raw_axi.wr.bits.resp
    io.bun_axi.bid           := io.raw_axi.wr.bits.id

    io.bun_axi.arready       := io.raw_axi.ra.ready
    io.raw_axi.ra.valid      := io.bun_axi.arvalid
    io.raw_axi.ra.bits.addr  := io.bun_axi.araddr
    io.raw_axi.ra.bits.id    := io.bun_axi.arid
    io.raw_axi.ra.bits.len   := io.bun_axi.arlen
    io.raw_axi.ra.bits.size  := io.bun_axi.arsize
    io.raw_axi.ra.bits.burst := io.bun_axi.arburst

    io.raw_axi.rd.ready      := io.bun_axi.rready
    io.bun_axi.rvalid        := io.raw_axi.rd.valid
    io.bun_axi.rresp         := io.raw_axi.rd.bits.resp
    io.bun_axi.rdata         := io.raw_axi.rd.bits.data
    io.bun_axi.rlast         := io.raw_axi.rd.bits.last
    io.bun_axi.rid           := io.raw_axi.rd.bits.id      

}