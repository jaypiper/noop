package rom

import java.nio.file.{Files, Paths}
import chisel3._
import chisel3.util._

class rom extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val data = Output(UInt(64.W))
  })
  val binpath = "/home/chenlu/program/a55/noop/bin/coremark-riscv64-kjw.bin"
  // val binpath = "/home/chenlu/program/noop/bin/add-longlong-riscv64-mycpu-rv64g.bin"
  val wordbits = 64
  val bin = Files.readAllBytes(Paths.get(binpath))
  val upSize = 1 << log2Ceil(bin.size)
  val bingp = (bin ++ Seq.fill(upSize - bin.size)(0.toByte)).grouped(wordbits / 8)
  def byteShift(x: Byte, y: BigInt) = (x.toInt & 0xff) | (y << 8)
  val wordArray = bingp.map(_.foldRight(BigInt(0))(byteShift)).toSeq
  val rom = VecInit(wordArray.map(x => x.U(wordbits.W)))
  io.data := RegNext(rom(io.addr))

}