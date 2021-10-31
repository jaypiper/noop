module newtop(
    input clock,
    input reset
);

    ysyxSoCFull socfull( // @[:freechips.rocketchip.system.LvNAFPGAConfigsidewinder.fir@172327.2]
        .clock(clock),
        .reset(reset)
    );

endmodule
