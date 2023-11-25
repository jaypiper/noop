package noop.utils

import chisel3._
import chisel3.util._

class PipelineConnectPipe[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(gen.cloneType))
    val out = DecoupledIO(gen.cloneType)
    val rightOutFire = Input(Bool())
    val isFlush = Input(Bool())
  })

  PipelineConnect.connect(io.in, io.out, io.rightOutFire, io.isFlush)
}

object PipelineConnect {
  def connect[T <: Data](
    left: DecoupledIO[T],
    right: DecoupledIO[T],
    rightOutFire: Bool,
    isFlush: Bool
  ): T = {
    val valid = RegInit(false.B)
    val leftFire = left.valid && right.ready
    when (rightOutFire) { valid := false.B }
    when (leftFire) { valid := true.B }
    when (isFlush) { valid := false.B }

    left.ready := right.ready
    val data = RegEnable(left.bits, leftFire)
    right.bits := data
    right.valid := valid

    data
  }

  def apply[T <: Data](
    left: DecoupledIO[T],
    right: DecoupledIO[T],
    rightOutFire: Bool,
    isFlush: Bool,
    moduleName: Option[String] = None
  ): Option[T] = {
    if (moduleName.isDefined) {
      val pipeline = Module(new PipelineConnectPipe(left.bits))
      pipeline.suggestName(moduleName.get)
      pipeline.io.in <> left
      pipeline.io.rightOutFire := rightOutFire
      pipeline.io.isFlush := isFlush
      pipeline.io.out <> right
      pipeline.io.out.ready := right.ready
      None
    }
    else {
      // do not use module here to please DCE
      Some(connect(left, right, rightOutFire, isFlush))
    }
  }
}

object PipelineNext {
  def apply[T <: Data](
    left: DecoupledIO[T],
    rightOutFire: Bool,
    isFlush: Bool
  ): DecoupledIO[T] = {
    val right = Wire(Decoupled(left.bits.cloneType))
    PipelineConnect(left, right, rightOutFire, isFlush)
    right
  }

  def apply[T <: Data](
    left: ValidIO[T]
  ): ValidIO[T] = {
    val right = Wire(ValidIO(left.bits.cloneType))
    right.valid := RegNext(left.valid, false.B)
    right.bits := RegEnable(left.bits, left.valid)
    right
  }
}

class PipelineAdjuster[T <: Data](gen: T, width: Int) extends Module {
  val io = IO(new Bundle() {
    val in = Vec(width, Flipped(DecoupledIO(gen)))
    val out = Vec(width, DecoupledIO(gen))
    val flush = Input(Bool())
  })
  io.out.tail.foreach(o => {
    o.valid := false.B
    o.bits := DontCare
  })

  val in_try_r = RegInit(VecInit.fill(width)(true.B))
  val just_in = RegNext(io.in.head.ready)
  val in_try = Mux(just_in, VecInit(io.in.map(_.valid)), in_try_r)

  val try_index = PriorityEncoder(in_try)

  io.out.head.valid := io.in(try_index).valid
  io.out.head.bits := io.in(try_index).bits
  when (io.flush || io.in.head.ready) {
    in_try_r.foreach(_ := true.B)
  }.elsewhen(just_in && !io.out.head.fire) {
    in_try_r := in_try
  }.elsewhen (io.out.head.fire) {
    in_try_r(try_index) := false.B
  }

  val is_last = try_index === (width - 1).U || !io.in(try_index + 1.U).valid
  io.in.foreach(_.ready := !io.in(try_index).valid || io.out.head.ready && is_last)
}

object PipelineAdjuster {
  def apply[T <: Data](
    left: Seq[DecoupledIO[T]],
    flush: Bool
  ) = {
    val adjuster = Module(new PipelineAdjuster(left.head.bits.cloneType, left.length))
    val right = Wire(adjuster.io.out.cloneType)
    adjuster.io.in <> left
    adjuster.io.out <> right
    adjuster.io.flush := flush
    right
  }
}
