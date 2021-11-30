package noop.device

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.cpu.CPU_AXI_IO
import axi.axi_config._

class Mapping extends Module{
    val io = IO(new Bundle{
        val map_in = Flipped(new CPU_AXI_IO)
        val ctrl_in = Flipped(new CPU_AXI_IO)
        val map_out = new CPU_AXI_IO
    })
    val offset = RegInit(0.U(PADDR_WIDTH.W))
    val sIdle :: sWaddr :: sWdata :: sWresp :: sRaddr :: sRdata :: Nil = Enum(6)
    val state = RegInit(sIdle)

    val raddrEn = RegInit(false.B)
    val rdataEn = RegInit(false.B)
    val rdata = RegInit(0.U(DATA_WIDTH.W))
    val rlast = RegInit(false.B)
    val rid     = RegInit(0.U(4.W))
    val waddrEn = RegInit(false.B)
    val wdataEn = RegInit(false.B)
    val bid     = RegInit(0.U(4.W))
    val bEn     = RegInit(false.B)
    switch(state){
        is(sIdle){
            when(io.ctrl_in.arvalid){
                state := sRaddr
                bid := io.ctrl_in.awid
                raddrEn := true.B
            }.elsewhen(io.ctrl_in.awvalid){
                state := sWaddr
                rid := io.ctrl_in.arid
                waddrEn := true.B
            }
        }
        is(sWaddr){
            when(waddrEn && io.ctrl_in.awvalid){
                state := sWdata
                waddrEn := false.B
                wdataEn := true.B
            }
        }
        is(sWdata){
            when(wdataEn && io.ctrl_in.wvalid){
                offset := io.ctrl_in.wdata
                when(io.ctrl_in.wlast){
                    state := sWresp
                    wdataEn := false.B
                    bEn := true.B
                }
            }
        }
        is(sWresp){
            state := sIdle
            bEn := false.B
        }
        is(sRaddr){
            when(raddrEn && io.ctrl_in.arvalid){
                state   := sRdata
                raddrEn := false.B
                rdataEn := true.B
                rdata   := offset
                rlast   := true.B
            }
        }
        is(sRdata){
            when(rdataEn && io.ctrl_in.rready){
                state := sIdle
                rdataEn := false.B
                rlast   := false.B
            }
        }
    }
    
    io.map_out <> io.map_in
    io.map_out.awaddr := io.map_in.awaddr + offset
    io.map_out.araddr := io.map_in.araddr + offset
 
    io.ctrl_in.awready   := waddrEn
    io.ctrl_in.wready    := wdataEn
    io.ctrl_in.bvalid    := bEn
    io.ctrl_in.bresp     := RESP_OKAY
    io.ctrl_in.bid       := bid
    io.ctrl_in.arready   := raddrEn
    io.ctrl_in.rvalid    := rdataEn
    io.ctrl_in.rresp     := RESP_OKAY
    io.ctrl_in.rdata     := rdata
    io.ctrl_in.rlast     := rlast
    io.ctrl_in.rid       := rid
}