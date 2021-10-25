package noop.cache

import chisel3._
import chisel3.util._
import chisel3.util.random._
import noop.param.common._
import noop.param.cache_config._
import noop.param.decode_config._
import noop.datapath._
import axi._
import axi.axi_config._
import ram._

class DcacheSelector extends Module{
    val io = IO(new Bundle{
        val tlb_if2dc   = new DcacheRW
        val tlb_mem2dc  = new DcacheRW
        val mem2dc      = new DcacheRW
        val select      = Flipped(new DcacheRW)
    })
    io.tlb_if2dc.rdata      := io.select.rdata
    io.tlb_if2dc.rvalid     := false.B;         io.tlb_if2dc.ready  := false.B
    io.tlb_mem2dc.rdata     := io.select.rdata
    io.tlb_mem2dc.rvalid    := false.B;         io.tlb_mem2dc.ready := false.B
    io.mem2dc.rdata         := io.select.rdata
    io.mem2dc.rvalid        := false.B;         io.mem2dc.ready     := false.B
    val pre_idx = RegInit(0.U(2.W))
    val busy    = RegInit(false.B)
    io.select.addr      := 0.U
    io.select.wdata     := 0.U
    io.select.dc_mode   := 0.U
    io.mem2dc.ready     := 0.U
    when(busy && !io.select.rvalid){
    }.elsewhen(io.mem2dc.dc_mode =/= mode_NOP){
        pre_idx := 0.U
        busy    := true.B
        io.select.addr      := io.mem2dc.addr
        io.select.wdata     := io.mem2dc.wdata
        io.select.dc_mode   := io.mem2dc.dc_mode
        io.mem2dc.ready     := io.select.ready
    }.elsewhen(io.tlb_mem2dc.dc_mode =/= mode_NOP){
        pre_idx := 1.U
        busy    := true.B
        io.select.addr      := io.tlb_mem2dc.addr
        io.select.wdata     := io.tlb_mem2dc.wdata
        io.select.dc_mode   := io.tlb_mem2dc.dc_mode
        io.tlb_mem2dc.ready := io.select.ready
    }.elsewhen(io.tlb_if2dc.dc_mode =/= mode_NOP){
        pre_idx :=2.U
        busy    := true.B
        io.select.addr      := io.tlb_if2dc.addr
        io.select.wdata     := io.tlb_if2dc.wdata
        io.select.dc_mode   := io.tlb_if2dc.dc_mode
        io.tlb_if2dc.ready  := io.select.ready
    }
    when(io.select.rvalid){
        busy := false.B
    }
    io.mem2dc.rvalid        := io.select.rvalid && pre_idx === 0.U
    io.tlb_mem2dc.rvalid    := io.select.rvalid && pre_idx === 1.U
    io.tlb_if2dc.rvalid     := io.select.rvalid && pre_idx === 2.U

}


class DataCache extends Module{
    val io = IO(new Bundle{
        val dataAxi     = new AxiMaster
        val dcRW        = new DcacheRW
        val flush       = Input(Bool())
    })

    val tag     = RegInit(VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(DC_BLOCK_NUM)(0.U(DC_TAG_WIDTH.W))))))
    val valid   = RegInit(VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(DC_BLOCK_NUM)(false.B)))))
    val dirty   = RegInit(VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(DC_BLOCK_NUM)(false.B)))))
    val data    = VecInit(Seq.fill(CACHE_WAY_NUM)(Module(new Ram_bw).io))
    for(i <- 0 until CACHE_WAY_NUM){
        data(i).init()
    }
    val wait_r      = RegInit(false.B) // cache miss
    val valid_r     = RegInit(false.B)
    val mode_r      = RegInit(0.U(DC_MODE_WIDTH.W))
    val wdata_r     = RegInit(0.U(DATA_WIDTH.W))
    valid_r := false.B
    val valid_in    = (io.dcRW.dc_mode =/= mode_NOP) && !io.flush
    val hs_in       = io.dcRW.dc_mode =/= mode_NOP && io.dcRW.ready
    io.dcRW.ready := valid_in && !wait_r
    io.dcRW.rvalid := valid_r
    val addr_r          = RegInit(0.U(PADDR_WIDTH.W))
    val cur_addr        = Mux(hs_in, io.dcRW.addr, addr_r)
    val matchWay_r      = RegInit(0.U(2.W))
    val offset          = RegInit(0.U(3.W))
    val rdatabuf        = RegInit(0.U(DATA_WIDTH.W))
    val blockIdx        = cur_addr(DC_INDEX_WIDTH+DC_BLOCK_WIDTH-1, DC_BLOCK_WIDTH)
    val cur_tag         = cur_addr(PADDR_WIDTH-1, DC_BLOCK_WIDTH+DC_INDEX_WIDTH)
    val cache_hit_vec   = VecInit((0 until CACHE_WAY_NUM).map(i => tag(i)(blockIdx) === cur_tag && valid(i)(blockIdx)))
    val cacheHit        = cache_hit_vec.asUInt().orR
    val matchWay        = Mux(cacheHit, OHToUInt(cache_hit_vec), Mux(hs_in, LFSR(2), matchWay_r))

    val is_dirty        = dirty(matchWay)(blockIdx)  // can get dirty bit concurrently with cacheHit, matchWay
    when(hs_in){
        addr_r := io.dcRW.addr
        matchWay_r := matchWay
        mode_r  := io.dcRW.dc_mode
        wdata_r := io.dcRW.wdata
    }
    when(io.flush){
        valid   := VecInit(Seq.fill(CACHE_WAY_NUM)(VecInit(Seq.fill(DC_BLOCK_NUM)(false.B))))
    }
    val cur_way         = Mux(hs_in, matchWay, matchWay_r)
    val cur_ram_addr    = cur_addr(RAM_ADDR_WIDTH+RAM_WIDTH_BIT-1, RAM_WIDTH_BIT)
    val cur_axi_addr    = Cat(cur_addr(RAM_ADDR_WIDTH + RAM_WIDTH_BIT-1, DC_BLOCK_WIDTH), offset(2,1))
    val cur_mode        = Mux(hs_in, io.dcRW.dc_mode, mode_r)
    val cur_wdata       = Mux(hs_in, io.dcRW.wdata, wdata_r)
    val pre_blockIdx    = addr_r(DC_INDEX_WIDTH+DC_BLOCK_WIDTH-1, DC_BLOCK_WIDTH)
    val pre_tag     = addr_r(PADDR_WIDTH-1, DC_BLOCK_WIDTH+DC_INDEX_WIDTH)
    val sIdle :: sRaddr :: sRdata :: sWaddr :: sWdata :: sAtomic :: Nil = Enum(6)
    val state = RegInit(sIdle)
    val rdata64 = data(matchWay_r).rdata >> Cat(addr_r(RAM_WIDTH_BIT-1, 0), 0.U(3.W))
    io.dcRW.rdata := rdata_by_mode(mode_r, rdata64)
    val cur_mode_sl = cur_mode(3,2)
    val cur_mode_s  = cur_mode(DC_S_BIT)
    val cur_mode_l  = cur_mode(DC_L_BIT)
    val wen     = Wire(Bool())
    val mask    = Wire(UInt(RAM_MASK_WIDTH.W))
    // val wdata   = Wire(UInt(DATA_WIDTH.W))
    // val atomic_write    = Wire(UInt(DATA_WIDTH.W))
// atomic
    val pre_amo = RegInit(0.U(AMO_WIDTH.W))  // TODO
    val amo_rdata = signTruncateData(mode_r(1,0), rdata64)
    val amo_imm = signTruncateData(mode_r(1,0), wdata_r)
    val amo_alu = MuxLookup(pre_amo, 0.U(DATA_WIDTH.W), Seq(
        amoSwap -> (amo_imm),
        amoAdd  -> (amo_imm + amo_rdata),
        amoXor  -> (amo_imm ^ amo_rdata),
        amoAnd  -> (amo_imm & amo_rdata),
        amoOr   -> (amo_imm | amo_rdata),
        amoMin  -> Mux(amo_imm.asSInt > amo_rdata.asSInt, amo_rdata, amo_imm),
        amoMax  -> Mux(amo_imm.asSInt > amo_rdata.asSInt, amo_imm, amo_rdata),
        amoMinU -> Mux(amo_imm > amo_rdata, amo_rdata, amo_imm),
        amoMaxU -> Mux(amo_imm > amo_rdata, amo_imm, amo_rdata)
    ))
    val amo_wdata = zeroTruncateData(mode_r(1,0), amo_alu)

     
    // wd_mask := Ignore(wtype(0) << (Cat(cur_addr(RAM_WIDTH_BIT-1, 0), 0.U(3.W))))
    val inp_wdata   = cur_wdata<<(Cat(cur_addr(RAM_WIDTH_BIT-1, 0), 0.U(3.W)))
    val inp_mask    = MuxLookup(cur_mode(1,0), 0.U(RAM_MASK_WIDTH.W), Seq(
                        0.U   ->"hff".U(RAM_MASK_WIDTH.W),
                        1.U   ->"hffff".U(RAM_MASK_WIDTH.W),
                        2.U   ->"hffffffff".U(RAM_MASK_WIDTH.W), 
                        3.U   ->"hffffffffffffffff".U(RAM_MASK_WIDTH.W), 
                    )) << (Cat(cur_addr(RAM_WIDTH_BIT-1, 0), 0.U(3.W)))
    data(cur_way).addr  := Mux(state === sIdle || state === sAtomic, cur_ram_addr, cur_axi_addr)
    data(cur_way).cen   := wait_r || hs_in
    data(cur_way).wen   := wen
    data(cur_way).wdata := Mux(state === sAtomic, amo_wdata, 
                            Mux(state === sIdle, inp_wdata, Cat(io.dataAxi.rd.bits.data, rdatabuf)))
    data(cur_way).mask  := mask
    wen     := false.B
    mask    := Fill(RAM_MASK_WIDTH, 1.U(1.W))
    when(wen && state === sIdle){
        dirty(cur_way)(blockIdx) := true.B
    }
// axi signal
    val axiRaddrEn      = RegInit(false.B)
    val axiRaddr        = cur_addr & DC_BLOCK_MASK
    val axiRdataEn      = RegInit(false.B)
    val axiWaddrEn      = RegInit(false.B)
    val axiWaddr        = Cat(tag(matchWay_r)(blockIdx), blockIdx, 0.U(DC_BLOCK_WIDTH.W))
    val axiWdata        = Mux(offset(0), data(matchWay_r).rdata(127,64), data(matchWay_r).rdata(63,0))
    val axiWdataEn      = RegInit(false.B)
    val axiWdataLast    = offset === 7.U

    switch(state){
        is(sIdle){
            when(!hs_in && !wait_r){

            }.elsewhen(cacheHit){
                when(cur_mode_sl === 3.U){
                    state := sAtomic
                    valid_r := false.B
                    wait_r := true.B
                }.otherwise{
                    wen := cur_mode(DC_S_BIT)
                    mask := inp_mask
                    valid_r := true.B
                    wait_r  := false.B
                }
            }.otherwise{
                when(is_dirty){
                    state := sWaddr
                    axiWaddrEn := true.B
                }.otherwise{
                    state := sRaddr
                    axiRaddrEn := true.B
                }
                valid_r := false.B
                wait_r  := true.B
            }
        }
        is(sRaddr){
            offset := 0.U
            when(axiRaddrEn && io.dataAxi.ra.ready){
                state   := sRdata
                axiRaddrEn := false.B
                axiRdataEn := true.B
            }
        }
        is(sRdata){
            when(axiRdataEn && io.dataAxi.rd.valid){
                offset := offset + 1.U
                when(offset(0)){
                    wen := true.B
                }.otherwise{
                    rdatabuf := io.dataAxi.rd.bits.data
                }
                when(io.dataAxi.rd.bits.last){
                    axiRdataEn := false.B
                    tag(matchWay_r)(pre_blockIdx) := pre_tag
                    valid(matchWay_r)(pre_blockIdx) := true.B
                    state := sIdle
                }
            }
        }
        is(sWaddr){
            offset := 0.U
            when(axiWaddrEn && io.dataAxi.wa.ready){
                state       := sWdata
                axiWaddrEn  := false.B
                axiWdataEn  := true.B
            }
        }
        is(sWdata){
            axiWdataEn := true.B
            when(axiWdataEn && io.dataAxi.wd.ready){
                offset := offset + 1.U
                axiWdataEn := false.B
                when(io.dataAxi.wd.bits.last){
                    state := sIdle
                    axiWdataEn := false.B
                    valid(matchWay_r)(pre_blockIdx) := false.B
                    dirty(matchWay_r)(pre_blockIdx) := false.B
                }
            }
        }
        is(sAtomic){
            wen     := true.B
            wait_r  := false.B
        }
    }
    io.dataAxi.init()
    //ra
    io.dataAxi.ra.valid      := axiRaddrEn
    io.dataAxi.ra.bits.addr  := axiRaddr
    io.dataAxi.ra.bits.len   := 7.U
    io.dataAxi.ra.bits.size  := 3.U
    io.dataAxi.ra.bits.burst := BURST_INCR
    //rd
    io.dataAxi.rd.ready      := true.B
    //wa
    io.dataAxi.wa.valid      := axiWaddrEn
    io.dataAxi.wa.bits.addr  := axiWaddr
    io.dataAxi.wa.bits.len   := 7.U
    io.dataAxi.wa.bits.size  := 3.U
    io.dataAxi.wa.bits.burst := BURST_INCR
    //wd
    io.dataAxi.wd.valid      := axiWdataEn
    io.dataAxi.wd.bits.data  := axiWdata
    io.dataAxi.wd.bits.strb  := 0xff.U
    io.dataAxi.wd.bits.last  := axiWdataLast
    //wr
    io.dataAxi.wr.ready      := true.B
    dontTouch(io.dataAxi)
}