package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpcodeTest {

    @Test
    void opcodeSplitsIntoNibbles() {
        Opcode opcode = new Opcode(0xD01F);

        assertEquals(0xD, opcode.high());
        assertEquals(0x0, opcode.x());
        assertEquals(0x1, opcode.y());
        assertEquals(0xF, opcode.n());
        assertEquals(0x1F, opcode.nn());
        assertEquals(0x01F, opcode.nnn());

        // A second instruction from the ROM walk. 0xD01F separates hi, X, Y and N but
        // its X is zero, so nn and nnn coincide; 0xA22A separates nn from nnn, and its
        // four nibbles all differ, so no two accessors can be swapped undetected.
        Opcode second = new Opcode(0xA22A);

        assertEquals(0xA, second.high());
        assertEquals(0x2, second.x());
        assertEquals(0x2, second.y());
        assertEquals(0xA, second.n());
        assertEquals(0x2A, second.nn());
        assertEquals(0x22A, second.nnn());
    }

    @Test
    void outOfRangeOpcodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Opcode(0x10000));
        assertThrows(IllegalArgumentException.class, () -> new Opcode(-1));
    }
}
