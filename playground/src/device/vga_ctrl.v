module vga_ctrl(
	input pclk,
	input reset,
	input [31:0]vga_data,
	output [9:0]h_addr,
	output [9:0]v_addr,
	output hsync,
	output vsync,
	output [3:0]vga_r,
	output [3:0]vga_g,
	output [3:0]vga_b);

	parameter h_frontporch = 96;
	parameter h_active = 144;
	parameter h_backporch = 784;
	parameter h_total = 800;

	parameter v_frontporch = 2;
	parameter v_active = 35;
	parameter v_backporch = 515;
	parameter v_total = 525;

	// 像素计数值
	reg [9:0] x_cnt=1;
	reg [9:0] y_cnt=1;
	reg [9:0] x_cnt_next=1;
	reg [9:0] y_cnt_next=1;
	wire h_valid;
	wire v_valid;

	always @(posedge reset or posedge pclk) // 行像素计数
		if (reset == 1'b1)  x_cnt_next <= 1;
		else begin
			if (x_cnt_next == h_total)
				x_cnt_next <= 1;
			else
				x_cnt_next <= x_cnt_next + 10'd1;
		end

	always @(posedge pclk) // 列像素计数
		if (reset == 1'b1) y_cnt_next <= 1;
		else begin
			if (y_cnt_next == v_total & x_cnt_next == h_total)
				y_cnt_next <= 1;
			else if (x_cnt_next == h_total)
				y_cnt_next <= y_cnt_next + 10'd1;
		end
	always @(posedge pclk) begin
		if (reset == 1'b1)  x_cnt <= 1;
		x_cnt <= x_cnt_next;
		y_cnt <= y_cnt_next;
	end
	// 生成同步信号
	assign hsync = (x_cnt > h_frontporch);
	assign vsync = (y_cnt > v_frontporch);
	// 生成消隐信号
	assign h_valid = (x_cnt > h_active) & (x_cnt <= h_backporch);
	assign v_valid = (y_cnt > v_active) & (y_cnt <= v_backporch);
	wire valid = h_valid & v_valid;
	// 计算当前有效像素坐标
	assign h_addr = x_cnt_next - 10'd145;
	assign v_addr = y_cnt_next - 10'd36;
	// 设置输出的颜色值
	assign vga_r = valid ? vga_data[23:20] : 0;
	assign vga_g = valid ? vga_data[15:12] : 0;
	assign vga_b = valid ? vga_data[7:4] : 0;

endmodule