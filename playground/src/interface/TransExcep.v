module TransExcep(
    input           clock,
    input           intr,
    input [63:0]    cause,
    input [31:0]    pc
);

import "DPI-C" function void update_excep(int hartid, bit intr, longint cause, int pc);

always @(posedge clock) begin
    update_excep(0, intr, cause, pc);
end

endmodule