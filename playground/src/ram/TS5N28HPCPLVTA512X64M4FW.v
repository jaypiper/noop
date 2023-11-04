module TS5N28HPCPLVTA512X64M4FW(
    Q, CLK, CEB, WEB, BWEB, A, D
);
parameter Bits = 64;
parameter Word_Depth = 512;
parameter Add_Width = 9;
parameter Wen_Width = 64;

output reg [Bits-1:0] Q;
input                 CLK;
input                 CEB;
input                 WEB;
input [Wen_Width-1:0] BWEB;
input [Add_Width-1:0] A;
input [Bits-1:0]      D;

wire cen  = ~CEB;
wire wen  = ~WEB;
wire [Wen_Width-1:0] bwen = ~BWEB;

reg [Bits-1:0] ram [0:Word_Depth-1];
always @(posedge CLK) begin
    if(cen && wen) begin
        ram[A] <= (D & bwen) | (ram[A] & ~bwen);
    end
    Q <= cen && !wen ? ram[A] : {2{$random}};
end

endmodule
