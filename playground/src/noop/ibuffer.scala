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

  val deqPtrVec = RegInit(VecInit.tabulate(2 * ISSUE_WIDTH)(_.U.asTypeOf(new IBufPtr)))
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
    io.out.valid(i) := validEntries > i.U
    io.out.bits(i) := deqData(i)
  }
  when(io.out.ready && !io.flush) {
    deqPtrVec := deqPtrVec.map(_ + PopCount(io.out.valid))
  }

  // Dequeue data. Read in the previous clock cycle.
  val ptrMatch = new QPtrMatchMatrix(deqPtrVec, enqPtrVec)
  val oldData = deqData ++ data.io.read.map(_.data)
  val (enqBypassEn, enqBypassData, nextStepData) = oldData.zipWithIndex.map{ case (old_d, i) =>
    val enqBypassEnVec = io.in.valid.zipWithIndex.map { case (v, j) => v && ptrMatch(i)(j) }
    val enqBypassEn = io.in.ready && VecInit(enqBypassEnVec).asUInt.orR
    val enqBypassData = Mux1H(enqBypassEnVec, io.in.bits)
    (enqBypassEn, enqBypassData, Mux(enqBypassEn, enqBypassData, old_d))
  }.unzip3
  val deqEnable_n = io.out.valid.tail.map(v => !v) :+ true.B
  for (i <- 0 until ISSUE_WIDTH) {
    data.io.read(i).addr := deqPtrVec(i + ISSUE_WIDTH).value
    when (io.out.ready && io.out.valid.head) {
      deqData(i) := PriorityMux(deqEnable_n, nextStepData.drop(i + 1).take(ISSUE_WIDTH))
    }.elsewhen (enqBypassEn(i)) {
      deqData(i) := enqBypassData(i)
    }
  }

  // Flush
  when (io.flush) {
    enqPtrVec := deqPtrVec.take(ISSUE_WIDTH)
  }
}
