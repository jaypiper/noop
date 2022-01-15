module vga_ctrl_axi(
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

    input vga_clk,
	output hsync,
	output vsync,
	output [3:0]vga_r,
	output [3:0]vga_g,
	output [3:0]vga_b,
    output [8:0] out_offset,
    output [19:0] out_vaddr,
    output [10:0] out_h_addr,
	output [9:0] out_v_addr,
    output out_vga_idx_v,
    output [9:0] out_vga_idx_h,
    output [10:0] out_axi_vidx,
    output [19:0] out_axi_vaddr,
    output [10:0] out_pre_axi_vidx
);

	parameter h_frontporch = 128;
	parameter h_active = 216;
	parameter h_backporch = 1016;
	parameter h_total = 1056;

	parameter v_frontporch = 4;
	parameter v_active = 27;
	parameter v_backporch = 627;
	parameter v_total = 628;

    parameter MODE800x600 = 0;
    parameter MODE400x300 = 1;

    reg [11:0] buffer[1:0][799:0];
    reg [31:0] vga_ctrl_reg [1:0];
    wire mode = vga_ctrl_reg[0][0];
    wire isMode800 = mode == MODE800x600;
    wire [31: 0]vga_base = vga_ctrl_reg[1];

	// 像素计数值
	reg [10:0] x_cnt=1;
	reg [9:0] y_cnt=1;
	wire h_valid;
	wire v_valid;

	always @(posedge vga_clk) // 行像素计数
		if (!resetn)  x_cnt <= 1;
		else begin
			if (x_cnt == h_total) begin
				x_cnt <= 1;
            end
			else
				x_cnt <= x_cnt + 10'd1;
		end

	always @(posedge vga_clk) // 列像素计数
		if (!resetn) y_cnt <= 1;
		else begin
			if (y_cnt == v_total & x_cnt == h_total)
				y_cnt <= 1;
			else if (x_cnt == h_total)
				y_cnt <= y_cnt + 10'd1;
		end

	// 生成同步信号
	assign hsync = (x_cnt > h_frontporch);
	assign vsync = (y_cnt > v_frontporch);
	// 生成消隐信号
	assign h_valid = (x_cnt > h_active) & (x_cnt <= h_backporch);
	assign v_valid = (y_cnt > v_active) & (y_cnt <= v_backporch);
	wire valid = h_valid & v_valid;
	// 计算当前有效像素坐标
	wire [10:0] h_addr = x_cnt - 10'd217;
	wire [9:0] v_addr = y_cnt - 10'd28;
	// 设置输出的颜色值
    wire vga_idx_v = MODE800x600? v_addr & 1 : v_addr[9:1] & 1;
    wire [9:0] vga_idx_h = MODE800x600? h_addr : h_addr[10:1];
	assign vga_r = valid ? buffer[vga_idx_v][vga_idx_h][11:8] : 0;
	assign vga_g = valid ? buffer[vga_idx_v][vga_idx_h][7:4] : 0;
	assign vga_b = valid ? buffer[vga_idx_v][vga_idx_h][3:0] : 0;

    reg [10:0] axi_vidx;
    reg [19:0] axi_vaddr;
    always @(posedge vga_clk) begin
        if(!resetn) begin
            axi_vaddr <= 0; axi_vidx <= 0;
        end
        else begin
            if(v_valid && x_cnt == 1) begin
                axi_vidx <= y_cnt == v_backporch ? 0 : axi_vidx + 1;
                axi_vaddr <= y_cnt == v_backporch ? 0 : axi_vaddr + (isMode800 ? 800 : 400);
            end
        end
    end

    // 更新buffer
    parameter[1:0] mIdle = 0, mRaddr = 1, mRdata = 2;
    wire[9:0] v_num = (isMode800)? 600 : 300;

    reg [1:0] mstate = mIdle;
    reg axi_idx = 0;
    reg mraddrEn         = 0;
    reg [31:0] mraddr    = 0;
    reg mrdataEn         = 0;
    reg [8:0] axiOffset = 0;
    reg second = 0;
    reg [10:0] pre_axi_vidx;

    assign out_offset = axiOffset;
    assign out_vaddr = axi_vaddr;
    assign out_h_addr = h_addr;
    assign out_v_addr = v_addr;
    assign out_vga_idx_h = vga_idx_h;
    assign out_vga_idx_v = vga_idx_v;
    assign out_axi_vidx = axi_vidx;
    assign out_axi_vaddr = axi_vaddr;
    assign out_pre_axi_vidx = pre_axi_vidx;


    always @(posedge clock) begin
        if (!resetn) begin
            mraddrEn <= 0;
            mrdataEn <= 0;
            mstate <= mIdle;
            axiOffset <= 0;
            pre_axi_vidx <= 0;
            second <= 0;
        end else if (mstate == mIdle & (MODE800x600 ? pre_axi_vidx != axi_vidx || second == 1 : pre_axi_vidx[10:1] != axi_vidx[10:1])) begin
            pre_axi_vidx <= axi_vidx;
            mstate <= mRaddr;
            axi_idx <= MODE800x600? axi_vidx[0] : axi_vidx[1];
            mraddrEn <= 1;
            mraddr   <= vga_base + (MODE800x600? axi_vaddr : axi_vaddr/2 + second ? 10'd400 : 0) * 4;
        end else if (mstate == mRaddr) begin
            if(mraddrEn && io_master_arready) begin
                mstate <= mRdata;
                mraddrEn <= 0;
                mrdataEn <= 1;
            end
        end else if (mstate == mRdata) begin
            if(mrdataEn && io_master_rvalid) begin
                if(io_master_rresp == 0 | io_master_rresp == 1) begin
                    buffer[axi_idx][(second ? 10'd400 : 0) + axiOffset] <= {io_master_rdata[23:20], io_master_rdata[15:12], io_master_rdata[7:4]};
                    buffer[axi_idx][(second ? 10'd400 : 0) + axiOffset + 1] <= {io_master_rdata[55:52], io_master_rdata[47:44], io_master_rdata[39:36]};
                end
                if(io_master_rlast) begin
                    mrdataEn <= 0;
                    mstate <= mIdle;
                    axiOffset <= 0;
                    second <= ~second;
                end else begin
                    axiOffset <= axiOffset + 2;
                end
                    
            end
        end
    end

    parameter [1:0] sIdle = 0, sWdata = 1, sWresp = 2, sRaddr = 1, sRdata = 2;
    reg [1:0] swstate = sIdle;
    reg [1:0] srstate = sIdle;
    reg waddr_r = 0;

    reg swaddrEn = 1;
    reg swdataEn = 0;
    reg sbEn     = 0;
    reg [1:0] sbresp = 0;
    reg [3:0] sbid = 0;

    always @(posedge clock) begin  // slave write channel
        if(!resetn) begin
            {vga_ctrl_reg[0], vga_ctrl_reg[1]} <= 0;
            swstate <= sIdle;
            swaddrEn <= 1;
            swdataEn <= 0;
        end else if (swstate == sIdle) begin
            if(swaddrEn & io_slave_awvalid) begin
                swstate <= sWdata;
                swaddrEn <= 0;
                swdataEn <= 1;
                waddr_r <= io_slave_awaddr[2];
                sbid <= io_slave_awid;
            end
        end else if (swstate == sWdata) begin
            if(swdataEn & io_slave_wvalid) begin
                vga_ctrl_reg[waddr_r] <= waddr_r ? io_slave_wdata[63:32]: io_slave_wdata[31:0];
                if(io_slave_wlast) begin
                    swstate <= sWresp;
                    swdataEn <= 0;
                    sbresp <= 0;
                    sbEn <= 1;
                end
            end
        end else if (swstate == sWresp) begin
            if(sbEn & io_slave_bready) begin
                swstate <= sIdle;
                sbEn <= 0;
                swaddrEn <= 1;
            end
        end
    end

    reg raddr_r = 0;
    reg sraddrEn = 1;
    reg srdataEn = 0;
    reg [63:0] srdata = 0;
    reg srlast = 0;
    reg [3:0] srid = 0;
    always @(posedge clock) begin // slave read channel
        if(!resetn) begin
            srstate <= sIdle;
            sraddrEn <= 1;
            srdataEn <= 0;
            srlast <= 0;
        end else if (srstate == sIdle) begin
            if(sraddrEn & io_slave_arvalid) begin
                srstate <= sRdata;
                sraddrEn <= 0;
                srdataEn <= 1;
                srdata <= io_slave_araddr[2:0] == 0? vga_ctrl_reg[0]: {vga_ctrl_reg[1], 32'h0};
                srlast <= 1;
                // raddr_r <= io_slave_araddr[0];
                srid <= io_slave_arid;
            end
        end else if (srstate == sRdata) begin
            if(srdataEn & io_slave_rready) begin
                srstate <= sIdle;
                sraddrEn <= 1;
                srdataEn <= 0;
                srlast <= 0;
            end
        end
    end

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
    assign io_master_arvalid    = mraddrEn;
    assign io_master_araddr     = mraddr;
    assign io_master_arid       = 0;
    assign io_master_arlen      = 8'd199;
    assign io_master_arsize     = 3;
    assign io_master_arburst    = 1;
    assign io_master_rready     = mrdataEn;

    assign io_slave_awready = swaddrEn;
    assign io_slave_wready  = swdataEn;
    assign io_slave_bvalid  = sbEn;
    assign io_slave_bresp   = sbresp;
    assign io_slave_bid     = sbid;
    assign io_slave_arready = sraddrEn;
    assign io_slave_rvalid  = srdataEn;
    assign io_slave_rresp   = 1;
    assign io_slave_rdata   = srdata;
    assign io_slave_rlast   = srlast;
    assign io_slave_rid     = srid;
endmodule