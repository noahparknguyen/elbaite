package dev.noahpn.elbaite.chip8;

/**
 * The CHIP-8 CPU's state between instructions: sixteen general registers, the index
 * register, and the program counter.
 *
 * <p>The three are different widths and each is guarded separately. A general register
 * holds one byte, the index register holds sixteen bits, and the program counter holds a
 * twelve-bit address. Nothing about {@code int} enforces any of that.
 *
 * <p>Like {@link Memory}, this class rejects an out-of-range value rather than
 * truncating or wrapping it. Nothing here executes instructions; it only holds state.
 */
public final class Cpu {

    public static final int REGISTER_COUNT = 16;

    private static final int REGISTER_MAX = 0xFF;
    private static final int INDEX_MAX = 0xFFFF;
    private static final int ADDRESS_MAX = 0xFFF;

    private final int[] registers = new int[REGISTER_COUNT];
    private int index;
    private int programCounter = Memory.PROGRAM_START;

    /**
     * Returns the value held in one of the sixteen general registers.
     *
     * @param register which register, {@code 0} for {@code V0} through {@code 15} for
     *                 {@code VF}
     * @return the register value, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code register} is outside {@code 0} to
     *                                   {@code 15}
     */
    public int readRegister(int register) {
        checkRegister(register);

        return registers[register];
    }

    /**
     * Stores a byte value in one of the sixteen general registers.
     *
     * <p>{@code VF} is not special here. Instructions use it to report a carry, borrow
     * or collision, but it is an ordinary register that accepts any byte.
     *
     * <p>The value must fit in a byte, and this method rejects rather than truncates.
     *
     * @param register which register, {@code 0} for {@code V0} through {@code 15} for
     *                 {@code VF}
     * @param value    the value to store, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code register} is outside {@code 0} to
     *                                   {@code 15}
     * @throws IllegalArgumentException  if {@code value} is outside {@code 0} to
     *                                   {@code 255}
     */
    public void writeRegister(int register, int value) {
        checkRegister(register);
        if (value < 0 || value > REGISTER_MAX) {
            throw new IllegalArgumentException(
                "CHIP-8 register value out of range: " + value + " (valid: 0x00-0xFF)");
        }

        registers[register] = value;
    }

    /**
     * Returns the index register, which instructions use to point at memory.
     *
     * @return the index register, {@code 0x0000} to {@code 0xFFFF}
     */
    public int getIndexRegister() {
        return index;
    }

    /**
     * Sets the index register.
     *
     * <p>It is sixteen bits wide rather than twelve, so it may legally hold a value
     * above the {@code 0xFFF} top of the address space. That is why an out-of-range
     * value here is an argument error rather than an addressing one.
     *
     * @param value the value to store, {@code 0x0000} to {@code 0xFFFF}
     * @throws IllegalArgumentException if {@code value} is outside {@code 0x0000} to
     *                                  {@code 0xFFFF}
     */
    public void setIndexRegister(int value) {
        if (value < 0 || value > INDEX_MAX) {
            throw new IllegalArgumentException(
                "Index register value out of range: " + value + " (valid: 0x0000-0xFFFF)");
        }

        index = value;
    }

    /**
     * Returns the address of the next instruction.
     *
     * @return the program counter, {@code 0x000} to {@code 0xFFF}
     */
    public int getProgramCounter() {
        return programCounter;
    }

    /**
     * Sets the address of the next instruction.
     *
     * @param address the address to jump to, {@code 0x000} to {@code 0xFFF}
     * @throws IndexOutOfBoundsException if {@code address} is outside {@code 0x000} to
     *                                   {@code 0xFFF}
     */
    public void setProgramCounter(int address) {
        if (address < 0 || address > ADDRESS_MAX) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 address out of range: " + address + " (valid: 0x000-0xFFF)");
        }

        programCounter = address;
    }

    /**
     * Moves the program counter on to the next instruction, two bytes further along.
     *
     * <p>Two, because every CHIP-8 instruction is exactly two bytes.
     *
     * @throws IndexOutOfBoundsException if the program counter is already at
     *                                   {@code 0xFFE} or {@code 0xFFF}, where advancing
     *                                   would leave the address space
     */
    public void advanceProgramCounter() {
        if (programCounter > ADDRESS_MAX - 2) {
            throw new IndexOutOfBoundsException(
                "Program counter cannot advance past 0xFFF from " + programCounter);
        }

        programCounter += 2;
    }

    /**
     * Prints the whole register file to standard output: the sixteen general registers
     * four to a line, then the index register and the program counter on lines of their
     * own.
     *
     * <p>Values are hex, two digits for a general register and four for {@code I} and
     * {@code PC}.
     */
    public void dump() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < REGISTER_COUNT; i++) {
            sb.append(String.format("V%X %02X", i, registers[i]));

            if ((i + 1) % 4 == 0) {
                sb.append(System.lineSeparator());
            } else {
                sb.append("  ");
            }
        }

        sb.append(String.format("I  %04X%n", index));
        sb.append(String.format("PC %04X%n", programCounter));

        IO.print(sb.toString());
    }

    private void checkRegister(int register) {
        if (register < 0 || register >= REGISTER_COUNT) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 register out of range: " + register + " (valid: V0-VF)");
        }
    }

    static void main() {
        Cpu cpu = new Cpu();
        cpu.dump();
        IO.println();

        cpu.writeRegister(0x0, 0x0C);
        cpu.writeRegister(0x1, 0x08);
        cpu.writeRegister(0xF, 0x01);
        cpu.setIndexRegister(0x22A);
        cpu.advanceProgramCounter();
        cpu.advanceProgramCounter();

        cpu.dump();
    }
}
