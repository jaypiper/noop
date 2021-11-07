package noop.dma

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.datapath._
import axi._
import axi.axi_config._
import noop.cpu._

class DmaBridge extends Module{
    val io = IO(new Bundle{
        val dmaAxi = Flipped(new CPU_AXI_IO)
        val dcRW   = Flipped(new DcacheRW)
    })
    val sIdle :: sRaddr :: sRdata :: sWaddr :: sWdata :: sWresp :: sDcRead :: sDcWrite :: Nil = Enum(8)
    val state = RegInit(sIdle)

    val awready_r   = RegInit(false.B)
    val wready_r    = RegInit(false.B)
    val bvalid_r    = RegInit(false.B)
    val bresp_r     = RegInit(0.U(2.W))
    val bid_r       = RegInit(0.U(4.W))
    val arready_r   = RegInit(false.B)
    val rvalid_r    = RegInit(false.B)
    val rresp_r     = RegInit(0.U(2.W))
    val rdata_r     = RegInit(0.U(DATA_WIDTH.W))
    val rlast_r     = RegInit(false.B)
    val rid_r       = RegInit(0.U(4.W))

    val dc_addr_r   = RegInit(0.U(PADDR_WIDTH.W))
    val dc_wdata_r  = RegInit(0.U(DATA_WIDTH.W))
    val dc_mode_r   = RegInit(0.U(DC_MODE_WIDTH.W))
    val data_buf_r  = RegInit(0.U(DATA_WIDTH.W))
    val data_strb_r = RegInit(0.U(8.W))
    val addr_left_r = RegInit(0.U(3.W))

    val addr_r      = RegInit(0.U(32.W))
    val id_r        = RegInit(0.U(4.W))
    val len_r       = RegInit(0.U(8.W))
    val size_r      = RegInit(0.U(8.W))
    val burst_r     = RegInit(0.U(2.W))


    switch(state){
        is(sIdle){
            when(io.dmaAxi.arvalid){
                state       := sRaddr
                arready_r   := true.B
            }
            when(io.dmaAxi.awvalid){
                state       := sWaddr
                awready_r   := true.B
            }
        }
        is(sRaddr){
            arready_r   := false.B
            addr_r      := io.dmaAxi.araddr
            id_r        := io.dmaAxi.arid
            len_r       := io.dmaAxi.arlen
            size_r      := 1.U << io.dmaAxi.arsize
            burst_r     := io.dmaAxi.arburst
            state       := sDcRead
            dc_addr_r   := io.dmaAxi.araddr & ~0x7.U(PADDR_WIDTH.W)
            dc_mode_r   := mode_LD
        }
        is(sDcRead){
            when(io.dcRW.ready && (dc_mode_r =/= mode_NOP)){
                dc_mode_r := mode_NOP
            }
            when(io.dcRW.rvalid){
                data_buf_r := io.dcRW.rdata
                state := sRdata
            }
        }
        is(sRdata){
            rdata_r := data_buf_r
            rvalid_r := true.B
            rlast_r := len_r === 0.U
            rid_r   := id_r
            rresp_r := RESP_OKAY
            when(io.dmaAxi.rready && rvalid_r){
                rvalid_r := false.B
                when(rlast_r){
                    state := sIdle
                }.otherwise{
                    len_r := len_r - 1.U
                    dc_addr_r := (addr_r + size_r) & ~0x7.U(PADDR_WIDTH.W)
                    addr_r := addr_r + size_r
                    dc_mode_r := mode_LD
                    state := sDcRead
                }
            }
        }
        is(sWaddr){
            awready_r   := false.B
            addr_r      := io.dmaAxi.awaddr
            dc_addr_r   := io.dmaAxi.awaddr & ~0x7.U(PADDR_WIDTH.W)
            id_r        := io.dmaAxi.awid
            len_r       := io.dmaAxi.awlen
            size_r      := 1.U << io.dmaAxi.awsize
            burst_r     := io.dmaAxi.awburst
            wready_r    := true.B
            state       := sWdata
        }
        is(sWdata){
            when(io.dmaAxi.wvalid && wready_r){
                wready_r    := false.B
                data_buf_r  := io.dmaAxi.wdata
                data_strb_r := io.dmaAxi.wstrb
                addr_left_r := 8.U
                state       := sDcWrite
            }
        }
        is(sDcWrite){
            when(data_strb_r === 0.U){
                addr_r := addr_r + size_r
                dc_addr_r := (addr_r + size_r) & ~0x7.U(PADDR_WIDTH.W)
                when(len_r === 0.U){
                    bvalid_r := true.B
                    bresp_r := RESP_OKAY
                    bid_r   := id_r
                    state   := sWresp
                }.otherwise{
                    state := sWdata
                    wready_r := true.B
                    len_r := len_r - 1.U
                }
            }.otherwise{
                when(data_strb_r(0)){
                    dc_mode_r   := mode_SB
                    dc_wdata_r  := data_buf_r
                }.otherwise{
                    data_strb_r := data_strb_r >> 1.U
                    data_buf_r  := data_buf_r >> 8.U
                    dc_addr_r   := dc_addr_r + 1.U
                    addr_left_r := addr_left_r - 1.U
                }
                when(io.dcRW.ready && dc_mode_r =/= mode_NOP){
                    dc_mode_r   := mode_NOP
                    data_strb_r := data_strb_r >> 1.U
                    data_buf_r  := data_buf_r >> 8.U
                    dc_addr_r   := dc_addr_r + 1.U
                    addr_left_r := addr_left_r - 1.U
                }
            }
        }
        is(sWresp){
            when(io.dmaAxi.bready && bvalid_r){
                state := sIdle
                bresp_r := false.B
            }
        }
    }
    io.dmaAxi.awready   := awready_r
    io.dmaAxi.wready    := wready_r
    io.dmaAxi.bvalid    := bvalid_r
    io.dmaAxi.bresp     := bresp_r
    io.dmaAxi.bid       := bid_r
    io.dmaAxi.arready   := arready_r
    io.dmaAxi.rvalid    := rvalid_r
    io.dmaAxi.rresp     := rresp_r
    io.dmaAxi.rdata     := rdata_r
    io.dmaAxi.rlast     := rlast_r
    io.dmaAxi.rid       := rid_r

    io.dcRW.addr    := dc_addr_r
    io.dcRW.wdata   := dc_wdata_r
    io.dcRW.dc_mode := dc_mode_r
    io.dcRW.amo     := 0.U
}