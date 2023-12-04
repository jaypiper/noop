module InstFinish (
    input           clock,
    input           is_mmio,
    input           valid,
    input [31:0]    pc,
    input [31:0]    inst,
    input [11:0]    rcsr_id,
    input [31:0]    hartid
);

import "DPI-C" function void update_indi(int hartid, bit is_mmio, bit valid, int rcsr_id);
import "DPI-C" function void update_pc(int hartid, int pc, int inst);

always @(posedge clock) begin
    update_indi(hartid, is_mmio, valid, {20'b0, rcsr_id});
    update_pc(hartid, pc, inst);
end

endmodule