package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontTest {

    @Test
    void renderRowReadsMostSignificantBitFirst() {
        assertEquals("█ █  █ █", Font.renderRow(0xA5, 8));
        assertEquals("████", Font.renderRow(0xF0, 4));
    }

    @Test
    void everyGlyphByteFitsInAByte() {
        for (int glyphByte : Font.GLYPHS) {
            assertTrue(glyphByte >= 0 && glyphByte <= 0xFF,
                () -> "Glyph byte out of range: " + glyphByte);
        }
    }

    @Test
    void glyphTableHasFiveBytesPerGlyph() {
        assertEquals(16 * 5, Font.GLYPHS.length);
    }
}
