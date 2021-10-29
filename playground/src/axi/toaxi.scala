package noop.bus

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.datapath._
import axi._
import axi.axi_config._

class Splite64to32 extends Module{
    val io = IO(new Bundle{
        val data_in = new DcacheRW
        val data_out = Flipped(new DcacheRW)
    })
    val data_buf = RegInit(0.U(32.W))
    val sIdle :: sWait :: Nil = Enum(2)
    val addr_r = RegInit(0.U(PADDR_WIDTH.W))
    val is_64 = RegInit(false.B)
    val busy = RegInit(false.B)
    val state = RegInit(sIdle)
    val hs_out = (io.data_out.dc_mode =/= mode_NOP) && io.data_out.ready
    io.data_out.amo := 0.U;  io.data_out.wdata := 0.U;  io.data_out.dc_mode := mode_NOP;  io.data_out.addr := 0.U
    io.data_in.ready := false.B; io.data_in.rvalid := false.B
    io.data_in.rdata := Mux(is_64, Cat(io.data_out.rdata(31,0), data_buf), io.data_out.rdata)
    switch(state){
        is(sIdle){
                when(io.data_in.dc_mode =/= mode_NOP){
                    busy := true.B
                    io.data_out.addr := Cat(io.data_in.addr(PADDR_WIDTH-1, 3), 0.U(3.W))
                    io.data_out.dc_mode := mode_LWU
                    io.data_in.ready := io.data_out.ready
                    when(hs_out && io.data_out.dc_mode =/= mode_LD){
                        state := sWait
                        addr_r := Cat(io.data_in.addr(PADDR_WIDTH-1, 3), 0.U(3.W))
                        is_64 := true.B
                    }.elsewhen(hs_out){
                        is_64 := false.B
                    }
                }.elsewhen(io.data_in.rvalid){
                    busy := false.B
                }
                when(busy){
                    io.data_in.rvalid := io.data_out.rvalid
                }
            }
        is(sWait){
            when(io.data_out.rvalid){
                data_buf := io.data_out.rdata
            }
            io.data_out.addr := addr_r + 4.U
            io.data_out.dc_mode := mode_LWU
            when(hs_out){
                state := sIdle
            }
        }
    }
}

class ToAXI extends Module{
    val io = IO(new Bundle{
        val dataIO = new DcacheRW
        val outAxi = new AxiMaster
    })

    val waddrEn = RegInit(false.B)
    val waddr   = RegInit(0.U(PADDR_WIDTH.W))
    val wsize   = RegInit(0.U(3.W))
    val offset  = RegInit(0.U(8.W))
    val wlast   = (offset >= 0.U) //指示burst结束
    val wdataEn = RegInit(false.B)
    val wdata   = RegInit(0.U(DATA_WIDTH.W))
    val wstrb   = RegInit(0.U(8.W))

    val rsize   = RegInit(0.U(3.W))
    val raddrEn = RegInit(false.B)
    val raddr   = RegInit(0.U(PADDR_WIDTH.W))
    val rdataEn = RegInit(false.B)
    val rdata   = RegInit(0.U(DATA_WIDTH.W))

    val pre_addr = RegInit(0.U(PADDR_WIDTH.W))
    // 和Mem交互
    val addr    = Wire(UInt(PADDR_WIDTH.W))

    val mode = RegInit(mode_NOP)
    val curAddr     = io.dataIO.addr
    val curWdata    = io.dataIO.wdata
    val curMode     = io.dataIO.dc_mode
    //
    io.dataIO.ready := false.B
    val (sIdle:: sWaddr :: sWdata :: sWresp :: sRaddr :: sRdata :: sFinish :: Nil) = Enum(7)
    val state  = RegInit(sIdle)
    addr := curAddr
    //store
    switch(state){
        is(sIdle){
            mode    := curMode
            when(curMode =/= mode_NOP){
                io.dataIO.ready := true.B
            }
            when(curMode(3) === 1.U){
                state   := sWaddr
                waddr   := curAddr
                // wdata   := curWdata
                waddrEn := true.B
                val wtype   = ListLookup(curMode, List(0.U(3.W), 0.U(8.W)) , Array(
                    BitPat(mode_SB) -> List(0.U(3.W), 0x1.U(8.W)),
                    BitPat(mode_SH) -> List(1.U(3.W), 0x3.U(8.W)),
                    BitPat(mode_SW) -> List(2.U(3.W), 0xf.U(8.W)),
                    BitPat(mode_SD) -> List(3.U(3.W), 0xff.U(8.W))
                ))
                wsize   := wtype(0)
                wstrb   := wtype(1) << curAddr(2, 0)
                wdata   := (curWdata << (curAddr(2, 0)*8.U))(63, 0)
                pre_addr := curAddr
            }.elsewhen(curMode(2) === 1.U){
                state := sRaddr
                rsize := MuxLookup(curMode, 0.U , Array(
                    mode_LB -> (0.U(3.W)),
                    mode_LBU -> (0.U(3.W)),
                    mode_LH -> (1.U(3.W)),
                    mode_LHU -> (1.U(3.W)),
                    mode_LW -> (2.U(3.W)),
                    mode_LWU -> (2.U(3.W)),
                    mode_LD -> (3.U(3.W))
                ))

                // raddr := Cat(curAddr(31, 8), 0.U(8.W))
                raddr := curAddr
                raddrEn := true.B
                pre_addr := curAddr
            }
        }
        // write
        is(sWaddr){
            when(waddrEn && io.outAxi.wa.ready){ //等待ready
                waddrEn := false.B
                offset  := 0.U
                state   := sWdata
            }
        }
        is(sWdata){
            wdataEn := true.B
            when(io.outAxi.wd.ready){
                offset  := offset + 1.U
                when(wlast){
                    state   := sWresp
                }
            }
        }
        is(sWresp){
            wdataEn := false.B
            state   := sIdle
        }
        //read
        is(sRaddr){
            when(raddrEn && io.outAxi.ra.ready){
                raddrEn := false.B
                offset  := 0.U
                state   := sRdata
            }
        }
        is(sRdata){
            rdataEn := true.B

            when(rdataEn && io.outAxi.rd.valid){
                val tem_rdata = Wire(SInt(DATA_WIDTH.W))
                tem_rdata   := 0.S
                val strb_offset = pre_addr(2, 0)
                switch(mode){
                    is(mode_LB){
                        tem_rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(7, 0).asSInt
                        rdata       := tem_rdata.asUInt
                    }
                    is(mode_LH){
                        tem_rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(15, 0).asSInt
                        rdata       := tem_rdata.asUInt
                    }
                    is(mode_LW){
                        tem_rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(31, 0).asSInt
                        rdata       := tem_rdata.asUInt
                    }
                    is(mode_LD){
                        rdata       := io.outAxi.rd.bits.data
                    }
                    is(mode_LBU){
                        rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(7, 0)
                    }
                    is(mode_LHU){
                        rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(15, 0)
                    }
                    is(mode_LWU){
                        rdata   := (io.outAxi.rd.bits.data >> (8.U * strb_offset))(31, 0)
                    }
                }
                offset := offset + 1.U

                rdataEn := false.B
                state   := sFinish
            }
        }
        is(sFinish){
            state := sIdle
        }
    }
    io.dataIO.ready := (state === sIdle)
    // io.stall := state =/= sIdle
    val out_rdata = RegInit(0.U(DATA_WIDTH.W))
    val out_valid = RegInit(false.B)
    out_valid := (state === sFinish) || (state === sWresp)
    out_rdata := rdata
    io.dataIO.rvalid    := out_valid
    io.dataIO.rdata     := out_rdata

    io.outAxi.init()
    //wa
    io.outAxi.wa.valid        := waddrEn
    io.outAxi.wa.bits.addr    := waddr
    io.outAxi.wa.bits.len     := 0.U
    io.outAxi.wa.bits.size    := wsize
    io.outAxi.wa.bits.burst   := BURST_INCR
    //wd
    io.outAxi.wd.valid        := wdataEn
    io.outAxi.wd.bits.data    := wdata
    io.outAxi.wd.bits.strb    := wstrb
    io.outAxi.wd.bits.last    := wlast
    //wr
    io.outAxi.wr.ready        := true.B
    //ra
    io.outAxi.ra.valid        := raddrEn
    io.outAxi.ra.bits.addr    := raddr
    io.outAxi.ra.bits.len     := 0.U
    io.outAxi.ra.bits.size    := rsize
    io.outAxi.ra.bits.burst   := BURST_INCR
    //rd
    io.outAxi.rd.ready        := rdataEn

    dontTouch(io.outAxi)
}