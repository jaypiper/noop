module preg #(parameter w = 1, parameter reset_val = 0)(
    input clock,
    input reset,
    input [w-1:0] din,
    output [w-1:0] dout,
    input wen
);

reg [w-1:0] data;
assign dout = data;

always @(posedge clock) begin
    if(reset)
        data <= reset_val;
    else if(wen)
        data <= din; 
end

endmodule
