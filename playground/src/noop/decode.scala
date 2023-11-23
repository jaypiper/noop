package noop.decode

import chisel3._
import chisel3.util._
import noop.param._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._
import noop.param.Insts._
class Decode extends Module{
    val io = IO(new Bundle{
        val if2id   = Flipped(DecoupledIO(new IF2ID))
        val id2if   = Output(new PipelineBackCtrl)
        val id2df   = DecoupledIO(new ID2DF)
        val df2id   = Input(new PipelineBackCtrl)
        val idState = Input(new IdState)
    })
    // from if
    val drop_r      = RegInit(false.B)
    val stall_r     = RegInit(false.B)
    drop_r := false.B;  stall_r := false.B
    val drop_in     = drop_r || io.df2id.drop
    io.id2if.drop   := drop_in
    io.id2if.stall  := (stall_r && !io.df2id.drop) || io.df2id.stall

    def stall_pipe() = {
        when (io.if2id.fire) {
            stall_r := true.B
            drop_r := true.B
            io.id2df.bits.recov := true.B
        }
    }

    val inst_in = io.if2id.bits.inst
    val instType = ListLookup(inst_in, decodeDefault, decodeTable)
    val dType = instType(0)
    val jmp_indi = instType(5) === true.B
    val rs2_is_csr = instType(6) === true.B
    val imm = Wire(SInt(DATA_WIDTH.W))
    imm := 0.S
    switch(dType){
        is(IType){ imm := inst_in(31,20).asSInt }
        is(SType){ imm := Cat(inst_in(31, 25), inst_in(11, 7)).asSInt }
        is(BType){ imm := Cat(inst_in(31), inst_in(7), inst_in(30, 25), inst_in(11, 8), 0.U(1.W)).asSInt }
        is(UType){ imm := Cat(inst_in(31, 12), 0.U(12.W)).asSInt }
        is(JType){ imm := Cat(inst_in(31), inst_in(19, 12), inst_in(20), inst_in(30, 21), 0.U(1.W)).asSInt }
    }

    io.id2df.bits := DontCare
    io.id2df.bits.inst := io.if2id.bits.inst
    io.id2df.bits.pc := io.if2id.bits.pc
    io.id2df.bits.excep         := 0.U.asTypeOf(new Exception)
    io.id2df.bits.ctrl.aluOp      := instType(1)
    io.id2df.bits.ctrl.aluWidth   := instType(2)
    io.id2df.bits.ctrl.dcMode     := instType(3)
    io.id2df.bits.ctrl.writeRegEn := instType(4) & (inst_in(11,7) =/= 0.U)
    io.id2df.bits.ctrl.writeCSREn := instType(6)
    io.id2df.bits.rs1           := inst_in(19,15)
    io.id2df.bits.rrs1          := false.B
    io.id2df.bits.rs2           := Mux(rs2_is_csr, inst_in(31,20), inst_in(24,20))
    io.id2df.bits.rrs2          := false.B
    io.id2df.bits.dst           := inst_in(11,7)
    io.id2df.bits.jmp_type      := NO_JMP
    io.id2df.bits.nextPC := io.if2id.bits.nextPC

    io.id2df.bits.recov         := io.if2id.bits.recov
    when(dType === INVALID){
        io.id2df.bits.excep.en      := true.B
        io.id2df.bits.excep.cause   := CAUSE_ILLEGAL_INSTRUCTION.U
        io.id2df.bits.excep.tval    := inst_in
        io.id2df.bits.excep.pc      := io.if2id.bits.pc
        io.id2df.bits.excep.etype   := 0.U
        stall_pipe()
    }
    when(dType === RType){
        io.id2df.bits.rrs1  := true.B
        io.id2df.bits.rrs2  := true.B
    }
    when(dType === IType){
        when(jmp_indi){
            io.id2df.bits.jmp_type  := JMP_REG
            io.id2df.bits.rrs1      := true.B
            io.id2df.bits.rs2_d     := io.if2id.bits.pc + 4.U
            io.id2df.bits.dst_d     := imm.asUInt
        }.elsewhen(rs2_is_csr){
            io.id2df.bits.rs1_d     := inst_in(19,15)
            io.id2df.bits.rrs1      := true.B
            io.id2df.bits.rrs2      := true.B
            stall_pipe()
        }.otherwise{
            io.id2df.bits.rrs1      := true.B
            io.id2df.bits.rs2_d     := imm.asUInt
            io.id2df.bits.dst_d     := imm.asUInt
        }
    }
    when(dType === SType){
        io.id2df.bits.rrs1      := true.B
        io.id2df.bits.rrs2      := true.B
        io.id2df.bits.dst_d     := imm.asUInt
    }
    when(dType === BType){
        io.id2df.bits.rrs1      := true.B
        io.id2df.bits.rrs2      := true.B
        io.id2df.bits.dst_d     := imm.asUInt
        io.id2df.bits.ctrl.brType := inst_in(14,12)
        io.id2df.bits.jmp_type  := JMP_COND
    }
    when(dType === UType){
        io.id2df.bits.rs1_d     := imm.asUInt
        io.id2df.bits.rs2_d     := io.if2id.bits.pc
    }
    when(dType === JType){
        io.id2df.bits.rs1_d     := io.if2id.bits.pc
        io.id2df.bits.rs2_d     := io.if2id.bits.pc + 4.U
        io.id2df.bits.dst_d     := imm.asUInt
        io.id2df.bits.jmp_type := JMP_PC
    }

    when(inst_in === Insts.MRET){
        io.id2df.bits.excep.pc  := io.if2id.bits.pc
        io.id2df.bits.excep.en  := true.B
        io.id2df.bits.excep.etype := ETYPE_MRET
        io.id2df.bits.excep.cause := 0.U
        io.id2df.bits.excep.tval  := 0.U
        io.id2df.bits.jmp_type  := JMP_CSR
        io.id2df.bits.rs2       := CSR_MEPC
        stall_pipe()
    }

    io.if2id.ready := !io.if2id.valid || io.id2df.ready
    io.id2df.valid      := io.if2id.valid && !drop_r
}