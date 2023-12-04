package sim
import chisel3._
import chisel3.util._
import noop.param.common._
import axi._
import axi.axi_config._
import chisel3.util.experimental.loadMemoryFromFile
import difftest.UARTIO
import difftest.common.DifftestFlash
import noop.datapath._

object UartPara{
    val RHR = 0.U    // Receive Holding Register (read mode)
    val THR = 0.U    // Transmit Holding Register (write mode)
    val DLL = 0.U    // LSB of Divisor Latch (write mode)
    val IER = 1.U    // Interrupt Enable Register (write mode)
    val DLM = 1.U    // MSB of Divisor Latch (write mode)
    val FCR = 2.U    // FIFO Control Register (write mode)
    val ISR = 2.U    // Interrupt Status Register (read mode)
    val LCR = 3.U    // Line Control Register
    val MCR = 4.U    // Modem Control Register
    val LSR = 5.U    // Line Status Register
    val MSR = 6.U    // Modem Status Register
    val SPR = 7.U    // ScratchPad Register
}


object port{
    val UART_BASE   = "h10000000".U(PADDR_WIDTH.W)
    val CLINT       = "h2000000".U(PADDR_WIDTH.W)
    val CLINT_MTIMECMP = "h2004000".U(PADDR_WIDTH.W)
    val CLINT_MTIME = "h200bff8".U(PADDR_WIDTH.W)
    val RTC_ADDR    = "ha1000048".U(PADDR_WIDTH.W)
    val NEMU_VGA    = "ha0000000".U(PADDR_WIDTH.W)
    val PLIC        = "h0c000000".U(PADDR_WIDTH.W)
    val SDCARD_MMIO = "h43000000".U(PADDR_WIDTH.W)
    val FLASH_ADDR  = "h30000000".U(PADDR_WIDTH.W)
}

class SimMMIOIO extends Bundle{
    val mmioAxi = Flipped(new AxiMaster)
    // val int     = Output(Bool())
    val uart = new UARTIO
}

class SimMMIO extends Module{
    val io = IO(new SimMMIOIO)
    val sdcard = Module(new SdCard)
    sdcard.io.addr := 0.U; sdcard.io.wen := false.B; sdcard.io.cen := false.B; sdcard.io.wdata := 0.U
    sdcard.io.clock := clock
    val (sIdle :: sWdata :: sWresp :: sRdata :: sSDread :: Nil) = Enum(5)
    val uart = RegInit(VecInit(Seq.fill(0x8)(0.U(8.W))))
    val mtime = RegInit(0.U(64.W))
    val mtimecmp = RegInit(0.U(64.W))
    val vga = Mem(480000, UInt(8.W))
    val vga_ctrl = RegInit(VecInit(Seq.fill(2)(0.U(32.W))))
    val flash = DifftestFlash()
    // loadMemoryFromFile()
    val waready  = RegInit(false.B)
    val wdready  = RegInit(false.B)
    val waddr   = RegInit(0.U(PADDR_WIDTH.W))
    val wsize   = RegInit(0.U(3.W))
    val wdata   = RegInit(0.U(DATA_WIDTH.W))

    val raready = RegInit(false.B)
    val rdvalid = RegInit(false.B)
    val raddr   = RegInit(0.U(PADDR_WIDTH.W))
    val rdata   = RegInit(0.U(DATA_WIDTH.W))

    val offset  = RegInit(0.U(8.W))
    //serial
    val serialData = RegInit(0.U(64.W))

    val count = RegInit(0.U(2.W))
    // count := count + 1.U
    when(count === 0.U){
        mtime := mtime + 20.U
    }
    val state = RegInit(sIdle)
    val islast  = (offset === 0.U)
    val addr    = io.mmioAxi.wa.bits.addr
    val inputwd = Cat((0 until 8).reverse.map(i => Mux(io.mmioAxi.wd.bits.strb(i) === 1.U, io.mmioAxi.wd.bits.data(8*i+7, 8*i), 0.U(8.W))))

    val is_flash_read = state === sIdle &&
      io.mmioAxi.ra.valid && raready &&
      io.mmioAxi.ra.bits.addr >= port.FLASH_ADDR &&
      io.mmioAxi.ra.bits.addr < (port.FLASH_ADDR + "h10000000".U)
    flash.en := is_flash_read
    flash.addr := io.mmioAxi.ra.bits.addr - port.FLASH_ADDR
    val has_flash_read = RegInit(false.B)
    when (is_flash_read) {
        has_flash_read := true.B
    }.elsewhen (io.mmioAxi.rd.fire) {
        has_flash_read := false.B
    }

    io.uart.out.valid := false.B
    io.uart.out.ch := DontCare
    io.uart.in.valid := false.B

    uart(5) := 0x20.U
    switch(state){
        is(sIdle){
            waready := true.B
            raready := true.B
            offset  := 0.U
            when(io.mmioAxi.wa.valid && waready){
                waddr   := io.mmioAxi.wa.bits.addr
                wsize   := io.mmioAxi.wa.bits.size
                waready  := false.B
                state   := sWdata
            }
            when(io.mmioAxi.ra.valid && raready){
                raddr   := io.mmioAxi.ra.bits.addr
                raready := false.B
                state   := sRdata
                when(io.mmioAxi.ra.bits.addr >= port.UART_BASE && io.mmioAxi.ra.bits.addr <= port.UART_BASE + 7.U){
                    rdata   := uart(io.mmioAxi.ra.bits.addr - port.UART_BASE) << (io.mmioAxi.ra.bits.addr(2,0) * 8.U)
                }.elsewhen(io.mmioAxi.ra.bits.addr === port.CLINT_MTIME){
                    rdata   := mtime
                }.elsewhen(io.mmioAxi.ra.bits.addr === port.CLINT_MTIMECMP){
                    rdata   := mtimecmp
                }.elsewhen(io.mmioAxi.ra.bits.addr === "ha1000100".U(PADDR_WIDTH.W)){
                    rdata   := "h190012c".U(PADDR_WIDTH.W)
                }.elsewhen(io.mmioAxi.ra.bits.addr === port.RTC_ADDR){
                    rdata   := (mtime % 1000000.U(DATA_WIDTH.W))(31,0)
                }.elsewhen(io.mmioAxi.ra.bits.addr === port.RTC_ADDR + 4.U){
                    rdata   := Cat((mtime / 1000000.U(DATA_WIDTH.W))(31,0), Fill(32, 0.U(1.W)))
                }.elsewhen(io.mmioAxi.ra.bits.addr === "ha1000060".U){
                    // rdata   := 70.U
                    rdata := 0.U
                }.elsewhen(io.mmioAxi.ra.bits.addr === port.CLINT){
                    rdata := 0.U
                }.elsewhen(io.mmioAxi.ra.bits.addr >= port.PLIC && io.mmioAxi.ra.bits.addr < (port.PLIC + 0x3000.U)){

                }.elsewhen(io.mmioAxi.ra.bits.addr >= port.FLASH_ADDR && io.mmioAxi.ra.bits.addr < (port.FLASH_ADDR + "h10000000".U)){
                    // rdata := flash_rdata
                }.elsewhen(io.mmioAxi.ra.bits.addr >= port.SDCARD_MMIO && io.mmioAxi.ra.bits.addr < (port.SDCARD_MMIO + 0x80.U)){
                    sdcard.io.addr  := io.mmioAxi.ra.bits.addr(6,0)
                    sdcard.io.cen   := true.B
                    state           := sSDread
                }.otherwise{
                    rdata   := 0.U
                    assert(false.B, "mmio invalid raddr: %x\n", io.mmioAxi.ra.bits.addr)
                }
            }
        }
        //write
        is(sWdata){
            wdready := true.B
            when(io.mmioAxi.wd.valid){
                wdata   := io.mmioAxi.wd.bits.data
                // printf("%x\n", waddr)
                when(waddr >= port.UART_BASE && waddr <= port.UART_BASE + 7.U){
                    val offset = waddr(2,0)
                    uart(waddr - port.UART_BASE) := (inputwd >> (offset*8.U))(7,0)
                    io.uart.out.valid := (waddr & 0x7.U) === 0.U
                    io.uart.out.ch := inputwd(7, 0)
                }.elsewhen(waddr === port.CLINT_MTIMECMP){
                    mtimecmp := inputwd
                }.otherwise{
                    when(waddr === "ha10003f8".U){
                        printf("%c", inputwd(7,0))
                    }.elsewhen(waddr >= port.NEMU_VGA && waddr <= port.NEMU_VGA + 480000.U){
                        for(i <- 0 until 4){
                            vga(waddr - port.NEMU_VGA + i.U) := (inputwd >> (waddr(2,0)*8.U))(8*i+7, 8*i)
                        }
                    }.elsewhen(waddr === "ha1000100".U){
                        vga_ctrl(0) := inputwd(31,0)
                    }.elsewhen(waddr === "ha1000104".U){
                        vga_ctrl(1) := inputwd(63,32)
                    }.elsewhen(waddr >= "h0c000000".U && waddr <= "hc202000".U){

                    }.elsewhen(waddr === port.CLINT){

                    }.elsewhen(waddr >= port.PLIC && waddr < (port.PLIC + 0x3000.U)){

                    }.elsewhen(waddr >= port.SDCARD_MMIO && waddr < port.SDCARD_MMIO + 0x80.U){
                        sdcard.io.addr  := waddr(6,0)
                        sdcard.io.wdata := inputwd >> Cat(waddr(2,0), 0.U(3.W))
                        sdcard.io.wen   := true.B
                    }.otherwise{
                        printf("mmio invalid waddr: %x\n", io.mmioAxi.wa.bits.addr)
                    }
                }
                when(io.mmioAxi.wd.bits.last){
                    state   := sWresp
                    wdready := false.B
                }
            }
        }
        is(sWresp){
            // io.wr.valid := true.B
            // io.wr.bits.resp := RESP_OKAY  //先不清除吧，一直有效
            state := sIdle
        }
        //read
        is(sRdata){
            rdvalid := true.B
            when(io.mmioAxi.rd.ready && rdvalid){
                offset  := offset + 1.U
                when(islast){
                    rdvalid := false.B
                    state   := sIdle
                }
            }
        }
        is(sSDread){
            rdata := sdcard.io.rdata << Cat(io.mmioAxi.ra.bits.addr(2,0), 0.U(3.W))
            state := sRdata
        }
    }

    io.mmioAxi.initSlave()
    io.mmioAxi.wr.valid := true.B
    io.mmioAxi.wr.bits.resp := RESP_OKAY
    io.mmioAxi.wa.ready := waready
    io.mmioAxi.wd.ready := wdready
    io.mmioAxi.ra.ready := raready
    io.mmioAxi.rd.valid := rdvalid
    io.mmioAxi.rd.bits.data := Mux(has_flash_read, flash.data, rdata)
    io.mmioAxi.rd.bits.last := islast

}

// class SdCard extends BlackBox with HasBlackBoxPath{
//     val io = IO(new Bundle{
//         val addr    = Input(UInt(7.W))
//         val wen     = Input(Bool())
//         val wdata   = Input(UInt(64.W))
//         val clock   = Input(Clock())
//         val cen     = Input(Bool())
//         val rdata   = Output(UInt(64.W))
//     })
//     addPath("playground/src/device/SdCard.v")
// }

class SdCard extends Module {
    val io = IO(new Bundle{
        val addr    = Input(UInt(7.W))
        val wen     = Input(Bool())
        val wdata   = Input(UInt(64.W))
        val clock   = Input(Clock())
        val cen     = Input(Bool())
        val rdata   = Output(UInt(64.W))
    })

    io := DontCare
}
