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
        val if2id   = Flipped(new IF2ID)
        val id2df   = new ID2DF
        val idState = Input(new IdState)
    })
    // from if
    val drop_r      = RegInit(false.B)
    val stall_r     = RegInit(false.B)
    drop_r := false.B;  stall_r := false.B
    val drop_in     = drop_r || io.id2df.drop
    io.if2id.drop   := drop_in
    io.if2id.stall  := (stall_r && !io.id2df.drop) || io.id2df.stall
    dontTouch(io.if2id.stall)
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(PADDR_WIDTH.W))
    val excep_r     = RegInit(0.U.asTypeOf(new Exception))
    val ctrl_r      = RegInit(0.U.asTypeOf(new Ctrl))
    val rs1_r       = RegInit(0.U(REG_WIDTH.W))
    val rrs1_r      = RegInit(false.B)
    val rs1_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val rs2_r       = RegInit(0.U(CSR_WIDTH.W))
    val rrs2_r      = RegInit(false.B)
    val rs2_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val dst_r       = RegInit(0.U(REG_WIDTH.W))
    val dst_d_r     = RegInit(0.U(DATA_WIDTH.W))
    val jmp_type_r  = RegInit(0.U(2.W))
    val recov_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)
    val nextPC_r    = RegInit(0.U(PADDR_WIDTH.W))

    def stall_pipe() = {
        stall_r := true.B;  drop_r := true.B; recov_r := true.B
    }

    val hs_out = io.id2df.ready && io.id2df.valid
    val hs_in  = io.if2id.ready && io.if2id.valid
    val inst_in = io.if2id.inst
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
    when(hs_in){
        inst_r          := io.if2id.inst
        pc_r            := io.if2id.pc
        excep_r         := 0.U.asTypeOf(new Exception)
        ctrl_r.aluOp      := instType(1)
        ctrl_r.aluWidth   := instType(2)
        ctrl_r.dcMode     := instType(3)
        ctrl_r.writeRegEn := instType(4)
        ctrl_r.writeCSREn := instType(6)
        rs1_r           := inst_in(19,15)
        rrs1_r          := false.B
        rs2_r           := Mux(rs2_is_csr, inst_in(31,20), inst_in(24,20))
        rrs2_r          := false.B
        dst_r           := inst_in(11,7)
        jmp_type_r      := NO_JMP
        nextPC_r        := io.if2id.nextPC

        recov_r         := io.if2id.recov
        when(dType === INVALID){
            excep_r.en      := true.B
            excep_r.cause   := CAUSE_ILLEGAL_INSTRUCTION.U
            excep_r.tval    := inst_in
            excep_r.pc      := io.if2id.pc
            excep_r.etype   := 0.U
            stall_pipe()
        }
        when(dType === RType){
            rrs1_r  := true.B
            rrs2_r  := true.B
        }
        when(dType === IType){
            when(jmp_indi){
                jmp_type_r  := JMP_UNCOND
                rrs1_r      := true.B
                rs2_d_r     := io.if2id.pc + 4.U
                dst_d_r     := imm.asUInt
            }.elsewhen(rs2_is_csr){
                rs1_d_r     := inst_in(19,15)
                rrs1_r      := true.B
                rrs2_r      := true.B
                stall_pipe()
            }.otherwise{
                rrs1_r      := true.B
                rs2_d_r     := imm.asUInt
                dst_d_r     := imm.asUInt
            }
        }
        when(dType === SType){
            rrs1_r      := true.B
            rrs2_r      := true.B
            dst_d_r     := imm.asUInt
        }
        when(dType === BType){
            rrs1_r      := true.B
            rrs2_r      := true.B
            dst_d_r     := (io.if2id.pc.asSInt + imm.asSInt)(PADDR_WIDTH-1,0).asUInt
            ctrl_r.brType := inst_in(14,12)
            jmp_type_r  := JMP_COND
        }
        when(dType === UType){
            rs1_d_r     := imm.asUInt
            rs2_d_r     := io.if2id.pc
        }
        when(dType === JType){
            rs1_d_r     := (io.if2id.pc.asSInt + imm.asSInt)(PADDR_WIDTH-1,0).asUInt
            rs2_d_r     := io.if2id.pc + 4.U
            dst_d_r     := 0.U
            jmp_type_r := JMP_UNCOND
        }

        when(inst_in === Insts.MRET){
            excep_r.pc  := io.if2id.pc
            excep_r.en  := true.B
            excep_r.etype := ETYPE_MRET
            excep_r.cause := 0.U
            excep_r.tval  := 0.U
            jmp_type_r  := JMP_CSR
            rs2_r       := CSR_MEPC
            stall_pipe()
        }

    }
    
    io.if2id.ready := false.B
    when(!drop_in){
        when(valid_r && !hs_out){
        }.elsewhen(io.if2id.valid){
            io.if2id.ready := true.B
        }
    }
    when(!io.id2df.drop){
       when(hs_in){
           valid_r := true.B
       }.elsewhen(hs_out){
           valid_r := false.B
       }

    }.otherwise{
        valid_r := false.B
    }
    io.id2df.inst       := inst_r
    io.id2df.pc         := pc_r
    io.id2df.nextPC     := nextPC_r
    io.id2df.excep      := excep_r
    io.id2df.ctrl       := ctrl_r
    io.id2df.rs1        := rs1_r
    io.id2df.rrs1       := rrs1_r
    io.id2df.rs1_d      := rs1_d_r
    io.id2df.rs2        := rs2_r
    io.id2df.rrs2       := rrs2_r
    io.id2df.rs2_d      := rs2_d_r
    io.id2df.dst        := dst_r
    io.id2df.dst_d      := dst_d_r
    io.id2df.jmp_type   := jmp_type_r
    io.id2df.recov      := recov_r
    io.id2df.valid      := valid_r
}