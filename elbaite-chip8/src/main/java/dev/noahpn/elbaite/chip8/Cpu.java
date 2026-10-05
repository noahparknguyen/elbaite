package dev.noahpn.elbaite.chip8;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * The CHIP-8 processor: its registers, timers and call stack, and the fetch-decode-execute cycle
 * that runs instructions.
 *
 * <p>It holds sixteen general registers, the index register, the program counter, and a
 * sixteen-entry call stack. The first three are different widths and each is guarded separately. A
 * general register holds one byte, the index register holds sixteen bits, and the program counter
 * holds a twelve-bit address. Nothing about {@code int} enforces any of that.
 *
 * <p>Beside them sit two one-byte countdown timers, the delay timer and the sound timer.
 * Each falls by one on every {@link #tick()} until it reaches zero, where it stays.
 *
 * <p>It reads keys from a {@link Keypad}, given to it like the display, for {@code EX9E},
 * {@code EXA1} and {@code FX0A}.
 *
 * <p>It also holds a source of random bytes, used by {@code CXNN}. The source is injected through
 * the five-argument constructor rather than created inside, so a test can hand in a predictable
 * stand-in and assert on the result.
 *
 * <p>The behaviours where interpreters disagree come from the {@link Quirks} it is built
 * with. It reads five of the six; the sixth, display wait, belongs to the {@link Emulator}.
 *
 * <p>A new instance has every general register zeroed, the index register at
 * {@code 0x0000}, the program counter at {@link Memory#PROGRAM_START}, both timers at zero, and an
 * empty stack.
 *
 * <p>Like {@link Memory}, the public setters reject an out-of-range value rather than
 * truncating or wrapping it. Instructions follow the hardware instead: {@code 7XNN} wraps its sum
 * at eight bits, because that is what the original machine did.
 */
public final class Cpu {

    public static final int REGISTER_COUNT = 16;

    private static final int REGISTER_MAX = 0xFF;
    private static final int INDEX_MAX = 0xFFFF;
    private static final int ADDRESS_MAX = 0xFFF;
    private static final int TIMER_MAX = 0xFF;
    private static final int STACK_DEPTH = 16;
    private static final int NO_KEY = -1;

    private final int[] registers = new int[REGISTER_COUNT];
    private final int[] stack = new int[STACK_DEPTH];
    private int stackPointer;
    private int index;
    private int programCounter = Memory.PROGRAM_START;
    private int delayTimer;
    private int soundTimer;
    private int keyAwaitingRelease = NO_KEY;

    private final Memory memory;
    private final Display display;
    private final Keypad keypad;
    private final Quirks quirks;
    private final IntSupplier randomByte;

    /**
     * Creates a CPU that reads instructions and data from the given memory, draws to the given
     * display, reads keys from the given keypad, uses the {@link Quirks#VIP} preset, and takes its
     * random bytes from {@link ThreadLocalRandom}.
     *
     * @param memory  the memory this CPU fetches from, not {@code null}
     * @param display the display this CPU draws to, not {@code null}
     * @param keypad  the keypad this CPU reads keys from, not {@code null}
     */
    public Cpu(Memory memory, Display display, Keypad keypad) {
        this(memory, display, keypad, Quirks.VIP);
    }

    /**
     * Creates a CPU with the given quirks, taking its random bytes from {@link ThreadLocalRandom}.
     *
     * @param memory  the memory this CPU fetches from, not {@code null}
     * @param display the display this CPU draws to, not {@code null}
     * @param keypad  the keypad this CPU reads keys from, not {@code null}
     * @param quirks  the behaviours to use where interpreters differ, not {@code null}
     */
    public Cpu(Memory memory, Display display, Keypad keypad, Quirks quirks) {
        this(memory, display, keypad, quirks, () -> ThreadLocalRandom.current().nextInt(0x100));
    }

    /**
     * Creates a CPU with the given quirks and the given source of random bytes.
     *
     * <p>This is the constructor a test uses to make {@code CXNN} predictable. Each call
     * to {@code randomByte.getAsInt()} must return a value from {@code 0} to {@code 255}, and
     * {@code CXNN} calls it once per instruction.
     *
     * @param memory     the memory this CPU fetches from, not {@code null}
     * @param display    the display this CPU draws to, not {@code null}
     * @param keypad     the keypad this CPU reads keys from, not {@code null}
     * @param quirks     the behaviours to use where interpreters differ, not {@code null}
     * @param randomByte the source of random bytes, not {@code null}
     */
    public Cpu(Memory memory, Display display, Keypad keypad, Quirks quirks,
               IntSupplier randomByte) {
        this.memory = memory;
        this.display = display;
        this.keypad = keypad;
        this.quirks = quirks;
        this.randomByte = randomByte;
    }

    /**
     * Returns the value held in one of the sixteen general registers.
     *
     * @param register which register, {@code 0} for {@code V0} through {@code 15} for {@code VF}
     * @return the register value, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code register} is outside {@code 0} to {@code 15}
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
     * @param register which register, {@code 0} for {@code V0} through {@code 15} for {@code VF}
     * @param value    the value to store, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code register} is outside {@code 0} to {@code 15}
     * @throws IllegalArgumentException  if {@code value} is outside {@code 0} to {@code 255}
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
     * above the {@code 0xFFF} top of the address space. That is why an out-of-range value here is
     * an argument error rather than an addressing one.
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
     * @throws IndexOutOfBoundsException if the program counter is already at {@code 0xFFE} or
     *                                   {@code 0xFFF}, where advancing would leave the address
     *                                   space
     */
    public void advanceProgramCounter() {
        if (programCounter > ADDRESS_MAX - 2) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 program counter cannot advance past 0xFFF from " + programCounter);
        }

        programCounter += 2;
    }

    /**
     * Returns the delay timer.
     *
     * <p>A CHIP-8 program reads it with {@code FX07} to measure time: it sets the timer,
     * then polls it in a loop until it reaches zero. The value falls by one on each
     * {@link #tick()}, and stops at zero.
     *
     * @return the delay timer, {@code 0x00} to {@code 0xFF}
     */
    public int getDelayTimer() {
        return delayTimer;
    }

    /**
     * Sets the delay timer.
     *
     * <p>Setting a running timer overwrites it; nothing is added to the count already in
     * progress.
     *
     * @param value the value to store, {@code 0x00} to {@code 0xFF}
     * @throws IllegalArgumentException if {@code value} is outside {@code 0x00} to {@code 0xFF}
     */
    public void setDelayTimer(int value) {
        if (value < 0 || value > TIMER_MAX) {
            throw new IllegalArgumentException(
                "CHIP-8 delay timer out of range: " + value + " (valid: 0x00-0xFF)");
        }

        delayTimer = value;
    }

    /**
     * Returns the sound timer.
     *
     * <p>No instruction reads this timer: it is meant to be heard, not read. A tone plays
     * for as long as the value is above zero; in the window, {@link Emulator#isSounding()} checks
     * it once a frame and a {@link Beeper} plays it. The value falls by one on each
     * {@link #tick()}, and stops at zero.
     *
     * @return the sound timer, {@code 0x00} to {@code 0xFF}
     */
    public int getSoundTimer() {
        return soundTimer;
    }

    /**
     * Sets the sound timer.
     *
     * <p>Setting a running timer overwrites it; nothing is added to the count already in
     * progress.
     *
     * @param value the value to store, {@code 0x00} to {@code 0xFF}
     * @throws IllegalArgumentException if {@code value} is outside {@code 0x00} to {@code 0xFF}
     */
    public void setSoundTimer(int value) {
        if (value < 0 || value > TIMER_MAX) {
            throw new IllegalArgumentException(
                "CHIP-8 sound timer out of range: " + value + " (valid: 0x00-0xFF)");
        }

        soundTimer = value;
    }

    /**
     * Returns the whole register file as text: the sixteen general registers four to a line, then
     * the index register and the program counter on lines of their own, then the two timers side by
     * side on one line, then the call stack on the last line, oldest entry first, or {@code -} when
     * it is empty.
     *
     * <p>Values are hex, two digits for a general register and a timer, and four for
     * {@code I}, {@code PC} and each stack entry. Every line ends with a line separator, the last
     * included, so the text prints as it is with {@code IO.print}.
     *
     * <p>This returns the text; it does not print it. The terminal harness prints it,
     * and the window shows it in a {@link DebugView}.
     *
     * @return the register file as eight lines of text
     */
    public String dump() {
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
        sb.append(String.format("DT %02X  ST %02X%n", delayTimer, soundTimer));
        sb.append("STACK");
        if (stackPointer == 0) {
            sb.append(" -");
        } else {
            for (int i = 0; i < stackPointer; i++) {
                sb.append(String.format(" %04X", stack[i]));
            }
        }
        sb.append(System.lineSeparator());

        return sb.toString();
    }

    private void checkRegister(int register) {
        if (register < 0 || register >= REGISTER_COUNT) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 register out of range: " + register + " (valid: V0-VF)");
        }
    }

    /**
     * Reads the two bytes at the program counter as one instruction, high byte first, and advances
     * the program counter by two before returning.
     *
     * <p>Advancing is part of fetching, not something a caller does afterwards. It happens
     * before the instruction runs so that a jump, which writes the program counter itself, lands
     * where it means to instead of being undone by a later increment. Callers must not also
     * advance, or every instruction will skip the next one.
     *
     * @return the instruction at the old program counter
     * @throws IndexOutOfBoundsException if the program counter is at {@code 0xFFE} or
     *                                   {@code 0xFFF}, where the second byte or the advance would
     *                                   leave the address space
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
     * family, and hands off to the matching handler. Every CHIP-8 instruction is handled except
     * {@code 0NNN}, which called native machine code on the VIP and is not implemented here.
     *
     * @param opcode the instruction to run
     * @throws IndexOutOfBoundsException     if the instruction reaches outside memory, such as
     *                                       {@code BNNN} jumping past {@code 0xFFF}, {@code DXYN}
     *                                       reading a sprite byte past it, {@code FX33} with
     *                                       {@code I + 2} past {@code 0xFFF}, or {@code FX55} or
     *                                       {@code FX65} with {@code I + X} past {@code 0xFFF}
     * @throws UnsupportedOperationException if the opcode is {@code 0NNN}, or is not defined by
     *                                       CHIP-8 at all
     * @throws IllegalStateException         if {@code 2NNN} calls with sixteen calls already
     *                                       nested, or {@code 00EE} returns with none
     */
    public void execute(Opcode opcode) {
        switch (opcode.high()) {
            case 0x0 -> dispatch0(opcode);
            case 0x1 -> op1NNN(opcode);
            case 0x2 -> op2NNN(opcode);
            case 0x3 -> op3XNN(opcode);
            case 0x4 -> op4XNN(opcode);
            case 0x5 -> op5XY0(opcode);
            case 0x6 -> op6XNN(opcode);
            case 0x7 -> op7XNN(opcode);
            case 0x8 -> dispatch8(opcode);
            case 0x9 -> op9XY0(opcode);
            case 0xA -> opANNN(opcode);
            case 0xB -> opBNNN(opcode);
            case 0xC -> opCXNN(opcode);
            case 0xD -> opDXYN(opcode);
            case 0xE -> dispatchE(opcode);
            case 0xF -> dispatchF(opcode);
            default -> throw notImplemented(opcode);
        }
    }

    /**
     * Runs one full fetch-decode-execute cycle: fetches the instruction at the program counter,
     * executes it, and returns the opcode that ran.
     *
     * <p>Fetching advances the program counter by two, so by the time the
     * instruction executes, the counter already points at the next instruction in sequence. A jump
     * overwrites it from there.
     *
     * @return the opcode that was fetched and executed
     * @throws IndexOutOfBoundsException     if the program counter is too close to the top of the
     *                                       address space to fetch, or the instruction reaches
     *                                       outside memory, such as {@code BNNN} jumping past
     *                                       {@code 0xFFF}, {@code DXYN} reading a sprite byte past
     *                                       it, {@code FX33} with {@code I + 2} past {@code 0xFFF},
     *                                       or {@code FX55} or {@code FX65} with {@code I + X} past
     *                                       {@code 0xFFF}
     * @throws UnsupportedOperationException if the opcode is {@code 0NNN}, or is not defined by
     *                                       CHIP-8 at all
     * @throws IllegalStateException         if the instruction is {@code 2NNN} with sixteen calls
     *                                       already nested, or {@code 00EE} with none
     */
    public Opcode step() {
        Opcode opcode = fetch();
        execute(opcode);
        return opcode;
    }

    /**
     * Advances emulated time by one tick, a sixtieth of a second: each timer above zero falls by
     * one. A timer already at zero stays at zero; it never wraps.
     *
     * <p>This is separate from {@link #step()} because the timers run on the clock, not
     * on instructions. Whoever owns the clock calls this; {@code step} never does.
     */
    public void tick() {
        if (delayTimer > 0) {
            delayTimer--;
        }

        if (soundTimer > 0) {
            soundTimer--;
        }
    }

    // The whole 0 family shares its first nibble, so execute cannot tell 00E0 from 00EE.
    // This switches on the full value instead.
    private void dispatch0(Opcode opcode) {
        switch (opcode.value()) {
            case 0x00E0 -> op00E0();
            case 0x00EE -> op00EE();
            default -> throw notImplemented(opcode);
        }
    }

    // In 8XYN, X and Y select the register operands, so the last nibble is the only
    // field left to choose the operation. That is what this switches on.
    private void dispatch8(Opcode opcode) {
        switch (opcode.n()) {
            case 0x0 -> op8XY0(opcode);
            case 0x1 -> op8XY1(opcode);
            case 0x2 -> op8XY2(opcode);
            case 0x3 -> op8XY3(opcode);
            case 0x4 -> op8XY4(opcode);
            case 0x5 -> op8XY5(opcode);
            case 0x6 -> op8XY6(opcode);
            case 0x7 -> op8XY7(opcode);
            case 0xE -> op8XYE(opcode);
            default -> throw notImplemented(opcode);
        }
    }

    // In EXNN, X selects the register operand, so the low byte is the field left to
    // choose the operation. That is what this switches on.
    private void dispatchE(Opcode opcode) {
        switch (opcode.nn()) {
            case 0x9E -> opEX9E(opcode);
            case 0xA1 -> opEXA1(opcode);
            default -> throw notImplemented(opcode);
        }
    }

    // In FXNN, X selects the register operand, so the low byte is the field left to
    // choose the operation. That is what this switches on.
    private void dispatchF(Opcode opcode) {
        switch (opcode.nn()) {
            case 0x07 -> opFX07(opcode);
            case 0x0A -> opFX0A(opcode);
            case 0x15 -> opFX15(opcode);
            case 0x18 -> opFX18(opcode);
            case 0x1E -> opFX1E(opcode);
            case 0x29 -> opFX29(opcode);
            case 0x33 -> opFX33(opcode);
            case 0x55 -> opFX55(opcode);
            case 0x65 -> opFX65(opcode);
            default -> throw notImplemented(opcode);
        }
    }

    private UnsupportedOperationException notImplemented(Opcode opcode) {
        return new UnsupportedOperationException(
            String.format("CHIP-8 opcode not implemented: 0x%04X", opcode.value()));
    }

    private void setFlag(boolean set) {
        if (set) {
            writeRegister(0xF, 1);
        } else {
            writeRegister(0xF, 0);
        }
    }

    private void skipIf(boolean condition) {
        if (condition) {
            advanceProgramCounter();
        }
    }

    private void push(int address) {
        if (stackPointer >= STACK_DEPTH) {
            throw new IllegalStateException(
                "CHIP-8 stack overflow: more than 16 nested calls");
        }

        stack[stackPointer++] = address;
    }

    private int pop() {
        if (stackPointer == 0) {
            throw new IllegalStateException(
                "CHIP-8 stack underflow: 00EE with no call to return from");
        }

        return stack[--stackPointer];
    }

    private void op00E0() {
        display.clear();
    }

    private void op00EE() {
        setProgramCounter(pop());
    }

    private void op1NNN(Opcode opcode) {
        setProgramCounter(opcode.nnn());
    }

    private void op2NNN(Opcode opcode) {
        push(getProgramCounter());
        setProgramCounter(opcode.nnn());
    }

    private void op3XNN(Opcode opcode) {
        int vx = readRegister(opcode.x());

        skipIf(vx == opcode.nn());
    }

    private void op4XNN(Opcode opcode) {
        int vx = readRegister(opcode.x());

        skipIf(vx != opcode.nn());
    }

    private void op5XY0(Opcode opcode) {
        if (opcode.n() != 0) {
            throw notImplemented(opcode);
        }

        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        skipIf(vx == vy);
    }

    private void op6XNN(Opcode opcode) {
        writeRegister(opcode.x(), opcode.nn());
    }

    private void op7XNN(Opcode opcode) {
        writeRegister(opcode.x(), (readRegister(opcode.x()) + opcode.nn()) & 0xFF);
    }

    private void op8XY0(Opcode opcode) {
        int vy = readRegister(opcode.y());

        writeRegister(opcode.x(), vy);
    }

    private void op8XY1(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        writeRegister(opcode.x(), vx | vy);
        if (quirks.vfReset()) {
            setFlag(false);
        }
    }

    private void op8XY2(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        writeRegister(opcode.x(), vx & vy);
        if (quirks.vfReset()) {
            setFlag(false);
        }
    }

    private void op8XY3(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        writeRegister(opcode.x(), vx ^ vy);
        if (quirks.vfReset()) {
            setFlag(false);
        }
    }

    private void op8XY4(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        int result = vx + vy;

        writeRegister(opcode.x(), result & 0xFF);

        setFlag(result > REGISTER_MAX);
    }

    private void op8XY5(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        int result = vx - vy;

        writeRegister(opcode.x(), result & 0xFF);

        setFlag(vx >= vy);
    }

    private void op8XY6(Opcode opcode) {
        int value = quirks.shifting() ? readRegister(opcode.x()) : readRegister(opcode.y());

        int result = value >>> 1;

        writeRegister(opcode.x(), result & 0xFF);

        setFlag((value & 1) != 0);
    }

    private void op8XY7(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        int result = vy - vx;

        writeRegister(opcode.x(), result & 0xFF);

        setFlag(vy >= vx);
    }

    private void op8XYE(Opcode opcode) {
        int value = quirks.shifting() ? readRegister(opcode.x()) : readRegister(opcode.y());

        int result = value << 1;

        writeRegister(opcode.x(), result & 0xFF);

        setFlag(((value >>> 7) & 1) != 0);
    }

    private void op9XY0(Opcode opcode) {
        if (opcode.n() != 0) {
            throw notImplemented(opcode);
        }

        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        skipIf(vx != vy);
    }

    private void opANNN(Opcode opcode) {
        setIndexRegister(opcode.nnn());
    }

    private void opBNNN(Opcode opcode) {
        int offset = quirks.jumping() ? readRegister(opcode.x()) : readRegister(0x0);

        setProgramCounter(opcode.nnn() + offset);
    }

    private void opCXNN(Opcode opcode) {
        int random = randomByte.getAsInt();

        writeRegister(opcode.x(), random & opcode.nn());
    }

    private void opDXYN(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int vy = readRegister(opcode.y());

        int startX = vx % 64;
        int startY = vy % 32;

        int i = getIndexRegister();
        boolean collision = false;
        for (int row = 0; row < opcode.n(); row++) {
            int spriteByte = memory.read(i + row);

            for (int col = 0; col < 8; col++) {
                // col 0 is the leftmost bit: 0x80, then 0x40, 0x20, ...
                if ((spriteByte & (0x80 >> col)) == 0) {
                    continue;
                }

                int px = startX + col;
                int py = startY + row;

                if (quirks.clipping()) {
                    if (px >= 64 || py >= 32) {
                        continue;
                    }
                } else {
                    px %= 64;
                    py %= 32;
                }

                if (display.flipPixel(px, py)) {
                    collision = true;
                }
            }
        }

        setFlag(collision);
    }

    private void opEX9E(Opcode opcode) {
        int vx = readRegister(opcode.x());

        skipIf(keypad.isPressed(vx & 0xF));
    }

    private void opEXA1(Opcode opcode) {
        int vx = readRegister(opcode.x());

        skipIf(!keypad.isPressed(vx & 0xF));
    }

    private void opFX07(Opcode opcode) {
        writeRegister(opcode.x(), getDelayTimer());
    }

    private void opFX0A(Opcode opcode) {
        if (keyAwaitingRelease == NO_KEY) {
            int key = NO_KEY;

            // Lowest-numbered held key wins.
            for (int k = 0; k < 16; k++) {
                if (keypad.isPressed(k)) {
                    key = k;
                    break;
                }
            }

            if (key == NO_KEY) {
                setProgramCounter(getProgramCounter() - 2);
                return;
            }

            keyAwaitingRelease = key;
            setProgramCounter(getProgramCounter() - 2);
            return;
        }

        if (keypad.isPressed(keyAwaitingRelease)) {
            setProgramCounter(getProgramCounter() - 2);
            return;
        }

        writeRegister(opcode.x(), keyAwaitingRelease);
        keyAwaitingRelease = NO_KEY;
    }

    private void opFX15(Opcode opcode) {
        int vx = readRegister(opcode.x());

        setDelayTimer(vx);
    }

    private void opFX18(Opcode opcode) {
        int vx = readRegister(opcode.x());

        setSoundTimer(vx);
    }

    private void opFX1E(Opcode opcode) {
        int vx = readRegister(opcode.x());

        setIndexRegister((index + vx) & 0xFFFF);
    }

    private void opFX29(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int digit = vx & 0xF;

        setIndexRegister(Memory.FONT_START + digit * Font.GLYPH_BYTES);
    }

    private void opFX33(Opcode opcode) {
        int vx = readRegister(opcode.x());
        int hundreds = vx / 100;
        int tens = (vx / 10) % 10;
        int ones = vx % 10;

        memory.write(index, hundreds);
        memory.write(index + 1, tens);
        memory.write(index + 2, ones);
    }

    private void opFX55(Opcode opcode) {
        int start = index;
        for (int i = 0x0; i <= opcode.x(); i++) {
            int value = readRegister(i);
            memory.write(start + i, value);
        }

        if (quirks.memory()) {
            setIndexRegister(start + opcode.x() + 1);
        }
    }

    private void opFX65(Opcode opcode) {
        int start = index;
        for (int i = 0x0; i <= opcode.x(); i++) {
            int value = memory.read(start + i);
            writeRegister(i, value);
        }

        if (quirks.memory()) {
            setIndexRegister(start + opcode.x() + 1);
        }
    }
}
