module dir(
    (* X_INTERFACE_INFO = "xilinx.com:signal:interrupt:1.0 inp INTERRUPT" *)
    (* X_INTERFACE_PARAMETER = "SENSITIVITY EDGE_RISING" *)
    input inp,
    input eth_clk,
    input eth_resetn,
    input cpu_clk,
    input cpu_resetn,
    output out_p
);

wire [1:0] sync_din, sync_dout;
wire sync_wen;

preg #(2,0) sync(cpu_clk, ~cpu_resetn, sync_din, sync_dout, sync_wen);
assign sync_wen = 1;
assign sync_din = {sync_dout[0], inp};

wire out_din, out_dout, out_wen;
preg #(1,0) out_r(cpu_clk, ~cpu_resetn, out_din, out_dout, out_wen);
assign out_din = sync_dout[0] & !sync_dout[1];
assign out_wen = !out_dout || !count_din;

wire [2:0] count_din, count_dout;
wire count_wen;
preg #(3,8) count(eth_clk, ~eth_resetn, count_din, count_dout, count_wen);
assign count_din = count_dout - 1;
assign count_wen = out_dout;

assign out_p = out_dout; 

endmodule