package noop.bus

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.datapath._
import axi._
import axi.axi_config._

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

    // val mode = RegInit(mode_NOP)
    val curAddr     = io.dataIO.addr
    val curWdata    = io.dataIO.wdata
    //
    io.dataIO.ready := false.B
    val (sIdle:: sWaddr :: sWdata :: sWresp :: sRaddr :: sRdata :: sFinish :: Nil) = Enum(7)
    val state  = RegInit(sIdle)
    addr := curAddr
    //store
    switch(state){
        is(sIdle){
            when(io.dataIO.avalid){
                io.dataIO.ready := true.B
            }
            when(io.dataIO.avalid && io.dataIO.wen){
                state   := sWaddr
                waddr   := curAddr
                // wdata   := curWdata
                waddrEn := true.B
                val lowMask = io.dataIO.wmask >> (Cat(curAddr(ICACHE_OFFEST_WIDTH-1,0), 0.U(3.W)))
                when(lowMask === "hff".U) {
                    wsize := 0.U
                    wstrb := 1.U << curAddr(2, 0)
                }.elsewhen(lowMask === "hffff".U) {
                    wsize := 1.U
                    wstrb := 0x3.U << curAddr(2, 0)
                }.elsewhen(lowMask === "hffffffff".U) {
                    wsize := 2.U
                    wstrb := 0xf.U << curAddr(2, 0)
                }.elsewhen(lowMask === "hffffffffffffffff".U) {
                    wsize := 3.U
                    wstrb := 0xff.U << curAddr(2, 0)
                }
                wdata   := (curWdata << (curAddr(2, 0)*8.U))(63, 0)
                pre_addr := curAddr
            }.elsewhen(io.dataIO.avalid){
                state := sRaddr
                rsize := MuxLookup(io.dataIO.wmask >> Cat(curAddr(ICACHE_OFFEST_WIDTH-1,0), 0.U(3.W)), 0.U , Seq(
                    "hff".U(DATA_WIDTH) -> (0.U(3.W)),
                    "hffff".U(DATA_WIDTH) -> (1.U(3.W)),
                    "hffffffff".U(DATA_WIDTH) -> (2.U(3.W)),
                    "hffffffffffffffff".U(DATA_WIDTH) -> (3.U(3.W))
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
                val strb_offset = pre_addr(2, 0)
                rdata       := io.outAxi.rd.bits.data
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