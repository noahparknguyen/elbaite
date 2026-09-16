package dev.noahpn.elbaite.chip8;

public class Font {
    public static final int GLYPH_COUNT = 16;
    public static final int GLYPH_BYTES = 5;
    public static final int GLYPH_WIDTH = 4;
    public static final int[] GLYPHS = {
        0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
        0xE0, 0x20, 0x20, 0x20, 0xF0, // 1
        0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
        0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
        0x90, 0x90, 0xF0, 0x10, 0x10, // 4
        0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
        0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
        0xF0, 0x90, 0x10, 0x10, 0x10, // 7
        0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
        0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
        0x60, 0x90, 0xF0, 0x90, 0x90, // A
        0xF0, 0x90, 0xE0, 0x90, 0xF0, // B
        0x70, 0x80, 0x80, 0x80, 0x70, // C
        0xE0, 0x90, 0x90, 0x90, 0xE0, // D
        0xF0, 0x80, 0xE0, 0x80, 0xF0, // E
        0xF0, 0x80, 0xE0, 0x80, 0x80  // F
    };

    public static String renderRow(int spriteByte, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            int bit = (spriteByte >> i) & 1;
            if (bit != 0) {
                sb.append("#");
            } else {
                sb.append(".");
            }
        }
        return sb.substring(0, width);
    }

    public static void printGlyph(int digit) {
        IO.println(Integer.toHexString(digit).toUpperCase());
        int start = digit * GLYPH_BYTES;
        for (int i = start; i < start + GLYPH_BYTES; i++) {
            IO.println(renderRow(GLYPHS[i], GLYPH_WIDTH));
        }
    }

    static void main(String[] args) {
        IO.println(renderRow(0xA5, 8));
        for (int i = 0; i < GLYPH_COUNT; i++) {
            printGlyph(i);
            IO.println();
        }
    }
}
