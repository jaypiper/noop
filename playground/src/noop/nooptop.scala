
import sim.SimTop
import prefix._
import noop.cpu._
import noop.param.common._
import noop.alu._
import circt.stage._

object NoopTop extends App{
    // (new chisel3.stage.ChiselStage).execute(args,
    //     Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new CPU()),
    //         firrtl.stage.RunFirrtlTransformAnnotation(new AddModulePrefix()),
    //         ModulePrefixAnnotation("ysyx_210539_")
    //     ))

    if (isSim)
        (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new SimTop())))
    else {
        (new chisel3.stage.ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new CPU())))
        // (new ChiselStage).execute(args, Seq(chisel3.stage.ChiselGeneratorAnnotation(() => new CPU)) :+ CIRCTTargetAnnotation(CIRCTTarget.Verilog))
    }
}
