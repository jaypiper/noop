package noop.decode

import chisel3._
import chisel3.util._
import noop.param._
import noop.param.common._
import noop.param.decode_config._
import noop.datapath._
import noop.param.Insts._
import noop.utils.VecDecoupledIO

class Decoder extends Module {
    val io = IO(new Bundle {
        val in = Input(new IF2ID)
        val out = Output(new ID2DF)
        val stall = Output(Bool())
    })

    val inst_in = io.in.inst
    val instType = ListLookup(inst_in, decodeDefault, decodeTable)
    val dType = instType(0)
    val jmp_indi = instType(5) === true.B
    val rs2_is_csr = instType(6) === true.B
    val imm = Wire(SInt(DATA_WIDTH.W))
    imm := 0.S
    switch(dType) {
        is(IType) {
            imm := inst_in(31, 20).asSInt
        }
        is(SType) {
            imm := Cat(inst_in(31, 25), inst_in(11, 7)).asSInt
        }
        is(BType) {
            imm := Cat(inst_in(31), inst_in(7), inst_in(30, 25), inst_in(11, 8), 0.U(1.W)).asSInt
        }
        is(UType) {
            imm := Cat(inst_in(31, 12), 0.U(12.W)).asSInt
        }
        is(JType) {
            imm := Cat(inst_in(31), inst_in(19, 12), inst_in(20), inst_in(30, 21), 0.U(1.W)).asSInt
        }
    }

    io.out := DontCare
    io.out.inst := io.in.inst
    io.out.pc := io.in.pc
    io.out.excep := 0.U.asTypeOf(new Exception)
    io.out.ctrl.aluOp := instType(1)
    io.out.ctrl.aluWidth := instType(2)
    io.out.ctrl.dcMode := instType(3)
    io.out.ctrl.writeRegEn := instType(4) & (inst_in(11, 7) =/= 0.U)
    io.out.ctrl.writeCSREn := instType(6)
    io.out.rs1 := inst_in(19, 15)
    io.out.rrs1 := false.B
    io.out.rs2 := inst_in(24, 20)
    io.out.rrs2 := false.B
    io.out.dst := inst_in(11, 7)
    io.out.jmp_type := NO_JMP
    io.out.nextPC := io.in.nextPC

    io.out.recov := io.in.recov // TODO: what's this
    io.stall := false.B

    when(dType === INVALID) {
        io.out.excep.en := true.B
        io.out.excep.cause := CAUSE_ILLEGAL_INSTRUCTION.U
        io.out.excep.tval := inst_in
        io.out.excep.etype := 0.U
        io.stall := true.B
    }
    when(dType === RType) {
        io.out.rrs1 := true.B
        io.out.rrs2 := true.B
    }
    when(dType === IType) {
        when(jmp_indi) {
            io.out.jmp_type := JMP_REG
            io.out.rrs1 := true.B
            io.out.rs2_d := io.in.pc + 4.U
            io.out.dst_d := imm.asUInt
        }.elsewhen(rs2_is_csr) {
            io.out.rs1_d := inst_in(19, 15)
            io.out.rrs1 := true.B
            io.out.rrs2 := true.B
            io.stall := true.B
        }.otherwise {
            io.out.rrs1 := true.B
            io.out.rs2_d := imm.asUInt
            io.out.dst_d := imm.asUInt
        }
    }
    when(dType === SType) {
        io.out.rrs1 := true.B
        io.out.rrs2 := true.B
        io.out.dst_d := imm.asUInt
    }
    when(dType === BType) {
        io.out.rrs1 := true.B
        io.out.rrs2 := true.B
        io.out.dst_d := imm.asUInt
        io.out.ctrl.brType := inst_in(14, 12)
        io.out.jmp_type := JMP_COND
    }
    when(dType === UType) {
        io.out.rs1_d := imm.asUInt
        io.out.rs2_d := io.in.pc
    }
    when(dType === JType) {
        io.out.rs1_d := io.in.pc
        io.out.rs2_d := io.in.pc + 4.U
        io.out.dst_d := imm.asUInt
        io.out.jmp_type := JMP_PC
    }

    when(inst_in === Insts.MRET) {
        io.out.excep.pc := io.in.pc
        io.out.excep.en := true.B
        io.out.excep.etype := ETYPE_MRET
        io.out.excep.cause := 0.U
        io.out.excep.tval := 0.U
        io.out.jmp_type := JMP_CSR
        io.stall := true.B
    }
}

class Decode extends Module{
    val io = IO(new Bundle{
        val if2id   = Flipped(VecDecoupledIO(ISSUE_WIDTH, new IF2ID))
        val id2df   = VecDecoupledIO(ISSUE_WIDTH, new ID2DF)
        val stall   = Vec(ISSUE_WIDTH, Output(Bool()))
        val idState = Input(new IdState)
    })
    val decoder = Seq.fill(ISSUE_WIDTH)(Module(new Decoder))

    val stall = io.if2id.valid.zip(decoder).map{ case (v, dec) => v && dec.io.stall }

    for (((v, in), (dec, i)) <- io.if2id.valid.zip(io.if2id.bits).zip(decoder.zipWithIndex)) {
        dec.io.in := in

        io.id2df.valid(i) := v
        if (i > 0) { // check whether flushed by previous instructions
            io.id2df.valid(i) := v && !VecInit(stall.take(i)).asUInt.orR
        }
        io.id2df.bits(i) := dec.io.out
        io.id2df.bits(i).recov := dec.io.stall
    }

    io.if2id.ready := !io.if2id.valid.asUInt.orR || io.id2df.ready

    io.stall := stall
}