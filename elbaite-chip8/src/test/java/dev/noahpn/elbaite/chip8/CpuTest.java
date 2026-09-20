package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CpuTest {

    private Cpu cpu;

    @BeforeEach
    void setUp() {
        cpu = new Cpu();
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
}
