package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CpuTest {

    private Memory memory;
    private Display display;
    private Cpu cpu;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        display = new Display();
        cpu = new Cpu(memory, display);
    }

    // --- General registers ---

    @Test
    void registerValuesRoundTrip() {
        cpu.writeRegister(0x0, 0x00);
        cpu.writeRegister(0x1, 0xFF);

        // Two registers rather than one, so this also shows they are independent.
        assertEquals(0x00, cpu.readRegister(0x0));
        assertEquals(0xFF, cpu.readRegister(0x1));
    }

    @Test
    void flagRegisterHoldsAnyByteValue() {
        // VF reports carry, borrow and collision, but it is an ordinary register the
        // rest of the time. Nothing restricts it to 0 or 1.
        cpu.writeRegister(0xF, 0xFF);

        assertEquals(0xFF, cpu.readRegister(0xF));
    }

    @Test
    void outOfRangeRegisterThrows() {
        // A bad register number is an addressing error, on both accessors.
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.readRegister(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.readRegister(16));
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.writeRegister(-1, 0x00));
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.writeRegister(16, 0x00));

        // A bad value is an argument error. Different failure, different type.
        assertThrows(IllegalArgumentException.class, () -> cpu.writeRegister(0x0, -1));
        assertThrows(IllegalArgumentException.class, () -> cpu.writeRegister(0x0, 256));
    }

    // --- Index register ---

    @Test
    void indexRegisterRoundTrips() {
        // Both ends of the sixteen-bit range. There is only one index register, so each
        // value has to be set and read back before the next one overwrites it.
        cpu.setIndexRegister(0x0000);
        assertEquals(0x0000, cpu.getIndexRegister());

        cpu.setIndexRegister(0xFFFF);
        assertEquals(0xFFFF, cpu.getIndexRegister());
    }

    @Test
    void outOfRangeIndexRegisterThrows() {
        // Sixteen bits wide, and it may legally exceed the address space, so out of
        // range here is an argument error rather than an addressing one.
        assertThrows(IllegalArgumentException.class, () -> cpu.setIndexRegister(-1));
        assertThrows(IllegalArgumentException.class, () -> cpu.setIndexRegister(0x10000));
    }

    // --- Program counter ---

    @Test
    void programCounterStartsAtProgramStart() {
        // Pins the relationship rather than the number: the CPU starts where ROMs load.
        // MemoryTest pins 0x200 itself.
        assertEquals(Memory.PROGRAM_START, cpu.getProgramCounter());
    }

    @Test
    void programCounterAdvancesByTwo() {
        // Two, because every CHIP-8 instruction is exactly two bytes.
        cpu.advanceProgramCounter();
        cpu.advanceProgramCounter();

        assertEquals(0x204, cpu.getProgramCounter());
    }

    @Test
    void outOfRangeProgramCounterThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.setProgramCounter(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> cpu.setProgramCounter(0x1000));

        // 0xFFD is the last address it can advance from, so 0xFFE is the first failure.
        cpu.setProgramCounter(0xFFE);
        assertThrows(IndexOutOfBoundsException.class, cpu::advanceProgramCounter);
    }

    // --- Fetch ---

    @Test
    void fetchCombinesBytesBigEndian() {
        memory.write(0x200, 0xA2);
        memory.write(0x201, 0x2A);

        Opcode opcode = cpu.fetch();

        assertEquals(0xA22A, opcode.value());
    }

    @Test
    void fetchAdvancesProgramCounter() {
        cpu.fetch();

        assertEquals(0x202, cpu.getProgramCounter());
    }

    // --- Execute ---

    // 00E0
    @Test
    void clearScreenBlanksDisplay() {
        display.setPixel(0, 0, true);
        display.setPixel(1, 0, true);
        display.setPixel(0, 1, true);

        cpu.execute(new Opcode(0x00E0));

        assertFalse(display.getPixel(0, 0));
        assertFalse(display.getPixel(1, 0));
        assertFalse(display.getPixel(0, 1));
    }

    // 1NNN
    @Test
    void jumpSetsProgramCounter() {
        cpu.execute(new Opcode(0x1ABC));

        assertEquals(0xABC, cpu.getProgramCounter());
    }

    // 6XNN
    @Test
    void setRegisterStoresValue() {
        cpu.execute(new Opcode(0x6A42));

        assertEquals(0x42, cpu.readRegister(0xA));
    }

    // 7XNN
    @Test
    void addToRegisterWrapsAtEightBits() {
        cpu.writeRegister(0x3, 0xFF);

        cpu.execute(new Opcode(0x7301));

        assertEquals(0x00, cpu.readRegister(0x3));
    }

    @Test
    void addToRegisterLeavesFlagAlone() {
        cpu.writeRegister(0xF, 0x42);
        cpu.writeRegister(0x4, 0xFF);

        cpu.execute(new Opcode(0x7401));

        assertEquals(0x42, cpu.readRegister(0xF));
    }

    // No handler
    @Test
    void unimplementedOpcodeThrows() {
        // 00EE reaches dispatch0's default
        assertThrows(UnsupportedOperationException.class,
            () -> cpu.execute(new Opcode(0x00EE)));
        // 8000 reaches execute's default
        assertThrows(UnsupportedOperationException.class,
            () -> cpu.execute(new Opcode(0x8000)));
    }

    // --- Step ---

    @Test
    void stepFetchesThenExecutes() {
        memory.loadRom(new byte[]{0x60, 0x2A});

        Opcode returned = cpu.step();

        assertEquals(0x2A, cpu.readRegister(0x0));
        assertEquals(0x202, cpu.getProgramCounter());
        assertEquals(0x602A, returned.value());
    }
}
