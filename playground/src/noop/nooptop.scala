
import sim.newtop
import prefix._
import noop.cpu._
import noop.device._

object NoopTop extends App{
    // (new chisel3.stage.ChiselStage).execute(args,
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new CPU()),
    //         firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix()),
    //         ModulePrefixAnnotation("ysyx_210539_")
    //     ))

    (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new newtop())))
    // (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new VgaCrossbar())))
}
