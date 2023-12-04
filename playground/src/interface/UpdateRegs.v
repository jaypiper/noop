module UpdateRegs (
    input  [2047:0]     regs_data,
    input [31:0] hartid,
    input clock
);
import "DPI-C" function void update_reg(int hartid, int id, longint val);

genvar i;
generate
    for(i = 0; i < 32; i = i+1) begin
        always @(posedge clock) begin
            update_reg(hartid, i, regs_data[i*64+63:i*64]);
        end
    end
endgenerate

endmodule