#include "vga.h"

extern Vnewtop* cpu;

SDL_Renderer *renderer = NULL;
SDL_Texture *texture = NULL;
uint32_t vmem[SCREEN_H][SCREEN_W];
SDL_Window *window = NULL;
extern int count;
void init_vga() {
#ifdef HSA_VGA
    memset(&cpu->newtop__DOT__mmio__DOT__vga[0], 0, sizeof(cpu->newtop__DOT__mmio__DOT__vga));
    SDL_Init(SDL_INIT_VIDEO);

    SDL_CreateWindowAndRenderer(800, 600, 0, &window, &renderer);

    SDL_SetWindowTitle(window, "noop");
    texture = SDL_CreateTexture(renderer, SDL_PIXELFORMAT_ARGB8888,
        SDL_TEXTUREACCESS_STATIC, SCREEN_W, SCREEN_H);

    memset(vmem, 0x0, sizeof(vmem));

    update_screen();
#endif
}


void vga_update_screen() {
#ifdef HAS_VGA
  if(cpu->newtop__DOT__mmio__DOT__vga_ctrl_1 == 0) return;
  update_screen();
  cpu->newtop__DOT__mmio__DOT__vga_ctrl_1 = 0;
#endif
}
void update_screen() {
#ifdef HAS_VGA
    memcpy(vmem, &(cpu->newtop__DOT__mmio__DOT__vga[0]), sizeof(vmem));
    SDL_UpdateTexture(texture, NULL, &vmem, SCREEN_W*4);
    SDL_RenderClear(renderer);
    SDL_RenderCopy(renderer, texture, NULL, NULL);
    SDL_RenderPresent(renderer);
#endif
}
