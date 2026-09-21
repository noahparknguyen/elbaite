package dev.noahpn.elbaite.chip8;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;


/**
 * The CHIP-8 CPU's state between instructions: sixteen general registers, the index
 * register, and the program counter.
 *
 * <p>The three are different widths and each is guarded separately. A general register
 * holds one byte, the index register holds sixteen bits, and the program counter holds a
 * twelve-bit address. Nothing about {@code int} enforces any of that.
 *
 * <p>A new instance has every general register zeroed, the index register at
 * {@code 0x0000}, and the program counter at {@link Memory#PROGRAM_START}.
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

    private final Memory memory;

    /**
     * Creates a CPU that reads instructions and data from the given memory.
     *
     * @param memory the memory this CPU fetches from, not {@code null}
     */
    public Cpu(Memory memory) {
        this.memory = memory;
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

    static void main(String[] args) {
        if (args.length == 0) {
            IO.println("Usage: Cpu <rom-path>");
            return;
        }

        Memory memory = new Memory();
        try {
            memory.loadRom(Path.of(args[0]));
        } catch (NoSuchFileException e) {
            IO.println("ROM not found: " + args[0]);
            return;
        } catch (IOException e) {
            IO.println("Could not read ROM '" + args[0] + "': " + e.getMessage());
            return;
        }

        Cpu cpu = new Cpu(memory);

        String header = "addr  op     hi  X   Y   N   NN   NNN";
        String row = "%04X  %04X   %X   %X   %X   %X   %02X   %03X";

        IO.println(header);
        for (int i = 0; i < 12; i++) {
            int addr = cpu.getProgramCounter();
            Opcode op = cpu.fetch();

            IO.println(String.format(row,
                addr, op.value(), op.high(), op.x(), op.y(), op.n(), op.nn(), op.nnn()));
        }

        IO.println();
        cpu.dump();
    }
}
