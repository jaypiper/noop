module RF1FCIC_512X4M8WM1 (
    Q, CLK, CEN, GWEN, WEN, A, D, EMA, EMAW, EMAS, RET1N, 
    SO, SI, SE, DFTRAMBYP
);
parameter Bits = 4;
parameter Word_Depth = 512;
parameter Add_Width = 9;
parameter Wen_Width = 4;

output reg [Bits-1:0] Q;
input                 CLK;
input                 CEN;
input                 GWEN;
input [Wen_Width-1:0] WEN;
input [Add_Width-1:0] A;
input [Bits-1:0]      D;

input [2:0] EMA;
input [1:0] EMAW;
input  EMAS;
input  RET1N;
output [1:0] SO;
input [1:0] SI;
input  SE;
input  DFTRAMBYP;

wire cen  = ~CEN;
wire wen  = ~GWEN;
wire [Bits-1:0] bwen = ~WEN;

reg [Bits-1:0] ram [0:Word_Depth-1];
always @(posedge CLK) begin
    if(cen && wen) begin
        ram[A] <= (D & bwen) | (ram[A] & ~bwen);
    end
    Q <= cen && !wen ? ram[A] : {$random}[3:0];
end

endmodule
