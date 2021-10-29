package noop.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._
import axi._
import ram._
import axi.axi_config._

class IcacheCrossBlock extends Module{
    val io = IO(new Bundle{
        val icIn    = new IcacheRead
        val icOut   = Flipped(new IcacheRead)
    })
    val sIdle :: sCross :: Nil = Enum(2)
    val state       = RegInit(sIdle)
    val addr_r      = RegInit(0.U(VADDR_WIDTH.W))
    val inst_first_r = RegInit(0.U(INST_WIDTH.W))
    val cross_r     = RegInit(false.B)
    val hs_out      = io.icOut.arvalid && io.icOut.ready
    val hs_in       = io.icIn.arvalid && io.icIn.ready
    io.icIn.inst := 0.U; io.icIn.ready := false.B; io.icIn.rvalid := false.B
    io.icOut.addr := 0.U; io.icOut.arvalid := false.B
    when(hs_in){
        cross_r := false.B
    }
    switch(state){
        is(sIdle){
            io.icOut.addr       := io.icIn.addr & Cat(Fill(VADDR_WIDTH-2, 1.U(1.W)), 0.U(2.W))
            io.icOut.arvalid    := io.icIn.arvalid
            io.icIn.ready       := io.icOut.ready
            io.icIn.rvalid      := io.icOut.rvalid
            io.icIn.inst        := Mux(cross_r, Cat(io.icOut.inst(15,0), inst_first_r(31,16)), io.icOut.inst)
            when((io.icIn.addr(1,0) =/= 0.U) && hs_out){
                state   := sCross
                addr_r  := io.icIn.addr + 2.U   // next addr
                cross_r := true.B
            }
        }
        is(sCross){
            io.icOut.arvalid    := true.B
            io.icOut.addr       := addr_r
            when(io.icOut.rvalid){
                inst_first_r := io.icOut.inst
            }
            when(hs_out){
                state := sIdle
            }
        }
    }
}


class InstCache extends Module{
    val io = IO(new Bundle{
        val instAxi     = new AxiMaster
        val icRead      = new IcacheRead
        val flush       = Input(Bool())
    })

    val tag     = RegInit(VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(IC_BLOCK_NUM)(0.U(IC_TAG_WIDTH.W))))))
    val valid   = RegInit(VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(IC_BLOCK_NUM)(false.B)))))
    val data    = VecInit(Seq.fill(CACHE_WAY_NUM)(Module(new Ram_bw).io))
    for(i <- 0 until CACHE_WAY_NUM){
        data(i).init()
    }
    val wait_r      = RegInit(false.B) // cache miss
    val valid_r     = RegInit(false.B)
    valid_r := false.B
    val valid_in    = io.icRead.arvalid && !io.flush
    val hs_in       = io.icRead.ready && io.icRead.arvalid
    io.icRead.ready     := valid_in && !wait_r
    io.icRead.rvalid    := valid_r
    val addr_r          = RegInit(0.U(PADDR_WIDTH.W))
    val matchWay_r      = RegInit(0.U(PADDR_WIDTH.W))
    val axiOffset       = RegInit(0.U(3.W))
    val databuf         = RegInit(0.U(DATA_WIDTH.W))
    val cur_addr        = Mux(hs_in, io.icRead.addr, addr_r)
    val instTag         = cur_addr(PADDR_WIDTH-1, IC_BLOCK_WIDTH+IC_INDEX_WIDTH)
    val blockIdx        = cur_addr(IC_INDEX_WIDTH+IC_BLOCK_WIDTH-1, IC_BLOCK_WIDTH)
    val cur_ram_addr    = cur_addr(RAM_ADDR_WIDTH+RAM_WIDTH_BIT-1, RAM_WIDTH_BIT)
    val cur_raddr       = Cat(cur_addr(RAM_ADDR_WIDTH + RAM_WIDTH_BIT-1, IC_BLOCK_WIDTH), axiOffset(2,1))
    val cache_hit_vec   = VecInit((0 until CACHE_WAY_NUM).map(i => tag(i)(blockIdx) === instTag && valid(i)(blockIdx)))
    val cacheHit        = cache_hit_vec.asUInt().orR
    val matchWay        = Mux(cacheHit, OHToUInt(cache_hit_vec), LFSR(2))
    val cur_way         = Mux(hs_in, matchWay, matchWay_r)
    val pre_blockIdx    = addr_r(IC_INDEX_WIDTH+IC_BLOCK_WIDTH-1, IC_BLOCK_WIDTH)
    val pre_instTag     = addr_r(PADDR_WIDTH-1, IC_BLOCK_WIDTH+IC_INDEX_WIDTH)

    when(hs_in){
        addr_r := io.icRead.addr
        matchWay_r := matchWay
    }
    when(io.flush){
        valid   := VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(IC_BLOCK_NUM)(false.B))))
    }
    val sIdle :: sRaddr :: sRdata :: Nil = Enum(3)
    val state = RegInit(sIdle)

    val rdata128 = data(matchWay_r).rdata
    io.icRead.inst := MuxLookup(addr_r(3), 0.U, Seq( // can also be implemented using shift opeartion
        0.U     -> rdata128(63, 0),
        1.U     -> rdata128(127, 64)
    ))
    val wen     = Wire(Bool())
    data(cur_way).addr  := Mux(state === sRdata, cur_raddr, cur_ram_addr)
    data(cur_way).cen   := wait_r || hs_in
    data(cur_way).wen   := wen
    data(cur_way).wdata := Cat(io.instAxi.rd.bits.data, databuf)
    data(cur_way).mask  := Fill(RAM_MASK_WIDTH, 1.U(1.W))
    wen     := false.B
// axi signal
    val raddrEn     = RegInit(false.B)
    val raddr       = RegInit(0.U(PADDR_WIDTH.W))
    val rdataEn     = RegInit(false.B)

    switch(state){
        is(sIdle){
            when(!hs_in && !wait_r){

            }.elsewhen(cacheHit){
                valid_r := true.B
                wait_r  := false.B
            }.otherwise{
                raddr   := cur_addr & IC_BLOCK_MASK
                raddrEn := true.B
                state   := sRaddr
                valid_r := false.B
                wait_r  := true.B
            }
        }
        is(sRaddr){
            when(raddrEn && io.instAxi.ra.ready){
                state   := sRdata
                raddrEn := false.B
                rdataEn := true.B
                axiOffset := 0.U
            }
        }
        is(sRdata){
            when(rdataEn && io.instAxi.rd.valid){
                axiOffset := axiOffset + 1.U
                when(axiOffset(0)){
                    wen := true.B
                }.otherwise{
                    databuf := io.instAxi.rd.bits.data
                }
                when(io.instAxi.rd.bits.last){
                    rdataEn := false.B
                    tag(matchWay_r)(pre_blockIdx) := pre_instTag
                    valid(matchWay_r)(pre_blockIdx) := true.B
                    state := sIdle
                    axiOffset := 0.U
                }
            }
        }
    }

    io.instAxi.init()
    //ra
    io.instAxi.ra.valid      := raddrEn
    io.instAxi.ra.bits.addr  := raddr
    io.instAxi.ra.bits.len   := 7.U
    io.instAxi.ra.bits.size  := 3.U  // 8B
    io.instAxi.ra.bits.burst := BURST_INCR
    //rd
    io.instAxi.rd.ready      := true.B
    //wa
    io.instAxi.wa.valid      := 0.U
    io.instAxi.wa.bits.addr  := 0.U
    io.instAxi.wa.bits.len   := 0.U
    io.instAxi.wa.bits.size  := 0.U
    io.instAxi.wa.bits.burst := 0.U
    //wd
    io.instAxi.wd.valid      := 0.U
    io.instAxi.wd.bits.data  := 0.U
    io.instAxi.wd.bits.strb  := 0.U
    io.instAxi.wd.bits.last  := 0.U
    //wr
    io.instAxi.wr.ready      := true.B
}