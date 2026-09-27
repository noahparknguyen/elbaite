package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmulatorTest {

    private Memory memory;
    private Display display;
    private Cpu cpu;
    private Emulator emulator;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        memory.loadRom(new byte[]{0x70, 0x01, 0x12, 0x00});

        display = new Display();
        cpu = new Cpu(memory, display);
        emulator = new Emulator(cpu);
    }

    @Test
    void runStepsTicksAfterEveryTenthStep() {
        cpu.setDelayTimer(0xFF);

        emulator.runSteps(25);

        assertEquals(0x0D, cpu.readRegister(0x0));
        assertEquals(0x202, cpu.getProgramCounter());
        assertEquals(0xFD, cpu.getDelayTimer());
    }
}
