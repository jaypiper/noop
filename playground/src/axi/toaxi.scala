package noop.bus

import chisel3._
import chisel3.util._
import noop.param.common._
import noop.param.cache_config._
import noop.datapath._
import axi._
import axi.axi_config._

class RemoveReqCancel extends Module {
    val io = IO(new Bundle {
        val in = new DcacheRW
        val out = Flipped(new DcacheRW)
    })

    val req_valid = RegInit(false.B)
    val is_cancelled = RegNext(io.in.req.fire) && io.in.req_cancel
    val req_bits = RegEnable(io.in.req.bits, io.in.req.fire)
    when (io.in.req.fire) {
        req_valid := true.B
        printf(p"req_in: ${Hexadecimal(io.in.req.bits.addr)}\n")
    }.elsewhen (is_cancelled || io.out.req.ready) {
        req_valid := false.B
        when (is_cancelled) {
            printf(p"cancelled: ${Hexadecimal(req_bits.addr)}\n")
        }
    }

    io.out.req.valid := req_valid && !is_cancelled
    io.out.req.bits := req_bits
    io.out.req_cancel := false.B
    io.in.req.ready := !req_valid || io.out.req.ready

    io.in.resp := io.out.resp
    when(io.out.req.fire) {
        printf(p"out req: ${Hexadecimal(io.out.req.bits.addr)}\n")
    }
    when(is_cancelled) {
        printf(p"cancelled: ${Hexadecimal(req_bits.addr)}\n")
    }
    when (io.in.resp.valid) {
        printf(p"resp: ${Hexadecimal(io.in.resp.bits)}\n")
    }
}

object RemoveReqCancel {
    def apply(in: DcacheRW): DcacheRW = {
        val remover = Module(new RemoveReqCancel)
        remover.io.in <> in
        remover.io.out
    }
}

class ToAXI(with_req_cancel: Boolean) extends Module{
    val io = IO(new Bundle{
        val dataIO = new DcacheRW
        val outAxi = new AxiMaster
    })

    val in = if (with_req_cancel) {
        RemoveReqCancel(io.dataIO)
    } else {
        assert(!RegNext(io.dataIO.req.fire, false.B) || !io.dataIO.req_cancel)
        io.dataIO
    }

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

    in.req.ready := !valid_r
    val (sIdle:: sWaddr :: sWdata :: sWresp :: sRaddr :: sRdata :: sFinish :: Nil) = Enum(7)
    val state  = RegInit(sIdle)

    when(in.req.fire) {
        valid_r := true.B
        in_addr_r := in.req.bits.addr
        in_rdata_r := in.resp.bits
        in_wdata_r := in.req.bits.wdata
        in_wen_r := in.req.bits.wen
        in_wmask_r := in.req.bits.wmask
        in_size_r := in.req.bits.size
    }
    //store
    switch(state){
        is(sIdle){
            when(valid_r && in_wen_r){
                state   := sWaddr
                waddrEn := true.B
                wstrb := MuxLookup(in_size_r, 0.U, List(
                    (0.U -> VecInit((0 to 7).map(i => (1 << i).U))(in_addr_r)), // sb
                    (1.U -> VecInit((0 to 7).map(i => (3 << i).U))(in_addr_r)), //sh
                    (2.U -> VecInit((0 to 7).map(i => (0xf << i).U))(in_addr_r)), // sw
                    (3.U -> VecInit((0 to 7).map(i => (0xff << i).U))(in_addr_r))
                ))
                wdata := MuxLookup(in_size_r, 0.U, List(
                    (0.U -> Fill(8, in_wdata_r(7,0))),
                    (1.U -> Fill(4, in_wdata_r(15,0))),
                    (2.U -> Fill(2, in_wdata_r(31,0))),
                    (3.U -> in_wdata_r)		
                ))
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
                rdataEn := true.B
            }
        }
        is(sRdata){
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
    in.resp.valid := out_valid
    in.resp.bits := out_rdata

    io.outAxi.initMaster()
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
}