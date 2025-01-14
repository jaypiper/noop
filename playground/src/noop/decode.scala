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
    val inst_r      = RegInit(0.U(INST_WIDTH.W))
    val pc_r        = RegInit(0.U(VADDR_WIDTH.W))
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
    val special_r   = RegInit(0.U(2.W))
    val swap_r      = RegInit(0.U(SWAP_WIDTH.W))
    val indi_r      = RegInit(0.U(INDI_WIDTH.W))
    val recov_r     = RegInit(false.B)
    val valid_r     = RegInit(false.B)

    def stall_pipe() = {
        stall_r := true.B;  drop_r := true.B;   recov_r := true.B
    }

    val hs_out = io.id2df.ready && io.id2df.valid
    val hs_in  = io.if2id.ready && io.if2id.valid
    val inst_in = io.if2id.inst
    val instType = ListLookup(inst_in, decodeDefault, decodeTable)
    val instType_c = ListLookup(inst_in(15,0), decodeDefault_c, decodeTable_c)
    val dType = instType(0)
    val jmp_indi = instType(5) === true.B
    val rs2_is_csr = instType(6) === true.B
    val rs1_is_imm = instType(8) === true.B
    val imm = Wire(SInt(DATA_WIDTH.W))
    val is_compress = inst_in(1,0) =/= 0x3.U
    imm := 0.S
    switch(dType){
        is(IType){ imm := inst_in(31,20).asSInt }
        is(SType){ imm := Cat(inst_in(31, 25), inst_in(11, 7)).asSInt }
        is(BType){ imm := Cat(inst_in(31), inst_in(7), inst_in(30, 25), inst_in(11, 8), 0.U(1.W)).asSInt }
        is(UType){ imm := Cat(inst_in(31, 12), 0.U(12.W)).asSInt }
        is(JType){ imm := Cat(inst_in(31), inst_in(19, 12), inst_in(20), inst_in(30, 21), 0.U(1.W)).asSInt }
    }
    when(hs_in && ~is_compress && !io.if2id.excep.en){
        inst_r          := io.if2id.inst
        pc_r            := io.if2id.pc
        excep_r         := io.if2id.excep
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
        special_r       := 0.U
        indi_r          := Cat(inst_in === Insts.SC_W || inst_in === Insts.SC_D,
                                inst_in === Insts.LR_W || inst_in === Insts.LR_D)
        swap_r          := NO_SWAP
        recov_r         := io.if2id.recov
        when(dType === INVALID && !io.if2id.excep.en){
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
            swap_r  := COPY_2_d
        }
        when(dType === IType){
            when(jmp_indi){
                jmp_type_r  := JMP_UNCOND
                rrs1_r      := true.B
                rs2_d_r     := io.if2id.pc + 4.U
                dst_d_r     := imm.asUInt
            }.elsewhen(rs2_is_csr){
                rs1_d_r     := inst_in(19,15)
                rrs1_r      := !rs1_is_imm
                rrs2_r      := true.B
                stall_pipe()
            }.otherwise{
                rrs1_r      := true.B
                rs2_d_r     := imm.asUInt
            }
        }
        when(dType === SType){
            rrs1_r      := true.B
            rrs2_r      := true.B
            swap_r      := SWAP_2_d
            dst_d_r     := imm.asUInt
        }
        when(dType === BType){
            rrs1_r      := true.B
            rrs2_r      := true.B
            dst_d_r     := (io.if2id.pc.asSInt + imm.asSInt)(VADDR_WIDTH-1,0).asUInt
            ctrl_r.brType := inst_in(14,12)
            jmp_type_r  := JMP_COND
        }
        when(dType === UType){
            rs1_d_r     := imm.asUInt
            rs2_d_r     := io.if2id.pc
        }
        when(dType === JType){
            rs1_d_r     := (io.if2id.pc.asSInt + imm.asSInt)(VADDR_WIDTH-1,0).asUInt
            rs2_d_r     := io.if2id.pc + 4.U
            dst_d_r     := 0.U
            jmp_type_r := JMP_UNCOND
        }
        when(inst_in === Insts.ECALL){
            excep_r.pc  := io.if2id.pc
            excep_r.en  := true.B
            excep_r.cause := PriorityMux(Seq(
                (io.idState.priv === PRV_M,     CAUSE_MACHINE_ECALL.U),
                (io.idState.priv === PRV_S,     CAUSE_SUPERVISOR_ECALL.U),
                (true.B,                        CAUSE_USER_ECALL.U)))
            excep_r.tval  := 0.U
            jmp_type_r  := JMP_CSR
            rs2_r       :=  Mux(io.idState.priv === PRV_M, CSR_MTVEC, CSR_STVEC)
            stall_pipe()
        }
        when(inst_in === Insts.SRET){
            excep_r.pc  := io.if2id.pc
            excep_r.en  := true.B
            excep_r.etype := ETYPE_SRET
            excep_r.cause := 0.U
            excep_r.tval  := 0.U
            jmp_type_r  := JMP_CSR
            rs2_r       := CSR_SEPC
            stall_pipe()
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
        when(inst_in === Insts.FENCE_I){
            special_r := SPECIAL_FENCE_I
            stall_pipe()
        }
        when(inst_in === Insts.SFENCE_VMA){
            special_r := SPECIAL_SFENCE_VMA
            stall_pipe()
        }
    }
    val rtype_c = instType_c(0)
    val itype_c = instType_c(1)
    val imm_c = Wire(SInt(DATA_WIDTH.W))
    val inst_c = inst_in(15,0)
    imm_c := 0.S

    switch(itype_c){
        is(ciw_u){  imm_c := Cat(0.U(54.W), inst_c(10,7), inst_c(12,11), inst_c(5), inst_c(6), 0.U(2.W)).asSInt}
        is(cls_w){  imm_c := Cat(0.U(57.W), inst_c(5), inst_c(12,10), inst_c(6), 0.U(2.W)).asSInt}
        is(cls_d){  imm_c := Cat(0.U(56.W), inst_c(6,5), inst_c(12,10), 0.U(3.W)).asSInt}
        is(ci_u){   imm_c := Cat(0.U(58.W), inst_c(12), inst_c(6,2)).asSInt}
        is(ci_s){   imm_c := Cat(inst_c(12), inst_c(6,2)).asSInt}
        is(ci_u2){  imm_c := Cat(0.U(56.W), inst_c(3,2), inst_c(12), inst_c(6,4), 0.U(2.W)).asSInt}
        is(ci_u3){  imm_c := Cat(0.U(55.W), inst_c(4,2), inst_c(12), inst_c(6,5), 0.U(3.W)).asSInt}
        is(ci_s4){  imm_c := Cat(inst_c(12), inst_c(4,3), inst_c(5), inst_c(2), inst_c(6), 0.U(4.W)).asSInt}
        is(ci_s12){ imm_c := Cat(inst_c(12), inst_c(6,2), 0.U(12.W)).asSInt}
        is(cj_s1){  imm_c := Cat(inst_c(12), inst_c(8), inst_c(10,9), inst_c(6), inst_c(7), inst_c(2), inst_c(11), inst_c(5,3), 0.U(1.W)).asSInt}
        is(cb_s1){  imm_c := Cat(inst_c(12), inst_c(6,5), inst_c(2), inst_c(11,10), inst_c(4,3), 0.U(1.W)).asSInt}
        is(css_u2){ imm_c := Cat(0.U(56.W), inst_c(8,7), inst_c(12,9), 0.U(2.W)).asSInt}
        is(css_u3){ imm_c := Cat(0.U(55.W), inst_c(9,7), inst_c(12,10), 0.U(3.W)).asSInt}
    }
    when(hs_in && is_compress && !io.if2id.excep.en){
        inst_r  := Cat(0.U(15.W), inst_c)
        pc_r    := io.if2id.pc
        excep_r         := io.if2id.excep
        ctrl_r.aluOp    := instType_c(2)
        ctrl_r.aluWidth := instType_c(3)
        ctrl_r.dcMode   := instType_c(4)
        ctrl_r.writeRegEn := instType_c(5)
        ctrl_r.writeCSREn := false.B
        rs1_r           := inst_c(11,7)
        rrs1_r          := false.B
        rs2_r           := inst_c(6,2)
        rrs2_r          := false.B
        dst_r           := inst_c(11,7)
        jmp_type_r      := NO_JMP
        special_r       := 0.U
        indi_r          := 0.U
        swap_r          := NO_SWAP
        recov_r         := io.if2id.recov
        when(rtype_c === c_invalid && !io.if2id.excep.en){
            excep_r.en      := true.B
            excep_r.cause   := CAUSE_ILLEGAL_INSTRUCTION.U
            excep_r.tval    := Cat(0.U(15.W), inst_c)
            excep_r.pc      := io.if2id.pc
            excep_r.etype   := 0.U
            stall_pipe()
        }
        when(rtype_c === cr){
            rrs1_r := true.B
            rrs2_r := true.B
            when(inst_c === C_JR){
                jmp_type_r  := JMP_UNCOND
                dst_d_r     := 0.U
            }
            when(inst_c === C_JALR){
                jmp_type_r  := JMP_UNCOND
                rrs2_r      := false.B
                rs2_d_r     := io.if2id.pc + 2.U
                dst_d_r     := 0.U
                dst_r       := 1.U
            }
        }
        when(rtype_c === ci){
            rrs1_r  := true.B
            rs2_d_r := imm_c.asUInt
            when(inst_c === C_LWSP || inst_c === C_LDSP){
                rs1_r := 2.U
            }
        }
        when(rtype_c === css){
            rrs1_r  := true.B
            rs1_r   := 2.U
            rrs2_r  := true.B
            rs2_r   := inst_c(6,2)
            dst_d_r := imm_c.asUInt
            swap_r  := SWAP_2_d
        }
        when(rtype_c === ciw){
            rrs1_r  := true.B
            rs1_r   := 2.U
            rs2_d_r := imm_c.asUInt
            dst_r   := decode_r3(inst_c(4,2))
        }
        when(rtype_c === cl){
            rrs1_r  := true.B
            rs1_r   := decode_r3(inst_c(9,7))
            rs2_d_r := imm_c.asUInt
            dst_r   := decode_r3(inst_c(4,2))
        }
        when(rtype_c === cs){
            rrs1_r  := true.B
            rs1_r   := decode_r3(inst_c(9,7))
            rrs2_r  := true.B
            rs2_r   := decode_r3(inst_c(4,2))
            dst_d_r := imm_c.asUInt
            dst_r   := decode_r3(inst_c(9,7))
            when(inst_c(1,0) === 0.U){      // sd, sw
                swap_r  := SWAP_2_d
            }
        }
        when(rtype_c === cb){
            rrs1_r  := true.B
            rs1_r   := decode_r3(inst_c(9,7))
            rs2_d_r := imm_c.asUInt
            dst_d_r := (io.if2id.pc.asSInt + imm_c).asUInt
            dst_r   := decode_r3(inst_c(9,7))
            when(inst_c === C_BEQZ){
                ctrl_r.brType := bEQ
                rs2_d_r     := 0.U
                jmp_type_r  := JMP_COND
            }
            when(inst_c === C_BNEZ){
                ctrl_r.brType := bNE
                rs2_d_r     := 0.U
                jmp_type_r  := JMP_COND
            }
        }
        when(rtype_c === cj){
            rs1_d_r := io.if2id.pc
            dst_d_r := imm_c.asUInt
            jmp_type_r  := JMP_UNCOND
        }
    }
    when(hs_in && io.if2id.excep.en){
        inst_r          := io.if2id.inst
        pc_r            := io.if2id.pc
        excep_r         := io.if2id.excep
        ctrl_r          := 0.U.asTypeOf(new Ctrl)
        rrs1_r          := false.B
        rrs2_r          := false.B
        jmp_type_r      := NO_JMP
        special_r       := 0.U
        indi_r          := 0.U
        swap_r          := NO_SWAP
        recov_r         := io.if2id.recov
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
    io.id2df.special    := special_r
    io.id2df.swap       := swap_r
    io.id2df.indi       := indi_r
    io.id2df.recov      := recov_r
    io.id2df.valid      := valid_r
}