module MuxOH #(NR_KEY, KEY_LEN, DATA_LEN, HAS_DEFAULT) (
    output reg [DATA_LEN-1:0] out,
    input [KEY_LEN-1:0] key,
    input [DATA_LEN-1:0] default_out,
    input [NR_KEY*(KEY_LEN + DATA_LEN)-1:0] lut
);

wire [KEY_LEN-1:0] key_list [NR_KEY-1:0];
wire [DATA_LEN-1:0] data_list [NR_KEY-1:0];
localparam PAIR_LEN = KEY_LEN + DATA_LEN;

generate
    for(genvar n = 0; n < NR_KEY; n = n + 1) begin
        assign data_list[n] = lut[PAIR_LEN*(n+1)-1 : PAIR_LEN*n][DATA_LEN-1 : 0];
        assign key_list[n] = lut[PAIR_LEN*(n+1)-1 : PAIR_LEN*n][PAIR_LEN-1 : DATA_LEN];
    end
endgenerate

always @(*) begin
    reg [DATA_LEN-1 : 0] lut_out = 0;
    reg hit = 0;
    for (integer i = 0; i < NR_KEY; i = i + 1) begin
        lut_out |= {DATA_LEN{key == key_list[i]}} & data_list[i];
        hit |= key == key_list[i];
    end
    if (!HAS_DEFAULT) out = lut_out;
    else out = (hit ? lut_out : default_out);
end

endmodule