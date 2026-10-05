package dev.noahpn.elbaite.chip8;

/**
 * The six behaviours where CHIP-8 interpreters disagree, each a {@code boolean} whose {@code true}
 * value is the quirks test's {@code ON}.
 *
 * <p>Programs were written for one machine or another, so the only right answer is the
 * one for the machine a program was written for. A {@link Cpu} reads five of the six; an
 * {@link Emulator} reads {@link #displayWait()}. The same record is given to both.
 *
 * @param vfReset     {@code true} if {@code 8XY1}, {@code 8XY2} and {@code 8XY3} set {@code VF} to
 *                    zero
 * @param memory      {@code true} if {@code FX55} and {@code FX65} leave {@code I} at
 *                    {@code I + X + 1}
 * @param displayWait {@code true} if a draw ends its frame
 * @param clipping    {@code true} if the part of a sprite past an edge is dropped; {@code false}
 *                    wraps it to the far edge
 * @param shifting    {@code true} if {@code 8XY6} and {@code 8XYE} shift {@code VX} in place and
 *                    ignore {@code VY}
 * @param jumping     {@code true} if {@code BNNN} adds {@code VX} instead of {@code V0}, {@code X}
 *                    being the opcode's second nibble
 */
public record Quirks(boolean vfReset, boolean memory, boolean displayWait,
                     boolean clipping, boolean shifting, boolean jumping) {

    /**
     * The original interpreter, Joseph Weisbecker's for the COSMAC VIP in 1977: the default.
     */
    public static final Quirks VIP = new Quirks(true, true, true, true, false, false);

    /**
     * SUPER-CHIP, from the HP-48 calculators, as modern emulators run it: without display wait.
     */
    public static final Quirks SUPER_CHIP = new Quirks(false, false, false, true, true, true);

    /**
     * Octo, John Earnest's CHIP-8 development tool.
     */
    public static final Quirks OCTO = new Quirks(false, true, false, false, false, false);

    /**
     * Returns the preset with the given name.
     *
     * @param name one of {@code "vip"}, {@code "schip"} or {@code "octo"}; exact and lowercase
     * @return the preset
     * @throws IllegalArgumentException if the name is not one of the three
     */
    public static Quirks forName(String name) {
        return switch (name) {
            case "vip" -> VIP;
            case "schip" -> SUPER_CHIP;
            case "octo" -> OCTO;
            default -> throw new IllegalArgumentException(
                "CHIP-8 unknown quirks preset: " + name + " (valid: vip, schip, octo)");
        };
    }
}
