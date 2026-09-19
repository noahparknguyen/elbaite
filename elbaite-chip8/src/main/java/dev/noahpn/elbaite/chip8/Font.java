package dev.noahpn.elbaite.chip8;

/**
 * The CHIP-8 built-in font: sixteen glyphs, one per hex digit, five bytes each.
 *
 * <p>A glyph is a sprite, a small picture stored one byte per row and one bit per pixel,
 * most significant bit leftmost. Glyphs are four pixels wide, so only the top four bits
 * of each byte carry a pixel and the low four are always zero.
 *
 * <p>This is deliberately not the standard CHIP-8 font. Seven glyphs are redrawn, which
 * is safe because nothing reads a glyph's shape: {@code FX29} returns a glyph's address
 * and {@code DXYN} draws whatever bytes it finds there.
 */
public final class Font {

    public static final int GLYPH_COUNT = 16;

    public static final int GLYPH_BYTES = 5;

    public static final int GLYPH_WIDTH = 4;

    /**
     * The glyph data, five bytes per glyph, glyph {@code 0} first.
     *
     * <p>Glyph {@code n} occupies indices {@code n * GLYPH_BYTES} through
     * {@code n * GLYPH_BYTES + GLYPH_BYTES - 1}. Every value is {@code 0} to
     * {@code 255}.
     */
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

    private Font() {
    }

    /**
     * Renders one row of a sprite as a string of {@code #} and {@code .} characters.
     *
     * <p>Character {@code 0} of the result is bit 7 of {@code spriteByte}, character
     * {@code 1} is bit 6, and so on, so the most significant bit is the leftmost pixel.
     * Bits below {@code width} are discarded, which is how a four-wide glyph reads only
     * the top nibble of its byte.
     *
     * @param spriteByte the row to render, {@code 0} to {@code 255}
     * @param width      how many pixels to render, at most {@code 8}
     * @return a string of exactly {@code width} characters
     */
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

    /**
     * Prints one glyph to standard output: its hex label on a line of its own, then the
     * glyph's five rows at {@link #GLYPH_WIDTH}.
     *
     * @param digit the glyph to print, {@code 0} to {@code 15}
     */
    public static void printGlyph(int digit) {
        IO.println(Integer.toHexString(digit).toUpperCase());
        int start = digit * GLYPH_BYTES;
        for (int i = start; i < start + GLYPH_BYTES; i++) {
            IO.println(renderRow(GLYPHS[i], GLYPH_WIDTH));
        }
    }

    static void main() {
        IO.println(renderRow(0xA5, 8));
        for (int i = 0; i < GLYPH_COUNT; i++) {
            printGlyph(i);
            IO.println();
        }
    }
}
