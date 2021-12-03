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

    wire vga_clk_din, vga_clk_dout;
    preg #(1,0) vga_clk_gen(clock, ~resetn, ~vga_clk_dout, vga_clk_dout, 1);
    wire vga_clk_en = vga_clk_dout;

	parameter h_frontporch = 120;
	parameter h_active = 184;
	parameter h_backporch = 984;
	parameter h_total = 1040;

	parameter v_frontporch = 6;
	parameter v_active = 29;
	parameter v_backporch = 629;
	parameter v_total = 666;

    parameter MODE800x600 = 0;
    parameter MODE400x300 = 1;

    wire [8:0] buf1_addr, buf2_addr;
    wire [23:0] buf1_din, buf2_din, buf1_dout, buf2_dout;
    wire buf1_wen, buf2_wen;

    S011HD1P_X256Y2D32 buffer1(buf1_dout, clock, 0, ~buf1_wen, buf1_addr, buf1_din);
    S011HD1P_X256Y2D32 buffer2(buf2_dout, clock, 0, ~buf2_wen, buf2_addr, buf2_din);

    wire [31:0] status_din, status_dout, base_din, base_dout;
    wire status_wen, base_wen;
    preg #(32, 0) status_r(clock, ~resetn, status_din, status_dout, status_wen);
    preg #(32, 0) base_r(clock, ~resetn, base_din, base_dout, base_wen);

    wire isMode800 = status_dout[0] == MODE800x600;

	// 像素计数值
    wire [10:0] x_next_din, x_next_dout;
    wire [9:0] y_next_din, y_next_dout;
    wire x_next_wen, y_next_wen;
    preg #(11, 1) x_next_cnt (clock, ~resetn, x_next_din, x_next_dout, x_next_wen);
    assign x_next_din = base_dout == 0 ? 0 : (x_next_dout == h_total ? 1 : x_next_dout + 11'd1);
    assign x_next_wen = vga_clk_en;

    preg #(10, 1) y_next_cnt (clock, ~resetn, y_next_din, y_next_dout, y_next_wen);
    assign y_next_din = base_dout == 0 ? 0 : ((y_next_dout == v_total & x_next_dout == h_total) ? 1 : y_next_dout + 10'd1);
    assign y_next_wen = (x_next_dout == h_total) & vga_clk_en;

    wire [10:0] x_din, x_dout;
    wire [9:0] y_din, y_dout;
    wire x_wen, y_wen;
    preg #(11, 1) x_cnt (clock, ~resetn, x_din, x_dout, x_wen);
    preg #(10, 1) y_cnt (clock, ~resetn, y_din, y_dout, y_wen);
    assign x_din = x_next_dout;
    assign x_wen = 1;
    assign y_din = y_next_dout;
    assign y_wen = 1;

	wire h_valid;
	wire v_valid;

	// 生成同步信号
	assign hsync = (x_dout > h_frontporch);
	assign vsync = (y_dout > v_frontporch);
	// 生成消隐信号
	assign h_valid = (x_dout > h_active) & (x_dout <= h_backporch);
	assign v_valid = (y_dout > v_active) & (y_dout <= v_backporch);
	wire valid = h_valid & v_valid;
	// 计算当前有效像素坐标
	wire [10:0] h_addr = x_next_dout - (h_active + 1);
	wire [9:0] v_addr = y_next_dout - (v_active + 1);
	// 设置输出的颜色值
    wire vga_idx_v = isMode800? v_addr[0] : v_addr[1];
    wire [9:0] vga_idx_h = isMode800 ? h_addr : h_addr[10:1];
    wire [10:0] pixel_h = x_dout - (h_active + 1);
    wire [9:0] pixel_v = y_dout - (v_active + 1);
    wire secondHalf = isMode800? pixel_h[0]: pixel_h[1];
    wire isBuf2 = isMode800? pixel_v[0]: pixel_v[1];
    wire [11:0] pixel = ({12{!isBuf2 & !secondHalf}} & buf1_dout[11:0]) | ({12{!isBuf2 & secondHalf}} & buf1_dout[23:12]) | 
                        ({12{isBuf2 & !secondHalf}} & buf2_dout[11:0]) | ({12{isBuf2 & secondHalf}} & buf2_dout[23:12]);

	assign vga_r = valid ? pixel[11:8] : 0;
	assign vga_g = valid ? pixel[7:4] : 0;
	assign vga_b = valid ? pixel[3:0] : 0;


    wire [10:0] vidx_din, vidx_dout, pre_vidx_din, pre_vidx_dout;
    wire vidx_wen, pre_vidx_wen, vaddr_wen;
    wire [19:0] vaddr_din, vaddr_dout;
    preg #(11, 0) axi_vidx(clock, ~resetn, vidx_din, vidx_dout, vidx_wen);
    preg #(11, 0) pre_axi_vidx(clock, ~resetn, pre_vidx_din, pre_vidx_dout, pre_vidx_wen);
    preg #(20, 0) axi_vaddr(clock, ~resetn, vaddr_din, vaddr_dout, vaddr_wen);
    assign vidx_din = y_dout == v_backporch ? 0 : vidx_dout + 1;
    assign vidx_wen = v_valid && (x_dout == 1) && vga_clk_en;
    assign vaddr_din = y_dout == v_backporch ? 0 : vaddr_dout + (isMode800 ? 20'd800 : 20'd400);
    assign vaddr_wen = v_valid && (x_dout == 1) && vga_clk_en;
    // wire [19:0] axi_vaddr = vidx_dout * (isMode800 ? 20'd800 : 20'd400);
    assign pre_vidx_din = vidx_dout;
    assign pre_vidx_wen = 1;

    // 更新buffer
    parameter[1:0] mIdle = 0, mRaddr = 1, mRdata = 2;
    wire [1:0] mstate_din, mstate_dout;
    wire mstate_wen;
    preg #(2, mIdle) mstate (clock, ~resetn, mstate_din, mstate_dout, mstate_wen);

    wire axi_idx_din, axi_idx_dout, axi_idx_wen;
    preg #(1,0) axi_idx(clock, ~resetn, axi_idx_din, axi_idx_dout, axi_idx_wen);
    wire mraddrEn_din, mraddrEn_dout, mraddrEn_wen, second_din, second_dout, second_wen, mrdataEn_din, mrdataEn_dout, mrdataEn_wen;
    preg #(1,0) mraddrEn(clock, ~resetn, mraddrEn_din, mraddrEn_dout, mraddrEn_wen);
    preg #(1,0) mrdataEn(clock, ~resetn, mrdataEn_din, mrdataEn_dout, mrdataEn_wen);
    preg #(1, 0) second (clock, ~resetn, second_din, second_dout, second_wen);
    wire [31:0] mraddr_din, mraddr_dout;
    wire mraddr_wen;
    preg #(32,0) mraddr(clock, ~resetn, mraddr_din, mraddr_dout, mraddr_wen);
    wire [8:0] axiOffset_din, axiOffset_dout;
    wire axiOffset_wen;
    preg #(9,0) axiOffset(clock, ~resetn, axiOffset_din, axiOffset_dout, axiOffset_wen);

    wire mIdle_s = (base_dout != 0) & (mstate_dout == mIdle & (isMode800 ? pre_vidx_dout != vidx_dout || second_dout == 1 : pre_vidx_dout[10:1] != vidx_dout[10:1]));
    wire mRaddr_s = mstate_dout == mRaddr & mraddrEn_dout & io_master_arready;
    wire mRdata_data = mstate_dout == mRdata & mrdataEn_dout & io_master_rvalid;
    wire mRdata_last = mRdata_data & io_master_rlast;
    wire mRdata_nlast = mRdata_data & !io_master_rlast;
    wire mhs_ar = mraddrEn_dout & io_master_arready;

    assign mstate_din = mIdle_s ? mRaddr : mRaddr_s ? mRdata : mRdata_last ? mIdle : mstate_dout;
    assign mstate_wen = mIdle_s | mRaddr_s | mRdata_last;
    assign axi_idx_din = isMode800? vidx_dout[0] : vidx_dout[1];
    assign axi_idx_wen = mIdle_s;
    assign mraddrEn_din = mIdle_s;
    assign mraddrEn_wen = mIdle_s | (mRaddr_s & mhs_ar);
    assign mraddr_din = base_dout + (isMode800? vaddr_dout + (second_dout ? 10'd400 : 0) : vaddr_dout/2) * 4;
    assign mraddr_wen = mIdle_s;

    assign mrdataEn_din = mRaddr_s;
    assign mrdataEn_wen = mRaddr_s | mRdata_last;
    assign axiOffset_din = mRdata_last ? 0 : axiOffset_dout + 1;
    assign axiOffset_wen = mRdata_data;
    assign second_din = ~second_dout;
    assign second_wen = mRdata_last;

    assign buf1_wen = axi_idx_dout == 0 & mRdata_data & (io_master_rresp == 0 | io_master_rresp == 1);
    assign buf1_addr = vga_idx_v == 0 ? vga_idx_h[9:1] : ((mRdata_data & second_dout & isMode800) ? 9'd200 + axiOffset_dout : axiOffset_dout);
    assign buf1_din = {io_master_rdata[55:52], io_master_rdata[47:44], io_master_rdata[39:36], io_master_rdata[23:20], io_master_rdata[15:12], io_master_rdata[7:4]};
    assign buf2_wen = axi_idx_dout == 1 & mRdata_data & (io_master_rresp == 0 | io_master_rresp == 1);
    assign buf2_addr = vga_idx_v == 1 ? vga_idx_h[9:1] : ((mRdata_data & second_dout & isMode800) ? 9'd200 + axiOffset_dout : axiOffset_dout);
    assign buf2_din = buf1_din;


    parameter [1:0] sIdle = 0, sWdata = 1, sWresp = 2, sRaddr = 1, sRdata = 2;
    wire [1:0] swstate_din, swstate_dout, srstate_din, srstate_dout;
    wire swstate_wen, srstate_wen;
    preg #(2, sIdle) swstate(clock, ~resetn, swstate_din, swstate_dout, swstate_wen);
    preg #(2, sIdle) srstate(clock, ~resetn, srstate_din, srstate_dout, srstate_wen);

    wire waddr_din, waddr_dout, waddr_wen;
    preg #(1, 0) waddr_r(clock, ~resetn, waddr_din, waddr_dout, waddr_wen);

    wire swaddrEn_din, swaddrEn_dout, swaddrEn_wen, swdataEn_din, swdataEn_dout, swdataEn_wen, sbEn_din, sbEn_dout, sbEn_wen;
    preg #(1,1) swaddrEn(clock, ~resetn, swaddrEn_din, swaddrEn_dout, swaddrEn_wen);
    preg #(1,0) swdataEn(clock, ~resetn, swdataEn_din, swdataEn_dout, swdataEn_wen);
    preg #(1,0) sbEn(clock, ~resetn, sbEn_din, sbEn_dout, sbEn_wen);
    wire [3:0] sbid_din, sbid_dout;
    wire sbid_wen;
    preg #(4,0) sbid(clock, ~resetn, sbid_din, sbid_dout, sbid_wen);

    wire sIdle_sw = swstate_dout == sIdle & swaddrEn_dout & io_slave_awvalid;
    wire sWdata_data = swstate_dout == sWdata & swdataEn_dout & io_slave_wvalid;
    wire sWdata_last = sWdata_data & io_slave_wlast;
    wire sWresp_sw = swstate_dout == sWresp & sbEn_dout & io_slave_bready;

    assign swstate_din = sIdle_sw ? sWdata : sWdata_last ? sWresp : sWresp_sw ? sIdle : swstate_dout;
    assign swstate_wen = sIdle_sw | sWdata_last | sWresp_sw;
    assign waddr_din = io_slave_awaddr[2];
    assign waddr_wen = sIdle_sw;
    assign swaddrEn_din = sWresp_sw;
    assign swaddrEn_wen = sIdle_sw | sWresp_sw;
    assign swdataEn_din = sIdle_sw;
    assign swdataEn_wen = sIdle_sw | sWdata_last;
    assign sbEn_din = sWdata_last;
    assign sbEn_wen = sWdata_last | sWresp_sw;
    assign sbid_din = io_slave_awid;
    assign sbid_wen = sIdle_sw;
    assign status_din = io_slave_wdata[31:0];
    assign status_wen = sWdata_data & !waddr_dout;
    assign base_din = io_slave_wdata[63:32];
    assign base_wen = sWdata_data & waddr_dout;

    wire sraddrEn_din, sraddrEn_dout, sraddrEn_wen, srdataEn_din, srdataEn_dout, srdataEn_wen, srlast_din, srlast_dout, srlast_wen;
    preg #(1,1) sraddrEn(clock, ~resetn, sraddrEn_din, sraddrEn_dout, sraddrEn_wen);
    preg #(1,0) srdataEn(clock, ~resetn, srdataEn_din, srdataEn_dout, srdataEn_wen);
    preg #(1,0) srlast(clock, ~resetn, srlast_din, srlast_dout, srlast_wen);
    wire [3:0] srid_din, srid_dout;
    wire srid_wen;
    preg #(4,0) srid(clock, ~resetn, srid_din, srid_dout, srid_wen);
    wire [63:0] srdata_din, srdata_dout;
    wire srdata_wen;
    preg #(64,0) srdata(clock, ~resetn, srdata_din, srdata_dout, srdata_wen);

    wire sIdle_sr = srstate_dout == sIdle & sraddrEn_dout & io_slave_arvalid;
    wire sRdata_data = srstate_dout == sRdata & srdataEn_dout & io_slave_rready;

    assign srstate_din = sIdle_sr ? sRdata : sIdle;
    assign srstate_wen = sIdle_sr | sRdata_data;
    assign sraddrEn_din = sRdata_data;
    assign sraddrEn_wen = sIdle_sr | sRdata_data;
    assign srdataEn_din = sIdle_sr;
    assign srdataEn_wen = sIdle_sr | sRdata_data;
    assign srid_din = io_slave_rid;
    assign srid_wen = sIdle_sr;
    assign srlast_din = sIdle_sr;
    assign srlast_wen = sIdle_sr | sRdata_data;
    assign srdata_din = io_slave_araddr[2:0] == 0? {32'h0, status_dout}: {base_dout, 32'h0};
    assign srdata_wen = sIdle_sr;

    assign io_master_awvalid    = 0;
    assign io_master_awaddr     = 0;
    assign io_master_awid       = 0;
    assign io_master_awlen      = 0;
    assign io_master_awsize     = 0;
    assign io_master_awburst    = 0;
    assign io_master_wvalid     = 0;
    assign io_master_wdata      = 0;
    assign io_master_wstrb      = 0;
    assign io_master_wlast      = 0;
    assign io_master_bready     = 0;
    assign io_master_arvalid    = mraddrEn_dout;
    assign io_master_araddr     = mraddr_dout;
    assign io_master_arid       = 0;
    assign io_master_arlen      = 8'd199;
    assign io_master_arsize     = 3;
    assign io_master_arburst    = 1;
    assign io_master_rready     = mrdataEn_dout;

    assign io_slave_awready = swaddrEn_dout;
    assign io_slave_wready  = swdataEn_dout;
    assign io_slave_bvalid  = sbEn_dout;
    assign io_slave_bresp   = 0;
    assign io_slave_bid     = sbid_dout;
    assign io_slave_arready = sraddrEn_dout;
    assign io_slave_rvalid  = srdataEn_dout;
    assign io_slave_rresp   = 1;
    assign io_slave_rdata   = srdata_dout;
    assign io_slave_rlast   = srlast_dout;
    assign io_slave_rid     = srid_dout;

endmodule