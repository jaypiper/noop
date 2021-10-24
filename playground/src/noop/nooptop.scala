
import sim.newtop

object NoopTop extends App{
    // (new chisel3.stage.ChiselStage).execute(args,
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new CPU()),
    //         firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix()),
    //         ModulePrefixAnnotation("ysyx_210539_")
    //     ))

    (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new newtop())))
}
