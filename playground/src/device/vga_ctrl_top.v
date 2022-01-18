// combine mapping and vga_ctrl_axi

module vga_ctrl(
    input clock,
	input resetn,
    input         io_master_awready,
    output        io_master_awvalid,
    output [31:0] io_master_awaddr,
    output [3:0]  io_master_awid,
    output [7:0]  io_master_awlen,
    output [2:0]  io_master_awsize,
    output [1:0]  io_master_awburst,
    input         io_master_wready,
    output        io_master_wvalid,
    output [63:0] io_master_wdata,
    output [7:0]  io_master_wstrb,
    output        io_master_wlast,
    output        io_master_bready,
    input         io_master_bvalid,
    input  [1:0]  io_master_bresp,
    input  [3:0]  io_master_bid,
    input         io_master_arready,
    output        io_master_arvalid,
    output [31:0] io_master_araddr,
    output [3:0]  io_master_arid,
    output [7:0]  io_master_arlen,
    output [2:0]  io_master_arsize,
    output [1:0]  io_master_arburst,
    output        io_master_rready,
    input         io_master_rvalid,
    input  [1:0]  io_master_rresp,
    input  [63:0] io_master_rdata,
    input         io_master_rlast,
    input  [3:0]  io_master_rid,

    output        io_slave_awready,
    input         io_slave_awvalid,
    input  [31:0] io_slave_awaddr,
    input  [3:0]  io_slave_awid,
    input  [7:0]  io_slave_awlen,
    input  [2:0]  io_slave_awsize,
    input  [1:0]  io_slave_awburst,
    output        io_slave_wready,
    input         io_slave_wvalid,
    input  [63:0] io_slave_wdata,
    input  [7:0]  io_slave_wstrb,
    input         io_slave_wlast,
    input         io_slave_bready,
    output        io_slave_bvalid,
    output [1:0]  io_slave_bresp,
    output [3:0]  io_slave_bid,
    output        io_slave_arready,
    input         io_slave_arvalid,
    input  [31:0] io_slave_araddr,
    input  [3:0]  io_slave_arid,
    input  [7:0]  io_slave_arlen,
    input  [2:0]  io_slave_arsize,
    input  [1:0]  io_slave_arburst,
    input         io_slave_rready,
    output        io_slave_rvalid,
    output [1:0]  io_slave_rresp,
    output [63:0] io_slave_rdata,
    output        io_slave_rlast,
    output [3:0]  io_slave_rid,

    output hsync,
	output vsync,
	output [3:0]vga_r,
	output [3:0]vga_g,
	output [3:0]vga_b

);
    wire        vga_slave_awready;
    wire        vga_slave_awvalid;
    wire [31:0] vga_slave_awaddr;
    wire [3:0]  vga_slave_awid;
    wire [7:0]  vga_slave_awlen;
    wire [2:0]  vga_slave_awsize;
    wire [1:0]  vga_slave_awburst;
    wire        vga_slave_wready;
    wire        vga_slave_wvalid;
    wire [63:0] vga_slave_wdata;
    wire [7:0]  vga_slave_wstrb;
    wire        vga_slave_wlast;
    wire        vga_slave_bready;
    wire        vga_slave_bvalid;
    wire [1:0]  vga_slave_bresp;
    wire [3:0]  vga_slave_bid;
    wire        vga_slave_arready;
    wire        vga_slave_arvalid;
    wire [31:0] vga_slave_araddr;
    wire [3:0]  vga_slave_arid;
    wire [7:0]  vga_slave_arlen;
    wire [2:0]  vga_slave_arsize;
    wire [1:0]  vga_slave_arburst;
    wire        vga_slave_rready;
    wire        vga_slave_rvalid;
    wire [1:0]  vga_slave_rresp;
    wire [63:0] vga_slave_rdata;
    wire        vga_slave_rlast;
    wire [3:0]  vga_slave_rid;
    wire        map_slave_awready;
    wire        map_slave_awvalid;
    wire [31:0] map_slave_awaddr;
    wire [3:0]  map_slave_awid;
    wire [7:0]  map_slave_awlen;
    wire [2:0]  map_slave_awsize;
    wire [1:0]  map_slave_awburst;
    wire        map_slave_wready;
    wire        map_slave_wvalid;
    wire [63:0] map_slave_wdata;
    wire [7:0]  map_slave_wstrb;
    wire        map_slave_wlast;
    wire        map_slave_bready;
    wire        map_slave_bvalid;
    wire [1:0]  map_slave_bresp;
    wire [3:0]  map_slave_bid;
    wire        map_slave_arready;
    wire        map_slave_arvalid;
    wire [31:0] map_slave_araddr;
    wire [3:0]  map_slave_arid;
    wire [7:0]  map_slave_arlen;
    wire [2:0]  map_slave_arsize;
    wire [1:0]  map_slave_arburst;
    wire        map_slave_rready;
    wire        map_slave_rvalid;
    wire [1:0]  map_slave_rresp;
    wire [63:0] map_slave_rdata;
    wire        map_slave_rlast;
    wire [3:0]  map_slave_rid;


    wire [31:0] offset;

    VgaCrossbar vgacrossbar(
        .clock(clock),
        .reset(~resetn),
        .io_master_awready(io_slave_awready),
        .io_master_awvalid(io_slave_awvalid),
        .io_master_awaddr(io_slave_awaddr),
        .io_master_awid(io_slave_awid),
        .io_master_awlen(io_slave_awlen),
        .io_master_awsize(io_slave_awsize),
        .io_master_awburst(io_slave_awburst),
        .io_master_wready(io_slave_wready),
        .io_master_wvalid(io_slave_wvalid),
        .io_master_wdata(io_slave_wdata),
        .io_master_wstrb(io_slave_wstrb),
        .io_master_wlast(io_slave_wlast),
        .io_master_bready(io_slave_bready),
        .io_master_bvalid(io_slave_bvalid),
        .io_master_bresp(io_slave_bresp),
        .io_master_bid(io_slave_bid),
        .io_master_arready(io_slave_arready),
        .io_master_arvalid(io_slave_arvalid),
        .io_master_araddr(io_slave_araddr),
        .io_master_arid(io_slave_arid),
        .io_master_arlen(io_slave_arlen),
        .io_master_arsize(io_slave_arsize),
        .io_master_arburst(io_slave_arburst),
        .io_master_rready(io_slave_rready),
        .io_master_rvalid(io_slave_rvalid),
        .io_master_rresp(io_slave_rresp),
        .io_master_rdata(io_slave_rdata),
        .io_master_rlast(io_slave_rlast),
        .io_master_rid(io_slave_rid),
        .io_vga_slave_awready(vga_slave_awready),
        .io_vga_slave_awvalid(vga_slave_awvalid),
        .io_vga_slave_awaddr(vga_slave_awaddr),
        .io_vga_slave_awid(vga_slave_awid),
        .io_vga_slave_awlen(vga_slave_awlen),
        .io_vga_slave_awsize(vga_slave_awsize),
        .io_vga_slave_awburst(vga_slave_awburst),
        .io_vga_slave_wready(vga_slave_wready),
        .io_vga_slave_wvalid(vga_slave_wvalid),
        .io_vga_slave_wdata(vga_slave_wdata),
        .io_vga_slave_wstrb(vga_slave_wstrb),
        .io_vga_slave_wlast(vga_slave_wlast),
        .io_vga_slave_bready(vga_slave_bready),
        .io_vga_slave_bvalid(vga_slave_bvalid),
        .io_vga_slave_bresp(vga_slave_bresp),
        .io_vga_slave_bid(vga_slave_bid),
        .io_vga_slave_arready(vga_slave_arready),
        .io_vga_slave_arvalid(vga_slave_arvalid),
        .io_vga_slave_araddr(vga_slave_araddr),
        .io_vga_slave_arid(vga_slave_arid),
        .io_vga_slave_arlen(vga_slave_arlen),
        .io_vga_slave_arsize(vga_slave_arsize),
        .io_vga_slave_arburst(vga_slave_arburst),
        .io_vga_slave_rready(vga_slave_rready),
        .io_vga_slave_rvalid(vga_slave_rvalid),
        .io_vga_slave_rresp(vga_slave_rresp),
        .io_vga_slave_rdata(vga_slave_rdata),
        .io_vga_slave_rlast(vga_slave_rlast),
        .io_vga_slave_rid(vga_slave_rid),
        .io_map_slave_awready(map_slave_awready),
        .io_map_slave_awvalid(map_slave_awvalid),
        .io_map_slave_awaddr(map_slave_awaddr),
        .io_map_slave_awid(map_slave_awid),
        .io_map_slave_awlen(map_slave_awlen),
        .io_map_slave_awsize(map_slave_awsize),
        .io_map_slave_awburst(map_slave_awburst),
        .io_map_slave_wready(map_slave_wready),
        .io_map_slave_wvalid(map_slave_wvalid),
        .io_map_slave_wdata(map_slave_wdata),
        .io_map_slave_wstrb(map_slave_wstrb),
        .io_map_slave_wlast(map_slave_wlast),
        .io_map_slave_bready(map_slave_bready),
        .io_map_slave_bvalid(map_slave_bvalid),
        .io_map_slave_bresp(map_slave_bresp),
        .io_map_slave_bid(map_slave_bid),
        .io_map_slave_arready(map_slave_arready),
        .io_map_slave_arvalid(map_slave_arvalid),
        .io_map_slave_araddr(map_slave_araddr),
        .io_map_slave_arid(map_slave_arid),
        .io_map_slave_arlen(map_slave_arlen),
        .io_map_slave_arsize(map_slave_arsize),
        .io_map_slave_arburst(map_slave_arburst),
        .io_map_slave_rready(map_slave_rready),
        .io_map_slave_rvalid(map_slave_rvalid),
        .io_map_slave_rresp(map_slave_rresp),
        .io_map_slave_rdata(map_slave_rdata),
        .io_map_slave_rlast(map_slave_rlast),
        .io_map_slave_rid(map_slave_rid)
    );

    vga_ctrl_comb vga(
        .clock(clock),
        .resetn(resetn),
        .io_master_awready(1'b0),
        // .io_master_awvalid,
        // .io_master_awaddr,
        // .io_master_awid,
        // .io_master_awlen,
        // .io_master_awsize,
        // .io_master_awburst,
        .io_master_wready(1'b0),
        // .io_master_wvalid,
        // .io_master_wdata,
        // .io_master_wstrb,
        // .io_master_wlast,
        .io_master_bready(1'b0),
        // .io_master_bvalid,
        // .io_master_bresp,
        // .io_master_bid,
        .io_master_arready(io_master_arready),
        .io_master_arvalid(io_master_arvalid),
        .io_master_araddr(io_master_araddr),
        .io_master_arid(io_master_arid),
        .io_master_arlen(io_master_arlen),
        .io_master_arsize(io_master_arsize),
        .io_master_arburst(io_master_arburst),
        .io_master_rready(io_master_rready),
        .io_master_rvalid(io_master_rvalid),
        .io_master_rresp(io_master_rresp),
        .io_master_rdata(io_master_rdata),
        .io_master_rlast(io_master_rlast),
        .io_master_rid(io_master_rid),

        .io_slave_awready(vga_slave_awready),
        .io_slave_awvalid(vga_slave_awvalid),
        .io_slave_awaddr(vga_slave_awaddr),
        .io_slave_awid(vga_slave_awid),
        .io_slave_awlen(vga_slave_awlen),
        .io_slave_awsize(vga_slave_awsize),
        .io_slave_awburst(vga_slave_awburst),
        .io_slave_wready(vga_slave_wready),
        .io_slave_wvalid(vga_slave_wvalid),
        .io_slave_wdata(vga_slave_wdata),
        .io_slave_wstrb(vga_slave_wstrb),
        .io_slave_wlast(vga_slave_wlast),
        .io_slave_bready(vga_slave_bready),
        .io_slave_bvalid(vga_slave_bvalid),
        .io_slave_bresp(vga_slave_bresp),
        .io_slave_bid(vga_slave_bid),
        .io_slave_arready(vga_slave_arready),
        .io_slave_arvalid(vga_slave_arvalid),
        .io_slave_araddr(vga_slave_araddr),
        .io_slave_arid(vga_slave_arid),
        .io_slave_arlen(vga_slave_arlen),
        .io_slave_arsize(vga_slave_arsize),
        .io_slave_arburst(vga_slave_arburst),
        .io_slave_rready(vga_slave_rready),
        .io_slave_rvalid(vga_slave_rvalid),
        .io_slave_rresp(vga_slave_rresp),
        .io_slave_rdata(vga_slave_rdata),
        .io_slave_rlast(vga_slave_rlast),
        .io_slave_rid(vga_slave_rid),

        .io_offset(offset),
        .hsync(hsync),
        .vsync(vsync),
        .vga_r(vga_r),
        .vga_g(vga_g),
        .vga_b(vga_b)
    );

    Mapping map(
        .clock(clock),
        .reset(~resetn),
        .io_map_in_awready(map_slave_awready),
        .io_map_in_awvalid(map_slave_awvalid),
        .io_map_in_awaddr(map_slave_awaddr),
        .io_map_in_awid(map_slave_awid),
        .io_map_in_awlen(map_slave_awlen),
        .io_map_in_awsize(map_slave_awsize),
        .io_map_in_awburst(map_slave_awburst),
        .io_map_in_wready(map_slave_wready),
        .io_map_in_wvalid(map_slave_wvalid),
        .io_map_in_wdata(map_slave_wdata),
        .io_map_in_wstrb(map_slave_wstrb),
        .io_map_in_wlast(map_slave_wlast),
        .io_map_in_bready(map_slave_bready),
        .io_map_in_bvalid(map_slave_bvalid),
        .io_map_in_bresp(map_slave_bresp),
        .io_map_in_bid(map_slave_bid),
        .io_map_in_arready(map_slave_arready),
        .io_map_in_arvalid(map_slave_arvalid),
        .io_map_in_araddr(map_slave_araddr),
        .io_map_in_arid(map_slave_arid),
        .io_map_in_arlen(map_slave_arlen),
        .io_map_in_arsize(map_slave_arsize),
        .io_map_in_arburst(map_slave_arburst),
        .io_map_in_rready(map_slave_rready),
        .io_map_in_rvalid(map_slave_rvalid),
        .io_map_in_rresp(map_slave_rresp),
        .io_map_in_rdata(map_slave_rdata),
        .io_map_in_rlast(map_slave_rlast),
        .io_map_in_rid(map_slave_rid),
        .io_offset(offset),
        .io_map_out_awready(io_master_awready),
        .io_map_out_awvalid(io_master_awvalid),
        .io_map_out_awaddr(io_master_awaddr),
        .io_map_out_awid(io_master_awid),
        .io_map_out_awlen(io_master_awlen),
        .io_map_out_awsize(io_master_awsize),
        .io_map_out_awburst(io_master_awburst),
        .io_map_out_wready(io_master_wready),
        .io_map_out_wvalid(io_master_wvalid),
        .io_map_out_wdata(io_master_wdata),
        .io_map_out_wstrb(io_master_wstrb),
        .io_map_out_wlast(io_master_wlast),
        .io_map_out_bready(io_master_bready),
        .io_map_out_bvalid(io_master_bvalid),
        .io_map_out_bresp(io_master_bresp),
        .io_map_out_bid(io_master_bid),
        .io_map_out_arready(0),
        // .io_map_out_arvalid,
        // .io_map_out_araddr,
        // .io_map_out_arid,
        // .io_map_out_arlen,
        // .io_map_out_arsize,
        // .io_map_out_arburst,
        // .io_map_out_rready,
        .io_map_out_rvalid(1'b0),
        .io_map_out_rresp(2'b0),
        .io_map_out_rdata(32'b0),
        .io_map_out_rlast(1'b0),
        .io_map_out_rid(4'b0)
    );

endmodule

