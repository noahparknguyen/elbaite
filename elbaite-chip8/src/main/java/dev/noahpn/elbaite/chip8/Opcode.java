package dev.noahpn.elbaite.chip8;

/**
 * One CHIP-8 instruction: sixteen bits, four nibbles, decoded into the groupings the
 * spec names.
 *
 * @param value the raw instruction, {@code 0x0000} to {@code 0xFFFF}
 */
public record Opcode(int value) {

    private static final int VALUE_MAX = 0xFFFF;

    /**
     * Creates an opcode from a raw instruction word.
     *
     * @throws IllegalArgumentException if {@code value} is outside {@code 0x0000} to
     *                                  {@code 0xFFFF}
     */
    public Opcode {
        if (value < 0 || value > VALUE_MAX) {
            throw new IllegalArgumentException(
                "CHIP-8 opcode out of range: " + value + " (valid: 0x0000-0xFFFF)");
        }
    }

    /**
     * Returns the first nibble, which selects the instruction family.
     *
     * @return the high nibble, {@code 0x0} to {@code 0xF}
     */
    public int high() {
        return (value >>> 12) & 0xF;
    }

    /**
     * Returns the second nibble, usually a register number.
     *
     * @return the X nibble, {@code 0x0} to {@code 0xF}
     */
    public int x() {
        return (value >>> 8) & 0xF;
    }

    /**
     * Returns the third nibble, usually a second register number.
     *
     * @return the Y nibble, {@code 0x0} to {@code 0xF}
     */
    public int y() {
        return (value >>> 4) & 0xF;
    }

    /**
     * Returns the fourth nibble, a four-bit value.
     *
     * @return the N nibble, {@code 0x0} to {@code 0xF}
     */
    public int n() {
        return value & 0x000F;
    }

    /**
     * Returns the low byte, an eight-bit value.
     *
     * @return the NN byte, {@code 0x00} to {@code 0xFF}
     */
    public int nn() {
        return value & 0x00FF;
    }

    /**
     * Returns the low twelve bits, an address.
     *
     * @return the NNN value, {@code 0x000} to {@code 0xFFF}
     */
    public int nnn() {
        return value & 0x0FFF;
    }
}
