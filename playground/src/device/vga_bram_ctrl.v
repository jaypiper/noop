// //  READ_WRITE_MODE           - Read Write Mode           (string default: READ_WRITE)
// module my_module (
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram EN" *)
//   // Uncomment the following to set interface specific parameter on the bus interface.
//   //  (* X_INTERFACE_PARAMETER = "MASTER_TYPE <value>,MEM_ECC <value>,MEM_WIDTH <value>,MEM_SIZE <value>,READ_WRITE_MODE <value>" *)
//   input <bram_en>, // Chip Enable Signal (optional)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram DOUT" *)
//   output [<left_bound>:0] <bram_dout>, // Data Out Bus (optional)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram DIN" *)
//   input [<left_bound>:0] <bram_din>, // Data In Bus (optional)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram WE" *)
//   input [<left_bound>:0] <bram_we>, // Byte Enables (optional)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram ADDR" *)
//   input [<left_bound>:0] <bram_addr>, // Address Signal (required)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram CLK" *)
//   input <bram_clk>, // Clock Signal (required)
//   (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram RST" *)
//   input <bram_rst>, // Reset Signal (required)
// //  additional ports here
// );

module vga_bram_ctrl(
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram EN" *)
    output bram_en, // Chip Enable Signal (optional)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram DOUT" *)
    input [31:0] bram_dout, // Data Out Bus (optional)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram DIN" *)
    output [31:0] bram_din, // Data In Bus (optional)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram WE" *)
    output [3:0] bram_we, // Byte Enables (optional)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram ADDR" *)
    output [31:0] bram_addr, // Address Signal (required)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram CLK" *)
    output bram_clk, // Clock Signal (required)
    (* X_INTERFACE_INFO = "xilinx.com:interface:bram:1.0 bram RST" *)
    output bram_rst, // Reset Signal (required)
    
    output [31:0]vga_data,
	input [9:0]vga_h_addr,
	input [9:0]vga_v_addr,
    input vga_clk,
    input vga_rst
	);
    reg pre_valid;
    always @(posedge vga_clk) begin
        pre_valid <= bram_en;
    end
    parameter width = 400;
    parameter height = 300;
    assign bram_addr = ((vga_v_addr * width) + vga_h_addr) * 4;
    assign bram_we = 1'b0;
    assign bram_en = (vga_h_addr < width) && (vga_v_addr < height);
    assign bram_din = 32'b0;
    assign vga_data = pre_valid ? bram_dout : 0;
    assign bram_clk = vga_clk;
    assign bram_rst = vga_rst;

endmodule
