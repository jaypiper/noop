package noop.ibuffer

import chisel3._
import chisel3.util._
import noop.datapath._
import noop.param.common._
import noop.utils._

class IBufPtr extends CircularQueuePtr[IBufPtr](IBUF_SIZE)

class IBufferData extends Module {
  val io = IO(new Bundle {
    val read = Vec(ISSUE_WIDTH, new Bundle {
      val addr = Input(UInt(IBUF_INDEX.W))
      val data = Output(new IF2ID)
    })
    val write = Vec(ISSUE_WIDTH, new Bundle {
      val en = Input(Bool())
      val addr = Input(UInt(IBUF_INDEX.W))
      val data = Input(new IF2ID)
    })
  })

  val entries = Reg(Vec(IBUF_SIZE, new IF2ID))

  io.read.foreach(r => r.data := entries(r.addr))

  io.write.foreach(w => {
    when (w.en) {
      entries(w.addr) := w.data
    }
  })
}

class IBuffer extends Module with HasCircularQueuePtrHelper {
  val io = IO(new Bundle {
    val in = Flipped(VecDecoupledIO(ISSUE_WIDTH, new IF2ID))
    val out = VecDecoupledIO(ISSUE_WIDTH, new IF2ID)
    val flush = Input(Bool())
  })

  val deqPtrVec = RegInit(VecInit.tabulate(ISSUE_WIDTH)(_.U.asTypeOf(new IBufPtr)))
  val enqPtrVec = RegInit(VecInit.tabulate(ISSUE_WIDTH)(_.U.asTypeOf(new IBufPtr)))
  val data = Module(new IBufferData)

  val validEntries = distanceBetween(enqPtrVec.head, deqPtrVec.head)

  // Enqueue
  io.in.ready := validEntries <= (IBUF_SIZE - ISSUE_WIDTH).U
  for (i <- 0 until ISSUE_WIDTH) {
    data.io.write(i).addr := enqPtrVec(i).value
    data.io.write(i).data := io.in.bits(i)
    data.io.write(i).en := io.in.valid(i) && io.in.ready && !io.flush
  }
  when(io.in.ready) {
    enqPtrVec := VecInit(enqPtrVec.map(_ + PopCount(io.in.valid)))
  }

  // Dequeue
  val deqData = Reg(Vec(ISSUE_WIDTH, new IF2ID))
  for (i <- 0 until ISSUE_WIDTH) {
    data.io.read(i).addr := deqPtrVec(i).value
    io.out.valid(i) := validEntries > i.U
    io.out.bits(i) := data.io.read(i).data
  }
  when(io.out.ready && !io.flush) {
    deqPtrVec := deqPtrVec.map(_ + PopCount(io.out.valid))
  }

  // Flush
  when (io.flush) {
    enqPtrVec := deqPtrVec
  }
}
