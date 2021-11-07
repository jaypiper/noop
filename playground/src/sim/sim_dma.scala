package sim

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.datapath._
import axi._
import axi.axi_config._
import noop.cpu._

class SimDma extends Module{
    val io = IO(new Bundle{
        val dmaAxi = new CPU_AXI_IO
    })

    val sIdle :: sRaddr :: sRdata :: sWaddr :: sWdata :: sWresp :: Nil = Enum(6)
    val state = RegInit(sIdle)
    val count = RegInit(0.U(DATA_WIDTH.W))
    count := count + 1.U
    val enable_r = RegInit(false.B)
    val enable_w = RegInit(false.B)

    when(count === 1000010.U){
        // enable_w := true.B
    }
    when(count === 1220200.U){
        // enable_r := true.B
    }

    val awvalid_r   = RegInit(false.B)
    val awaddr_r    = RegInit(0.U(PADDR_WIDTH.W))
    val awid_r      = RegInit(0.U(4.W))
    val awlen_r     = RegInit(0.U(8.W))
    val awsize_r    = RegInit(0.U(3.W))
    val awburst_r   = RegInit(0.U(2.W))
    val wvalid_r    = RegInit(false.B)
    val wdata_r     = RegInit(0.U(DATA_WIDTH.W))
    val wstrb_r     = RegInit(0.U(8.W))
    val wlast_r     = RegInit(false.B)
    val bready_r    = RegInit(false.B)
    val arvalid_r   = RegInit(false.B)
    val araddr_r    = RegInit(0.U(PADDR_WIDTH.W))
    val arid_r      = RegInit(0.U(2.W))
    val arlen_r     = RegInit(0.U(8.W))
    val arsize_r    = RegInit(0.U(3.W))
    val arburst_r   = RegInit(0.U(2.W))
    val rready_r    = RegInit(false.B)
    val count_r     = RegInit(0.U(3.W))
    switch(state){
        is(sIdle){
            when(enable_r){
                state := sRaddr
                arvalid_r := true.B
                araddr_r := "h81000004".U(PADDR_WIDTH.W)
                arid_r := 1.U
                arlen_r := 3.U
                arsize_r := 0.U
                arburst_r := BURST_INCR
                enable_r := false.B
            }
            when(enable_w){
                state := sWaddr
                awvalid_r := true.B
                awaddr_r := "h81000004".U(PADDR_WIDTH.W)
                awid_r := 1.U
                awlen_r := 3.U
                awsize_r := 0.U
                awburst_r := BURST_INCR
                enable_w := false.B
            }
        }
        is(sRaddr){
            when(io.dmaAxi.arready && arvalid_r){
                arvalid_r := false.B
                rready_r := true.B
                state := sRdata
            }
        }
        is(sRdata){
            when(io.dmaAxi.rvalid && rready_r){
                printf("dma rdata: %x\n", io.dmaAxi.rdata)
                when(io.dmaAxi.rlast){
                    state := sIdle
                    rready_r := false.B
                }
            }
        }
        is(sWaddr){
            when(io.dmaAxi.awready && awvalid_r){
                awvalid_r := false.B
                wdata_r := "h1234567887654321".U(DATA_WIDTH.W)
                wstrb_r := 0x10.U
                wvalid_r := true.B
                state := sWdata
            }
        }
        is(sWdata){
            when(io.dmaAxi.wready && wvalid_r){
                wdata_r := wdata_r >> 8.U
                wstrb_r := wstrb_r << 1.U
                count_r := count_r + 1.U
                wlast_r := count_r === 2.U
                when(io.dmaAxi.wlast){
                    state := sWresp
                    wvalid_r := false.B
                    wlast_r := false.B
                    bready_r := true.B
                }
            }
        }
        is(sWresp){
            when(bready_r && io.dmaAxi.bvalid){
                state := sIdle
                bready_r := false.B
            }
        }
    }
    io.dmaAxi.awvalid   := awvalid_r
    io.dmaAxi.awaddr    := awaddr_r
    io.dmaAxi.awid      := awid_r
    io.dmaAxi.awlen     := awlen_r
    io.dmaAxi.awsize    := awsize_r
    io.dmaAxi.awburst   := awburst_r
    io.dmaAxi.wvalid    := wvalid_r
    io.dmaAxi.wdata     := wdata_r
    io.dmaAxi.wstrb     := wstrb_r
    io.dmaAxi.wlast     := wlast_r
    io.dmaAxi.bready    := bready_r
    io.dmaAxi.arvalid   := arvalid_r
    io.dmaAxi.araddr    := araddr_r
    io.dmaAxi.arid      := arid_r
    io.dmaAxi.arlen     := arlen_r
    io.dmaAxi.arsize    := arsize_r
    io.dmaAxi.arburst   := arburst_r
    io.dmaAxi.rready    := rready_r
}