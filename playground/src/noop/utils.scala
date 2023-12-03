package noop.utils

import chisel3._
import chisel3.util._
import difftest.common.LogPerfControl
import noop.param.common.isSim

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
    left: DecoupledIO[T],
    isFlush: Bool
  ): DecoupledIO[T] = {
    val right = Wire(Decoupled(left.bits.cloneType))
    PipelineConnect(left, right, right.ready, isFlush)
    right
  }

  def apply[T <: Data](
    left: ValidIO[T]
  ): ValidIO[T] = {
    apply(left, false.B)
  }

  def apply[T <: Data](
    left: ValidIO[T],
    flush: Bool
  ): ValidIO[T] = {
    val right = Wire(ValidIO(left.bits.cloneType))
    right.valid := RegNext(left.valid && !flush, false.B)
    right.bits := RegEnable(left.bits, left.valid && !flush)
    right
  }
}

class VecDecoupledIO[T <: Data](val length: Int, gen: T) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Vec(length, Bool()))
  val bits = Output(Vec(length, gen))

  def connectNoPipe(right: Seq[DecoupledIO[T]], flush: Bool): Unit = {
    require(length == right.length, s"length of left($length) != right(${right.length})")
    val is_out = RegInit(VecInit.fill(length)(false.B))
    val is_fire = Wire(Vec(length, Bool()))
    for (i <- 0 until length) {
      right(i).valid := valid(i) && !is_out(i)
      right(i).bits := bits(i)
      is_fire(i) := is_out(i) || right(i).ready
      when(ready || flush) {
        is_out(i) := false.B
      }.elsewhen(valid(i) && right(i).ready) {
        is_out(i) := true.B
      }
    }
    ready := is_fire.asUInt.andR
  }
}

object VecDecoupledIO {
  def apply[T <: Data](length: Int, gen: T): VecDecoupledIO[T] = new VecDecoupledIO(length, gen.cloneType)
}

class VecPipelineConnectPipe[T <: Data](nWays: Int, gen: T) extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(VecDecoupledIO(nWays, gen))
    val out = VecDecoupledIO(nWays, gen)
    val rightOutFire = Input(Vec(nWays, Bool()))
    val isFlush = Input(Bool())
  })

  VecPipelineConnect.connect(io.in, io.out, io.rightOutFire, io.isFlush)
}

object VecPipelineConnect {
  def connect[T <: Data](
    left: VecDecoupledIO[T],
    right: VecDecoupledIO[T],
    rightOutFire: Seq[Bool],
    isFlush: Bool
  ): Seq[T] = {
    require(left.length == right.length && right.length == rightOutFire.length)
    val length = left.length
    val valid = RegInit(VecInit.fill(length)(false.B))

    left.ready := right.ready
    (0 until length).map(i => {
      val leftFire = left.valid(i) && right.ready
      when(rightOutFire(i)) { valid(i) := false.B }
      when(leftFire) { valid(i) := true.B }
      when(isFlush) { valid(i) := false.B }

      right.valid(i) := valid(i)
      val data = RegEnable(left.bits(i), leftFire)
      right.bits(i) := data
      data
    })
  }

  def apply[T <: Data](
    left: VecDecoupledIO[T],
    right: VecDecoupledIO[T],
    rightOutFire: Seq[Bool],
    isFlush: Bool,
    moduleName: Option[String] = None
  ): Option[Seq[T]] = {
    require(left.length == right.length && right.length == rightOutFire.length)
    val length = left.length
    if (moduleName.isDefined) {
      val pipeline = Module(new VecPipelineConnectPipe(length, left.bits))
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

  def apply[T <: Data](
    left: VecDecoupledIO[T],
    right: Seq[DecoupledIO[T]],
    isFlush: Bool
  ): Seq[T] = {
    val pipeline = Wire(VecDecoupledIO(right.length, right.head.bits))
    pipeline.ready := VecInit(right.map(_.ready)).asUInt.andR
    right.zipWithIndex.foreach { case (r, i) =>
      r.valid := pipeline.valid(i)
      r.bits := pipeline.bits(i)
    }
    connect(left, pipeline, right.map(_.ready), isFlush)
  }

  def apply[T <: Data](
    left: Seq[DecoupledIO[T]],
    right: Seq[DecoupledIO[T]],
    isFlush: Bool
  ): Seq[T] = {
    val pipeline_prev = Wire(VecDecoupledIO(left.length, left.head.bits))
    pipeline_prev.valid := left.map(_.valid)
    pipeline_prev.bits := left.map(_.bits)
    left.foreach(_.ready := pipeline_prev.ready)
    apply(pipeline_prev, right, isFlush)
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

object PerfAccumulate {
  def create(perfName: String, perfCnt: UInt): Unit = {
    val control = LogPerfControl()

    val counter = RegInit(0.U(64.W)).suggestName(perfName + "Counter")
    val next_counter = WireInit(0.U(64.W))
    next_counter := counter + perfCnt
    counter := Mux(control.clean, 0.U, next_counter)

    when (control.dump && control.logEnable) {
      printf(p"[cycle=${control.timer}] $perfName: $next_counter\n")
    }
  }

  def apply(perfName: String, perfCnt: UInt): Unit = {
    if (isSim) {
      create(perfName, perfCnt)
    }
  }
}


// The following code is from https://github.com/chipsalliance/rocket-chip
// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

abstract class ReplacementPolicy {
  def nBits: Int
  def perSet: Boolean
  def way: UInt
  def miss: Unit
  def hit: Unit
  def access(touch_way: UInt): Unit
  def access(touch_ways: Seq[Valid[UInt]]): Unit
  def state_read: UInt
  def get_next_state(state: UInt, touch_way: UInt): UInt
  def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt = {
    touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))
  }
  def get_replace_way(state: UInt): UInt
}

object ReplacementPolicy {
  def fromString(s: String, n_ways: Int): ReplacementPolicy = s.toLowerCase match {
    case "plru"   => new PseudoLRU(n_ways)
    case t => throw new IllegalArgumentException(s"unknown Replacement Policy type $t")
  }
}

class PseudoLRU(n_ways: Int) extends ReplacementPolicy {
  // Pseudo-LRU tree algorithm: https://en.wikipedia.org/wiki/Pseudo-LRU#Tree-PLRU
  //
  //
  // - bits storage example for 4-way PLRU binary tree:
  //                  bit[2]: ways 3+2 older than ways 1+0
  //                  /                                  \
  //     bit[1]: way 3 older than way 2    bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 3-way PLRU binary tree:
  //                  bit[1]: way 2 older than ways 1+0
  //                                                  \
  //                                       bit[0]: way 1 older than way 0
  //
  //
  // - bits storage example for 8-way PLRU binary tree:
  //                      bit[6]: ways 7-4 older than ways 3-0
  //                      /                                  \
  //            bit[5]: ways 7+6 > 5+4                bit[2]: ways 3+2 > 1+0
  //            /                    \                /                    \
  //     bit[4]: way 7>6    bit[3]: way 5>4    bit[1]: way 3>2    bit[0]: way 1>0

  def nBits = n_ways - 1
  def perSet = true
  private val state_reg = if (nBits == 0) Reg(UInt(0.W)) else RegInit(0.U(nBits.W))
  def state_read = WireDefault(state_reg)

  def access(touch_way: UInt): Unit = {
    state_reg := get_next_state(state_reg, touch_way)
  }
  def access(touch_ways: Seq[Valid[UInt]]): Unit = {
    when (VecInit(touch_ways.map(_.valid)).asUInt.orR) {
      state_reg := get_next_state(state_reg, touch_ways)
    }
  }


  /** @param state state_reg bits for this sub-tree
   * @param touch_way touched way encoded value bits for this sub-tree
   * @param tree_nways number of ways in this sub-tree
   */
  def get_next_state(state: UInt, touch_way: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1),                   s"wrong state bits width ${state.getWidth} for $tree_nways ways")
    require(touch_way.getWidth == (log2Ceil(tree_nways) max 1), s"wrong encoded way width ${touch_way.getWidth} for $tree_nways ways")

    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val set_left_older      = !touch_way(log2Ceil(tree_nways)-1)
      val left_subtree_state  = state(tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(set_left_older,
          Mux(set_left_older,
            left_subtree_state,  // if setting left sub-tree as older, do NOT recurse into left sub-tree
            get_next_state(left_subtree_state, touch_way(log2Ceil(left_nways)-1,0), left_nways)),  // recurse left if newer
          Mux(set_left_older,
            get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
            right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(set_left_older,
          Mux(set_left_older,
            get_next_state(right_subtree_state, touch_way(log2Ceil(right_nways)-1,0), right_nways),  // recurse right if newer
            right_subtree_state))  // if setting right sub-tree as older, do NOT recurse into right sub-tree
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so set the single state bit opposite of the lsb of the touched way encoded value
      !touch_way(0)
    } else {  // tree_nways <= 1
      // we are at an empty node in an empty tree for 1 way, so return single zero bit for Chisel (no zero-width wires)
      0.U(1.W)
    }
  }

  def get_next_state(state: UInt, touch_way: UInt): UInt = {
    def padTo(data: UInt, w: Int): UInt = Cat(0.U((w - data.getWidth).W), data)
    val touch_way_sized = if (touch_way.getWidth < log2Ceil(n_ways)) padTo(touch_way, log2Ceil(n_ways))
    else touch_way(log2Ceil(n_ways)-1,0)
    get_next_state(state, touch_way_sized, n_ways)
  }


  /** @param state state_reg bits for this sub-tree
   * @param tree_nways number of ways in this sub-tree
   */
  def get_replace_way(state: UInt, tree_nways: Int): UInt = {
    require(state.getWidth == (tree_nways-1), s"wrong state bits width ${state.getWidth} for $tree_nways ways")

    // this algorithm recursively descends the binary tree, filling in the way-to-replace encoded value from msb to lsb
    if (tree_nways > 2) {
      // we are at a branching node in the tree, so recurse
      val right_nways: Int = 1 << (log2Ceil(tree_nways) - 1)  // number of ways in the right sub-tree
      val left_nways:  Int = tree_nways - right_nways         // number of ways in the left sub-tree
      val left_subtree_older  = state(tree_nways-2)
      val left_subtree_state  = state(tree_nways-3, right_nways-1)
      val right_subtree_state = state(right_nways-2, 0)

      if (left_nways > 1) {
        // we are at a branching node in the tree with both left and right sub-trees, so recurse both sub-trees
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(left_subtree_older,  // if left sub-tree is older, recurse left, else recurse right
            get_replace_way(left_subtree_state,  left_nways),    // recurse left
            get_replace_way(right_subtree_state, right_nways)))  // recurse right
      } else {
        // we are at a branching node in the tree with only a right sub-tree, so recurse only right sub-tree
        Cat(left_subtree_older,      // return the top state bit (current tree node) as msb of the way-to-replace encoded value
          Mux(left_subtree_older,  // if left sub-tree is older, return and do not recurse right
            0.U(1.W),
            get_replace_way(right_subtree_state, right_nways)))  // recurse right
      }
    } else if (tree_nways == 2) {
      // we are at a leaf node at the end of the tree, so just return the single state bit as lsb of the way-to-replace encoded value
      state(0)
    } else {  // tree_nways <= 1
      // we are at an empty node in an unbalanced tree for non-power-of-2 ways, so return single zero bit as lsb of the way-to-replace encoded value
      0.U(1.W)
    }
  }

  def get_replace_way(state: UInt): UInt = get_replace_way(state, n_ways)

  def way = get_replace_way(state_reg)
  def miss = access(way)
  def hit = {}
}

// The following classes are from OpenXiangShan/Utility
class CircularQueuePtr[T <: CircularQueuePtr[T]](val entries: Int) extends Bundle {
  val PTR_WIDTH = log2Up(entries)
  val flag = Bool()
  val value = UInt(PTR_WIDTH.W)

  override def toPrintable: Printable = {
    p"$flag:$value"
  }

  final def +(v: UInt): T = {
    val entries = this.entries
    val new_ptr = Wire(this.asInstanceOf[T].cloneType)
    if(isPow2(entries)){
      new_ptr := (Cat(this.flag, this.value) + v).asTypeOf(new_ptr)
    } else {
      val new_value = this.value +& v
      val diff = Cat(0.U(1.W), new_value).asSInt - Cat(0.U(1.W), entries.U.asTypeOf(new_value)).asSInt
      val reverse_flag = diff >= 0.S
      new_ptr.flag := Mux(reverse_flag, !this.flag, this.flag)
      new_ptr.value := Mux(reverse_flag,
        diff.asUInt,
        new_value
      )
    }
    new_ptr
  }

  final def -(v: UInt): T = {
    val flipped_new_ptr = this + (this.entries.U - v)
    val new_ptr = Wire(this.asInstanceOf[T].cloneType)
    new_ptr.flag := !flipped_new_ptr.flag
    new_ptr.value := flipped_new_ptr.value
    new_ptr
  }

  final def === (that: T): Bool = this.asUInt === that.asUInt

  final def =/= (that: T): Bool = this.asUInt =/= that.asUInt

  final def > (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value > that.value
    differentFlag ^ compare
  }

  final def < (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value < that.value
    differentFlag ^ compare
  }

  final def >= (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value >= that.value
    differentFlag ^ compare
  }

  final def <= (that: T): Bool = {
    val differentFlag = this.flag ^ that.flag
    val compare = this.value <= that.value
    differentFlag ^ compare
  }

  def toOH: UInt = UIntToOH(value, entries)
}

trait HasCircularQueuePtrHelper {

  def isEmpty[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): Bool = {
    enq_ptr === deq_ptr
  }

  def isFull[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): Bool = {
    (enq_ptr.flag =/= deq_ptr.flag) && (enq_ptr.value === deq_ptr.value)
  }

  def distanceBetween[T <: CircularQueuePtr[T]](enq_ptr: T, deq_ptr: T): UInt = {
    assert(enq_ptr.entries == deq_ptr.entries)
    Mux(enq_ptr.flag === deq_ptr.flag,
      enq_ptr.value - deq_ptr.value,
      enq_ptr.entries.U + enq_ptr.value - deq_ptr.value)
  }

  def isAfter[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left > right

  def isBefore[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left < right

  def isNotAfter[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left <= right

  def isNotBefore[T <: CircularQueuePtr[T]](left: T, right: T): Bool = left >= right
}

// Should only be used when left and right are continuous pointers.
class QPtrMatchMatrix[T <: CircularQueuePtr[T]](left: Seq[T], right: Seq[T]) {
  val matrix = left.map(l => right.map(_.value === l.value))

  def apply(leftIndex: Int, rightIndex: Int): Bool = {
    require(leftIndex < left.length && rightIndex < right.length)
    if (leftIndex == 0 || rightIndex == 0) {
      matrix(leftIndex)(rightIndex)
    }
    else {
      apply(leftIndex - 1, rightIndex - 1)
    }
  }
  def apply(leftIndex: Int): Seq[Bool] = right.indices.map(i => apply(leftIndex, i))
}
