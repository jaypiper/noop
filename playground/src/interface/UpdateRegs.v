module UpdateRegs (
    input  [2047:0]   regs_data,
    // input  [2047:0]   csrs_data,
    // input  [1:0]      priv,
    input clock
);
import "DPI-C" function void update_reg(int id, longint val);
// import "DPI-C" function void update_csr(int id, longint val);
import "DPI-C" function void update_priv(int val);

genvar i;
generate
    for(i = 0; i < 32; i = i+1) begin
        always @(posedge clock) begin
            update_reg(i, regs_data[i*64+63:i*64]);
            // update_csr(i, csrs_data[i*64+63:i*64]);
            // update_priv({30'b0, priv});
        end
    end
endgenerate

endmodule