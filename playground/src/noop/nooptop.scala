
import sim.newtop
import prefix._
import noop.cpu._
import noop.simtop._

object NoopTop extends App{
    // (new chisel3.stage.ChiselStage).execute(args,
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new riscv_cpu_top()),
    //         firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix()),
    //         ModulePrefixAnnotation("ysyx_210539_")
    //     ))
    // (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new riscv_cpu_top())))
    (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new SimTop())))
    // (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new newtop())))
}
