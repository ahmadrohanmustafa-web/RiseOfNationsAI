package com.riseofnations.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Tiny text helpers so the screens only use the one verified drawing call: text(font, string, x, y, color, shadow). */
final class ScreenText {

    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFAAAAAA;
    static final int RED = 0xFFFF5555;
    static final int GOLD = 0xFFFFD36A;

    private ScreenText() { }

    static void left(GuiGraphicsExtractor g, Font font, String text, int x, int y, int argb) {
        g.text(font, text, x, y, argb, true);
    }

    static void centered(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, int argb) {
        g.text(font, text, centerX - font.width(text) / 2, y, argb, true);
    }
}
