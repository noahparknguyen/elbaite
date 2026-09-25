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

    // 00EE
    @Test
    void returnResumesAfterCall() {
        memory.write(0x200, 0x22);
        memory.write(0x201, 0x06);
        memory.write(0x206, 0x00);
        memory.write(0x207, 0xEE);

        cpu.step();
        cpu.step();

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void nestedCallsReturnInReverseOrder() {
        memory.write(0x200, 0x22);
        memory.write(0x201, 0x10);
        memory.write(0x210, 0x22);
        memory.write(0x211, 0x20);
        memory.write(0x212, 0x00);
        memory.write(0x213, 0xEE);
        memory.write(0x220, 0x00);
        memory.write(0x221, 0xEE);

        // Call, call, return: the first return goes back to the newest call, inside the
        // outer subroutine. A queue would send it to 0x202 instead.
        cpu.step();
        cpu.step();
        cpu.step();

        assertEquals(0x212, cpu.getProgramCounter());

        // The second return goes back to the main program.
        cpu.step();

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void returnWithEmptyStackThrows() {
        assertThrows(IllegalStateException.class, () -> cpu.execute(new Opcode(0x00EE)));
    }

    // 1NNN
    @Test
    void jumpSetsProgramCounter() {
        cpu.execute(new Opcode(0x1ABC));

        assertEquals(0xABC, cpu.getProgramCounter());
    }

    // 2NNN
    @Test
    void callJumpsToAddress() {
        cpu.execute(new Opcode(0x2ABC));

        assertEquals(0xABC, cpu.getProgramCounter());
    }

    @Test
    void callPastStackDepthThrows() {
        // Sixteen calls fill the stack exactly, so the seventeenth is the first with
        // nowhere to go.
        Opcode opcode = new Opcode(0x2200);
        for (int i = 1; i <= 16; i++) {
            cpu.execute(opcode);
        }

        assertThrows(IllegalStateException.class, () -> cpu.execute(opcode));
    }

    // 3XNN
    @Test
    void skipIfEqualSkipsOnMatch() {
        cpu.writeRegister(0xA, 0x42);

        cpu.execute(new Opcode(0x3A42));

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void skipIfEqualDoesNotSkipOnMismatch() {
        cpu.writeRegister(0xA, 0x41);

        cpu.execute(new Opcode(0x3A42));

        assertEquals(0x200, cpu.getProgramCounter());
    }

    // 4XNN
    @Test
    void skipIfNotEqualSkipsOnMismatch() {
        cpu.writeRegister(0xA, 0x41);

        cpu.execute(new Opcode(0x4A42));

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void skipIfNotEqualDoesNotSkipOnMatch() {
        cpu.writeRegister(0xA, 0x42);

        cpu.execute(new Opcode(0x4A42));

        assertEquals(0x200, cpu.getProgramCounter());
    }

    // 5XY0
    @Test
    void skipIfRegistersEqualSkipsOnMatch() {
        cpu.writeRegister(0xA, 0x42);
        cpu.writeRegister(0xB, 0x42);

        cpu.execute(new Opcode(0x5AB0));

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void skipIfRegistersEqualDoesNotSkipOnMismatch() {
        cpu.writeRegister(0xA, 0x42);
        cpu.writeRegister(0xB, 0x43);

        cpu.execute(new Opcode(0x5AB0));

        assertEquals(0x200, cpu.getProgramCounter());
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

    // 8XY0
    @Test
    void copyRegisterStoresSource() {
        cpu.writeRegister(0x1, 0xFF);

        cpu.execute(new Opcode(0x8010));

        assertEquals(0xFF, cpu.readRegister(0x0));
        assertEquals(0xFF, cpu.readRegister(0x1));
    }

    @Test
    void copyRegisterLeavesFlagAlone() {
        cpu.writeRegister(0xF, 0x42);
        cpu.writeRegister(0x1, 0xFF);

        cpu.execute(new Opcode(0x8010));

        assertEquals(0x42, cpu.readRegister(0xF));
    }

    // 8XY1
    @Test
    void orCombinesBits() {
        cpu.writeRegister(0x0, 0xF0);
        cpu.writeRegister(0x1, 0x3C);

        cpu.execute(new Opcode(0x8011));

        assertEquals(0xFC, cpu.readRegister(0x0));
    }

    // 8XY2
    @Test
    void andCombinesBits() {
        cpu.writeRegister(0x0, 0xF0);
        cpu.writeRegister(0x1, 0x3C);

        cpu.execute(new Opcode(0x8012));

        assertEquals(0x30, cpu.readRegister(0x0));
    }

    // 8XY3
    @Test
    void xorCombinesBits() {
        cpu.writeRegister(0x0, 0xF0);
        cpu.writeRegister(0x1, 0x3C);

        cpu.execute(new Opcode(0x8013));

        assertEquals(0xCC, cpu.readRegister(0x0));
    }

    // 8XY1-8XY3 share the vF reset
    @Test
    void logicOperationsResetFlag() {
        cpu.writeRegister(0xF, 1);
        cpu.execute(new Opcode(0x8011));
        assertEquals(0, cpu.readRegister(0xF));

        cpu.writeRegister(0xF, 1);
        cpu.execute(new Opcode(0x8012));
        assertEquals(0, cpu.readRegister(0xF));

        cpu.writeRegister(0xF, 1);
        cpu.execute(new Opcode(0x8013));
        assertEquals(0, cpu.readRegister(0xF));
    }

    // 8XY4
    @Test
    void addRegistersStoresSum() {
        // 0xF0 + 0x20 is 0x110, so the low byte is 0x10. That is the wrap, and it
        // also proves VX gets the low byte rather than the untruncated sum.
        cpu.writeRegister(0x0, 0xF0);
        cpu.writeRegister(0x1, 0x20);

        cpu.execute(new Opcode(0x8014));

        assertEquals(0x10, cpu.readRegister(0x0));
    }

    @Test
    void addRegistersUpdatesCarryFlag() {
        // Carry is the ninth bit: 0xFF + 0x01 is the smallest sum that has one.
        cpu.writeRegister(0xF, 0);
        cpu.writeRegister(0x0, 0xFF);
        cpu.writeRegister(0x1, 0x01);

        cpu.execute(new Opcode(0x8014));

        assertEquals(1, cpu.readRegister(0xF));

        // A sentinel in VF catches a missing clear: without it, VF would stay 1.
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0x01);
        cpu.writeRegister(0x1, 0x02);

        cpu.execute(new Opcode(0x8014));

        assertEquals(0, cpu.readRegister(0xF));
    }

    // 8XY5
    @Test
    void subtractRegistersStoresDifference() {
        // VX - VY, and the low byte is what lands in VX. 0x10 - 0x20 is negative,
        // so the low byte is 0xF0 and VF is tested separately.
        cpu.writeRegister(0x0, 0x10);
        cpu.writeRegister(0x1, 0x20);

        cpu.execute(new Opcode(0x8015));

        assertEquals(0xF0, cpu.readRegister(0x0));
    }

    @Test
    void subtractRegistersUpdatesBorrowFlag() {
        // VF is 1 when VX >= VY, which is the no-borrow case. Equal counts as no borrow.
        cpu.writeRegister(0xF, 0);
        cpu.writeRegister(0x0, 0x20);
        cpu.writeRegister(0x1, 0x20);

        cpu.execute(new Opcode(0x8015));

        assertEquals(1, cpu.readRegister(0xF));

        // A sentinel in VF catches a missing clear on the borrow case.
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0x10);
        cpu.writeRegister(0x1, 0x20);

        cpu.execute(new Opcode(0x8015));

        assertEquals(0, cpu.readRegister(0xF));
    }

    // 8XY6
    @Test
    void shiftRightShiftsVyIntoVx() {
        // VY is the source and VX the destination, so VY has to stay unchanged.
        cpu.writeRegister(0x0, 0xAA);
        cpu.writeRegister(0x1, 0x03);

        cpu.execute(new Opcode(0x8016));

        assertEquals(0x01, cpu.readRegister(0x0));
        assertEquals(0x03, cpu.readRegister(0x1));
    }

    @Test
    void shiftRightUpdatesFlagFromLeastSignificantBit() {
        // The flag is the bit shifted out: 0x01 has a 1, 0x02 has a 0.
        cpu.writeRegister(0xF, 0);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0x01);

        cpu.execute(new Opcode(0x8016));

        assertEquals(1, cpu.readRegister(0xF));

        // A sentinel in VF catches a missing clear on the zero-bit case.
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0x02);

        cpu.execute(new Opcode(0x8016));

        assertEquals(0, cpu.readRegister(0xF));
    }

    // 8XY7
    @Test
    void subtractVxFromVyStoresDifference() {
        // VY - VX, not VX - VY. 0x34 - 0x12 = 0x22.
        cpu.writeRegister(0x0, 0x12);
        cpu.writeRegister(0x1, 0x34);

        cpu.execute(new Opcode(0x8017));

        assertEquals(0x22, cpu.readRegister(0x0));
    }

    @Test
    void subtractVxFromVyUpdatesBorrowFlag() {
        // VF is 1 when VY >= VX, which is the no-borrow case. Equal counts as no borrow.
        cpu.writeRegister(0xF, 0);
        cpu.writeRegister(0x0, 0x20);
        cpu.writeRegister(0x1, 0x20);

        cpu.execute(new Opcode(0x8017));

        assertEquals(1, cpu.readRegister(0xF));

        // A sentinel in VF catches a missing clear on the borrow case.
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0x20);
        cpu.writeRegister(0x1, 0x10);

        cpu.execute(new Opcode(0x8017));

        assertEquals(0, cpu.readRegister(0xF));
    }

    // 8XYE
    @Test
    void shiftLeftShiftsVyIntoVx() {
        // VY is the source and VX the destination, so VY has to stay unchanged.
        // 0x81 << 1 is 0x102, and the low byte is 0x02.
        cpu.writeRegister(0x0, 0xAA);
        cpu.writeRegister(0x1, 0x81);

        cpu.execute(new Opcode(0x801E));

        assertEquals(0x02, cpu.readRegister(0x0));
        assertEquals(0x81, cpu.readRegister(0x1));
    }

    @Test
    void shiftLeftUpdatesFlagFromMostSignificantBit() {
        // The flag is the bit shifted out: 0x80 has a 1, 0x40 has a 0.
        cpu.writeRegister(0xF, 0);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0x80);

        cpu.execute(new Opcode(0x801E));

        assertEquals(1, cpu.readRegister(0xF));

        // A sentinel in VF catches a missing clear on the zero-bit case.
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0x40);

        cpu.execute(new Opcode(0x801E));

        assertEquals(0, cpu.readRegister(0xF));
    }

    // 9XY0
    @Test
    void skipIfRegistersNotEqualSkipsOnMismatch() {
        cpu.writeRegister(0xA, 0x42);
        cpu.writeRegister(0xB, 0x43);

        cpu.execute(new Opcode(0x9AB0));

        assertEquals(0x202, cpu.getProgramCounter());
    }

    @Test
    void skipIfRegistersNotEqualDoesNotSkipOnMatch() {
        cpu.writeRegister(0xA, 0x42);
        cpu.writeRegister(0xB, 0x42);

        cpu.execute(new Opcode(0x9AB0));

        assertEquals(0x200, cpu.getProgramCounter());
    }

    // ANNN
    @Test
    void setIndexStoresAddress() {
        cpu.execute(new Opcode(0xA123));

        assertEquals(0x123, cpu.getIndexRegister());
    }

    // BNNN
    @Test
    void jumpWithOffsetAddsV0() {
        // A correct handler never reads V3. It is set because X is 3 in B300, so a
        // handler that added VX instead of V0 would land on 0x310, not 0x304.
        cpu.writeRegister(0x0, 0x04);
        cpu.writeRegister(0x3, 0x10);

        cpu.execute(new Opcode(0xB300));

        assertEquals(0x304, cpu.getProgramCounter());
    }

    @Test
    void jumpWithOffsetPastMemoryThrows() {
        cpu.writeRegister(0x0, 0x01);

        assertThrows(IndexOutOfBoundsException.class, () -> cpu.execute(new Opcode(0xBFFF)));
    }

    // DXYN
    @Test
    void drawSpriteXorsOntoDisplay() {
        // 0xA0 = 1010 0000. The two set bits sit at opposite ends of the byte, so
        // a wrong shift direction or a wrong origin moves them visibly.
        memory.write(0x300, 0xA0);
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0);

        cpu.execute(new Opcode(0xD011));

        assertTrue(display.getPixel(0, 0));
        assertFalse(display.getPixel(1, 0));
        assertTrue(display.getPixel(2, 0));
    }

    @Test
    void drawSpriteSetsFlagOnCollision() {
        // Drawing the same sprite twice restores the screen, so the second pass
        // flips every lit pixel back off. That is what a collision is.
        memory.write(0x300, 0xFF);
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0);

        cpu.execute(new Opcode(0xD011));
        cpu.execute(new Opcode(0xD011));

        assertEquals(1, cpu.readRegister(0xF));
        assertFalse(display.getPixel(0, 0));
    }

    @Test
    void drawSpriteClearsFlagWithoutCollision() {
        // A sentinel in VF catches a missing write at the end of DXYN: without
        // it, VF would stay at the sentinel rather than becoming 0.
        memory.write(0x300, 0x80);
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0xF, 0xAA);
        cpu.writeRegister(0x0, 0);
        cpu.writeRegister(0x1, 0);

        cpu.execute(new Opcode(0xD011));

        assertEquals(0, cpu.readRegister(0xF));
        assertTrue(display.getPixel(0, 0));
    }

    @Test
    void drawSpriteWrapsStartPosition() {
        // 65 and 33 are the smallest values past each edge, so the modulo has to
        // be applied to the start coordinate and not to every pixel.
        memory.write(0x300, 0x80);
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0x0, 65);
        cpu.writeRegister(0x1, 33);

        cpu.execute(new Opcode(0xD011));

        assertTrue(display.getPixel(1, 1));
        assertFalse(display.getPixel(1, 0));
        assertFalse(display.getPixel(0, 1));
    }

    @Test
    void drawSpriteClipsAtEdges() {
        // Three rows from row 30, so the third lands on row 32. Eight columns from
        // column 60, so the last four land on columns 64-67. A wrap would put them
        // back at the far edge; a clip leaves them off. The loops check both.
        memory.write(0x300, 0xFF);
        memory.write(0x301, 0xFF);
        memory.write(0x302, 0xFF);
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0x0, 60);
        cpu.writeRegister(0x1, 30);

        cpu.execute(new Opcode(0xD013));

        for (int x = 60; x <= 63; x++) {
            assertTrue(display.getPixel(x, 30), "row 30, column " + x);
            assertTrue(display.getPixel(x, 31), "row 31, column " + x);
        }
        for (int x = 0; x <= 3; x++) {
            assertFalse(display.getPixel(x, 30), "column " + x + " is where a wrap would land");
            assertFalse(display.getPixel(x, 31), "column " + x + " is where a wrap would land");
        }
        for (int x = 0; x < 64; x++) {
            assertFalse(display.getPixel(x, 0), "row 0 is where a wrap would land");
        }
    }

    // FX1E
    @Test
    void addToIndexStoresSum() {
        cpu.setIndexRegister(0x300);
        cpu.writeRegister(0xA, 0x42);

        cpu.execute(new Opcode(0xFA1E));

        assertEquals(0x342, cpu.getIndexRegister());
    }

    @Test
    void addToIndexWrapsAtSixteenBits() {
        cpu.setIndexRegister(0xFFFF);
        cpu.writeRegister(0xA, 0x01);

        cpu.execute(new Opcode(0xFA1E));

        assertEquals(0x0000, cpu.getIndexRegister());
    }

    @Test
    void addToIndexLeavesFlagAlone() {
        cpu.setIndexRegister(0x0FFF);
        cpu.writeRegister(0xF, 0x42);
        cpu.writeRegister(0xA, 0x01);

        cpu.execute(new Opcode(0xFA1E));

        assertEquals(0x42, cpu.readRegister(0xF));
    }

    // FX55
    @Test
    void storeRegistersWritesV0ThroughVx() {
        cpu.writeRegister(0x0, 0x11);
        cpu.writeRegister(0x1, 0x22);
        cpu.writeRegister(0x2, 0x33);
        cpu.writeRegister(0x3, 0x44);
        cpu.setIndexRegister(0x300);

        cpu.execute(new Opcode(0xF255));

        assertEquals(0x11, memory.read(0x300));
        assertEquals(0x22, memory.read(0x301));
        assertEquals(0x33, memory.read(0x302));
        assertEquals(0x00, memory.read(0x303));
    }

    @Test
    void storeRegistersAdvancesIndex() {
        cpu.setIndexRegister(0x300);

        cpu.execute(new Opcode(0xF255));

        assertEquals(0x303, cpu.getIndexRegister());
    }

    // FX65
    @Test
    void loadRegistersReadsV0ThroughVx() {
        cpu.setIndexRegister(0x300);
        memory.write(0x300, 0x11);
        memory.write(0x301, 0x22);
        memory.write(0x302, 0x33);
        memory.write(0x303, 0x44);

        cpu.execute(new Opcode(0xF265));

        assertEquals(0x11, cpu.readRegister(0x0));
        assertEquals(0x22, cpu.readRegister(0x1));
        assertEquals(0x33, cpu.readRegister(0x2));
        assertEquals(0x00, cpu.readRegister(0x3));
    }

    @Test
    void loadRegistersAdvancesIndex() {
        cpu.setIndexRegister(0x300);

        cpu.execute(new Opcode(0xF265));

        assertEquals(0x303, cpu.getIndexRegister());
    }

    // No handler
    @Test
    void unimplementedOpcodeThrows() {
        // 00EF reaches dispatch0's default
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0x00EF)));
        // E000 reaches execute's default
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0xE000)));
        // 8009 reaches dispatch8's default
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0x8009)));
        // 5AB1 reaches op5XY0's nibble check
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0x5AB1)));
        // 9AB1 reaches op9XY0's nibble check
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0x9AB1)));
        // F0FF reaches dispatchF's default
        assertThrows(UnsupportedOperationException.class, () -> cpu.execute(new Opcode(0xF0FF)));
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
