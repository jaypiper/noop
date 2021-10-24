package axi

import chisel3._
import chisel3.util._

import noop.param.common._
import axi.axi_config._

object axi_config{ 
    val BURST_FIXED = 0.U(2.W) // address is the same for every transfer in the burst
    val BURST_INCR  = 1.U(2.W) // increment value depends on the size of the transfer
    val BURST_WRAP  = 2.U(2.W)

    val RESP_OKAY   = 0.U(2.W)
    val RESP_EXOKAY = 1.U(2.W)
    val RESP_SLVERR = 2.U(2.W)
    val RESP_DECERR = 3.U(2.W)

    val DEFAULT_STRB    = 0xff.U
}

/* dependency:
a write response must always follow the last write transfer in the write transaction of which it is a part
read data must always follow the address to which the data relates
*/

class AxiAddr(val idWidth: Int, val userWidth: Int) 
        extends Bundle{
    val id      = UInt(idWidth.W)
    val addr    = UInt(PADDR_WIDTH.W)
    val len     = UInt(8.W)             //burstlen - 1
    val size    = UInt(3.W)             // bytes in transfer 2^size
    val burst   = UInt(2.W)
    //optional
    // val lock    = Bool()
    // val cache   = UInt(4.W)
    // val prot    = UInt(3.W)
    // val qos     = UInt(4.W)
    // val region  = UInt(4.W)
    // val user    = UInt(userWidth.W)
    def init() = this := 0.U.asTypeOf(this)
    
}

class AxiWriteData(val userWidth: Int) extends Bundle{
    val data    = UInt(DATA_WIDTH.W)
    val strb    = UInt(8.W)
    val last    = Bool()
    // val user    = UInt(userWidth.W)
    def init() = this := 0.U.asTypeOf(this)
}


class AxiWriteResp(val idWidth: Int, val userWidth: Int) extends Bundle{
    val id      = UInt(idWidth.W)
    val resp    = UInt(2.W)
    // val user    = UInt(userWidth.W)
    def init() = this := 0.U.asTypeOf(this)
}


class AxiReadData(val idWidth: Int, val userWidth: Int) extends Bundle{
    val id      = UInt(idWidth.W)
    val data    = UInt(DATA_WIDTH.W)
    val resp    = UInt(2.W)
    val last    = Bool()
    // val user    = UInt(userWidth.W)
    def init() = this := 0.U.asTypeOf(this)
}


class AxiMaster extends Bundle{
    val wa = Decoupled(new AxiAddr(4, 1))
    val wd = Decoupled(new AxiWriteData(1))
    val wr = Flipped(Decoupled(new AxiWriteResp(4, 1)))
    val ra = Decoupled(new AxiAddr(4, 1))
    val rd = Flipped(Decoupled(new AxiReadData(4, 1)))

    def init() = {
        wa.bits.init()
        wd.bits.init()
        ra.bits.init()
        wa.valid := false.B
        wd.valid := false.B
        wr.ready := false.B
        ra.valid := false.B 
        rd.ready := false.B
    }
}

class AxiSlave extends Bundle{
    val wa = Flipped(Decoupled(new AxiAddr(4, 1)))
    val wd = Flipped(Decoupled(new AxiWriteData(1)))
    val wr = Decoupled(new AxiWriteResp(4, 1))
    val ra = Flipped(Decoupled(new AxiAddr(4, 1)))
    val rd = Decoupled(new AxiReadData(4, 1))

    def init() = {
        wr.bits.init()
        rd.bits.init()
        wa.ready := false.B
        wd.ready := false.B
        wr.valid := false.B
        ra.ready := false.B
        rd.valid := false.B
    }
}
