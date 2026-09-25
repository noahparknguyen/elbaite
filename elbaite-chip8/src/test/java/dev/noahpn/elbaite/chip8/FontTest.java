package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontTest {

    @Test
    void glyphsReturnsACopy() {
        Font.glyphs()[0] = 0x00;

        assertEquals(0xF0, Font.glyphs()[0]);
    }

    @Test
    void everyGlyphByteFitsInAByte() {
        int[] glyphs = Font.glyphs();
        for (int glyphByte : glyphs) {
            assertTrue(glyphByte >= 0 && glyphByte <= 0xFF,
                () -> "Glyph byte out of range: " + glyphByte);
        }
    }

    @Test
    void glyphTableHasFiveBytesPerGlyph() {
        assertEquals(16 * 5, Font.glyphs().length);
    }

    @Test
    void renderRowReadsMostSignificantBitFirst() {
        assertEquals("█ █  █ █", Font.renderRow(0xA5, 8));
        assertEquals("████", Font.renderRow(0xF0, 4));
    }
}
