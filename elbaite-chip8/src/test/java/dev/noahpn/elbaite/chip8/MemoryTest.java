package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryTest {

    private Memory memory;

    @BeforeEach
    void setup() {
        memory = new Memory();
    }

    // --- Construction ---

    @Test
    void fontLoadsAtFontStart() {
        for (int i = 0; i < Font.GLYPH_BYTES; i++) {
            assertEquals(Font.GLYPHS[i], memory.read(0x050 + i));
        }

        // Everything outside the font block is still zero.
        assertEquals(0x00, memory.read(0x000));
        assertEquals(0x00, memory.read(0x04F));
        assertEquals(0x00, memory.read(0x050 + Font.GLYPHS.length));
    }

    // --- ROM loading ---

    @Test
    void romLoadsAtProgramStart() {
        byte[] rom = {0x12, 0x34, 0x56};

        memory.loadRom(rom);

        assertEquals(0x12, memory.read(0x200));
        assertEquals(0x34, memory.read(0x201));
        assertEquals(0x56, memory.read(0x202));
        assertEquals(0x00, memory.read(0x1FF));
    }

    @Test
    void romLoadsHighBytesAsUnsigned() {
        byte[] rom = {(byte) 0xFF, (byte) 0xA2};

        memory.loadRom(rom);

        assertEquals(0xFF, memory.read(0x200));
        assertEquals(0xA2, memory.read(0x201));
    }

    @Test
    void oversizedRomThrows() {
        byte[] rom = new byte[Memory.SIZE - Memory.PROGRAM_START + 1];

        assertThrows(IllegalArgumentException.class, () -> memory.loadRom(rom));
    }

    // --- Reading and writing ---

    @Test
    void readReturnsUnsignedValue() {
        memory.write(0x000, 0xFF);
        memory.write(0x001, 0x80);

        // 0xFF is 255 and 0x80 is 128, never -1 and -128.
        assertEquals(0xFF, memory.read(0x000));
        assertEquals(0x80, memory.read(0x001));
    }

    @Test
    void outOfRangeAddressThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> memory.read(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.read(0x1000));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.write(-1, 0x00));
        assertThrows(IndexOutOfBoundsException.class, () -> memory.write(0x1000, 0x00));
    }

    @Test
    void outOfRangeValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> memory.write(0x200, 256));
        assertThrows(IllegalArgumentException.class, () -> memory.write(0x200, -1));
    }
}
