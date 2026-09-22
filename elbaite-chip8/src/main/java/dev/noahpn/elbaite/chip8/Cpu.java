package dev.noahpn.elbaite.chip8;

/**
 * The CHIP-8 processor: its registers, and the fetch-decode-execute cycle that runs
 * instructions.
 *
 * <p>It holds sixteen general registers, the index register, and the program counter.
 * The three are different widths and each is guarded separately. A general register
 * holds one byte, the index register holds sixteen bits, and the program counter holds a
 * twelve-bit address. Nothing about {@code int} enforces any of that.
 *
 * <p>A new instance has every general register zeroed, the index register at
 * {@code 0x0000}, and the program counter at {@link Memory#PROGRAM_START}.
 *
 * <p>Like {@link Memory}, the public setters reject an out-of-range value rather than
 * truncating or wrapping it. Instructions follow the hardware instead: {@code 7XNN} wraps
 * its sum at eight bits, because that is what the original machine did.
 */
public final class Cpu {

    public static final int REGISTER_COUNT = 16;

    private static final int REGISTER_MAX = 0xFF;
    private static final int INDEX_MAX = 0xFFFF;
    private static final int ADDRESS_MAX = 0xFFF;

    private final int[] registers = new int[REGISTER_COUNT];
    private int index;
    private int programCounter = Memory.PROGRAM_START;

    private final Memory memory;
    private final Display display;

    /**
     * Creates a CPU that reads instructions and data from the given memory and draws to
     * the given display.
     *
     * @param memory  the memory this CPU fetches from, not {@code null}
     * @param display the display this CPU draws to, not {@code null}
     */
    public Cpu(Memory memory, Display display) {
        this.memory = memory;
        this.display = display;
    }

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
                "CHIP-8 index register out of range: " + value + " (valid: 0x0000-0xFFFF)");
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
                "CHIP-8 program counter cannot advance past 0xFFF from " + programCounter);
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

    /**
     * Reads the two bytes at the program counter as one instruction, high byte first, and
     * advances the program counter by two before returning.
     *
     * <p>Advancing is part of fetching, not something a caller does afterwards. It happens
     * before the instruction runs so that a jump, which writes the program counter itself,
     * lands where it means to instead of being undone by a later increment. Callers must not
     * also advance, or every instruction will skip the next one.
     *
     * @return the instruction at the old program counter
     * @throws IndexOutOfBoundsException if the program counter is at {@code 0xFFE} or
     *                                   {@code 0xFFF}, where the second byte or the advance
     *                                   would leave the address space
     */
    public Opcode fetch() {
        int high = memory.read(programCounter);
        int low = memory.read(programCounter + 1);
        advanceProgramCounter();
        return new Opcode((high << 8) | low);
    }

    /**
     * Runs one decoded instruction.
     *
     * <p>Dispatches on the first nibble of the opcode, which selects the instruction
     * family, and hands off to the matching handler. It currently handles {@code 00E0},
     * {@code 1NNN}, {@code 6XNN}, and {@code 7XNN}. Every other opcode throws,
     * including ones that are legal CHIP-8 but not yet implemented here — this is not a
     * validation failure, it is a "not yet" signal.
     *
     * @param opcode the instruction to run
     * @throws UnsupportedOperationException if the opcode has no handler yet
     */
    public void execute(Opcode opcode) {
        switch (opcode.high()) {
            case 0x0 -> dispatch0(opcode);
            case 0x1 -> op1NNN(opcode);
            case 0x6 -> op6XNN(opcode);
            case 0x7 -> op7XNN(opcode);
            default -> throw notImplemented(opcode);
        }
    }

    /**
     * Runs one full fetch-decode-execute cycle: fetches the instruction at the
     * program counter, executes it, and returns the opcode that ran.
     *
     * <p>Fetching advances the program counter by two, so by the time the
     * instruction executes, the counter already points at the next instruction
     * in sequence. A jump overwrites it from there.
     *
     * @return the opcode that was fetched and executed
     * @throws IndexOutOfBoundsException     if the program counter is too close to
     *                                       the top of the address space to fetch
     * @throws UnsupportedOperationException if the opcode has no handler yet
     */
    public Opcode step() {
        Opcode opcode = fetch();
        execute(opcode);
        return opcode;
    }

    // The whole 0 family shares its first nibble, so execute cannot tell 00E0 from 00EE.
    // This switches on the full value instead.
    private void dispatch0(Opcode opcode) {
        switch (opcode.value()) {
            case 0x00E0 -> op00E0();
            default -> throw notImplemented(opcode);
        }
    }

    private UnsupportedOperationException notImplemented(Opcode opcode) {
        return new UnsupportedOperationException(
            String.format("CHIP-8 opcode not implemented: 0x%04X", opcode.value()));
    }

    private void op00E0() {
        display.clear();
    }

    private void op1NNN(Opcode opcode) {
        setProgramCounter(opcode.nnn());
    }

    private void op6XNN(Opcode opcode) {
        writeRegister(opcode.x(), opcode.nn());
    }

    private void op7XNN(Opcode opcode) {
        writeRegister(opcode.x(), (readRegister(opcode.x()) + opcode.nn()) & 0xFF);
    }

    static void main() {
        Memory memory = new Memory();
        Display display = new Display();
        memory.loadRom(new byte[]{0x60, (byte) 0xFD, 0x70, 0x01, 0x12, 0x02});
        Cpu cpu = new Cpu(memory, display);

        IO.println("addr  op    V0");
        for (int i = 1; i <= 8; i++) {
            int addr = cpu.getProgramCounter();
            Opcode opcode = cpu.step();
            IO.println(String.format("%04X  %04X  %02X",
                addr, opcode.value(), cpu.readRegister(0x0)));
        }
        IO.println();
        cpu.dump();
    }
}
