package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MemoryTest {

    private Memory memory;

    @BeforeEach
    void setup() {
        memory = new Memory();
    }

    @Test
    void readReturnsUnsignedValue() {
        memory.write(0x000, 0xFF);
        memory.write(0x001, 0x80);
        assertEquals(255, memory.read(0x000));
        assertEquals(128, memory.read(0x001));
    }

    @Test
    void fontIsLoadedAtFontStart() {
        // The first glyph's five bytes are at FONT_START.
        for (int i = 0; i < 5; i++) {
            assertEquals(
                Font.GLYPHS[i],
                memory.read(Memory.FONT_START + i)
            );
        }

        // Memory outside the font block is still zero.
        assertEquals(0, memory.read(0x000));
        assertEquals(0, memory.read(Memory.FONT_START - 1));
        assertEquals(0, memory.read(Memory.FONT_START + Font.GLYPHS.length));
    }

    @Test
    void outOfRangeAddressThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> memory.read(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.read(Memory.SIZE));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.write(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.write(Memory.SIZE, 0));
    }

    @Test
    void outOfRangeValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> memory.write(0x200, 300));
        assertThrows(IllegalArgumentException.class, () -> memory.write(0x200, -5));
    }

}
