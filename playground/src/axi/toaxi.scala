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

    val valid_r = RegInit(false.B)
    val in_addr_r   = RegInit(0.U(PADDR_WIDTH.W))
    val in_rdata_r  = RegInit(0.U(PADDR_WIDTH.W))
    val in_wdata_r  = RegInit(0.U(DATA_WIDTH.W))
    val in_wen_r    = RegInit(false.B)
    val in_wmask_r  = RegInit(0.U(DATA_WIDTH.W))
    val in_size_r   = RegInit(0.U(3.W))

    val waddrEn = RegInit(false.B)
    val offset  = RegInit(0.U(8.W))
    val wlast   = (offset >= 0.U) //指示burst结束
    val wdataEn = RegInit(false.B)
    val wdata   = RegInit(0.U(DATA_WIDTH.W))
    val wstrb   = RegInit(0.U(8.W))

    val raddrEn = RegInit(false.B)
    val rdataEn = RegInit(false.B)
    val rdata   = RegInit(0.U(DATA_WIDTH.W))

    io.dataIO.ready := !valid_r
    val (sIdle:: sWaddr :: sWdata :: sWresp :: sRaddr :: sRdata :: sFinish :: Nil) = Enum(7)
    val state  = RegInit(sIdle)

    when(io.dataIO.ready && io.dataIO.avalid) {
        valid_r := true.B
        in_addr_r := io.dataIO.addr
        in_rdata_r := io.dataIO.rdata
        in_wdata_r := io.dataIO.wdata
        in_wen_r := io.dataIO.wen
        in_wmask_r := io.dataIO.wmask
        in_size_r := io.dataIO.size
    }
    //store
    switch(state){
        is(sIdle){
            when(valid_r && in_wen_r){
                state   := sWaddr
                waddrEn := true.B
                val lowMask = io.dataIO.wmask >> (Cat(in_addr_r(ICACHE_OFFEST_WIDTH-1,0), 0.U(3.W)))
                when(in_size_r === 0.U) {
                    wstrb := 1.U << in_addr_r(2, 0)
                }.elsewhen(lowMask === 1.U) {
                    wstrb := 0x3.U << in_addr_r(2, 0)
                }.elsewhen(lowMask === 2.U) {
                    wstrb := 0xf.U << in_addr_r(2, 0)
                }.elsewhen(lowMask === 3.U) {
                    wstrb := 0xff.U << in_addr_r(2, 0)
                }
                wdata   := (in_wdata_r << (Cat(in_addr_r(2, 0), 0.U(3.W))))(63, 0)
            }.elsewhen(valid_r){
                state := sRaddr
                raddrEn := true.B
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
            valid_r := false.B
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
                when (in_size_r === 0.U) {
                    rdata       := io.outAxi.rd.bits.data.asTypeOf(Vec(8, UInt(8.W)))(in_addr_r(2,0)).asUInt
                }.elsewhen (in_size_r === 1.U) {
                    rdata       := io.outAxi.rd.bits.data.asTypeOf(Vec(4, UInt(16.W)))(in_addr_r(2,1)).asUInt
                }.elsewhen (in_size_r === 2.U) {
                    rdata       := io.outAxi.rd.bits.data.asTypeOf(Vec(2, UInt(32.W)))(in_addr_r(2)).asUInt
                }.elsewhen (in_size_r === 3.U) {
                    rdata       := io.outAxi.rd.bits.data
                }
                offset := offset + 1.U

                rdataEn := false.B
                state   := sFinish
            }
        }
        is(sFinish){
            state := sIdle
            valid_r := false.B
        }
    }
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
    io.outAxi.wa.bits.addr    := in_addr_r
    io.outAxi.wa.bits.len     := 0.U
    io.outAxi.wa.bits.size    := in_size_r
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
    io.outAxi.ra.bits.addr    := in_addr_r
    io.outAxi.ra.bits.len     := 0.U
    io.outAxi.ra.bits.size    := in_size_r
    io.outAxi.ra.bits.burst   := BURST_INCR
    //rd
    io.outAxi.rd.ready        := rdataEn

    dontTouch(io.outAxi)
}