module UpdateCsrs (
    input  [1:0]        priv,
    input  [75:0]       mstatus,
    input  [75:0]       mepc,
    input  [75:0]       mtval,
    input  [75:0]       mscratch,
    input  [75:0]       mcause,
    input  [75:0]       mtvec,
    input  [75:0]       mie,
    input  [75:0]       mip,
    input  [31:0]       hartid,
    input clock
);
import "DPI-C" function void update_csr(int hartid, int id, longint val);
import "DPI-C" function void update_priv(int hartid, int val);
always @(posedge clock) begin
    update_csr(hartid, {20'b0, mstatus[75:64]}, mstatus[63:0]);
    update_csr(hartid, {20'b0, mepc[75:64]},    mepc[63:0]);
    update_csr(hartid, {20'b0, mtval[75:64]},   mtval[63:0]);
    update_csr(hartid, {20'b0, mscratch[75:64]}, mscratch[63:0]);
    update_csr(hartid, {20'b0, mcause[75:64]},  mcause[63:0]);
    update_csr(hartid, {20'b0, mtvec[75:64]},   mtvec[63:0]);
    update_csr(hartid, {20'b0, mie[75:64]},     mie[63:0]);
    update_csr(hartid, {20'b0, mip[75:64]},     mip[63:0]);
    update_priv(hartid, {30'b0, priv});
end
endmodule