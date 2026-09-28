package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmulatorTest {

    private Memory memory;
    private Cpu cpu;
    private Emulator emulator;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        memory.loadRom(new byte[]{0x70, 0x01, 0x12, 0x00});

        Display display = new Display();
        Keypad keypad = new Keypad();
        cpu = new Cpu(memory, display, keypad);
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

    @Test
    void framesDueCountsWholeFrames() {
        assertEquals(0, Emulator.framesDue(0));
        assertEquals(0, Emulator.framesDue(16_666_666L));
        assertEquals(1, Emulator.framesDue(16_666_667L));
        assertEquals(60, Emulator.framesDue(1_000_000_000L));
    }

    @Test
    void framesDueHandlesLongRuns() {
        assertEquals(216_000, Emulator.framesDue(3_600_000_000_000L));
    }

    @Test
    void catchUpRunsFramesDue() {
        cpu.setDelayTimer(0xFF);

        assertEquals(3, emulator.catchUp(50_000_000L));
        assertEquals(0xFC, cpu.getDelayTimer());
    }

    @Test
    void catchUpNeverRunsAFrameTwice() {
        // First call at 50 ms runs the three frames due.
        assertEquals(3, emulator.catchUp(50_000_000L));

        // Second call at the same instant: nothing new is due.
        assertEquals(0, emulator.catchUp(50_000_000L));
    }

    @Test
    void catchUpSkipsFramesPastLimit() {
        // One second due: sixty frames, but only five run.
        assertEquals(5, emulator.catchUp(1_000_000_000L));

        // One frame later: the skipped fifty-five are gone, so only the new one runs.
        assertEquals(1, emulator.catchUp(1_016_666_667L));
    }

    @Test
    void runStepsWaitsOutFrameAfterDraw() {
        memory.loadRom(new byte[]{0x70, 0x01, (byte) 0xD0, 0x01, 0x12, 0x00});

        emulator.runSteps(10);

        assertEquals(0x01, cpu.readRegister(0x0), "only the step before the draw should have run");
        assertEquals(0x204, cpu.getProgramCounter(), "PC should be stalled just after the DXYN");
    }

    @Test
    void runStepsResumesAfterTick() {
        memory.loadRom(new byte[]{0x70, 0x01, (byte) 0xD0, 0x01, 0x12, 0x00});
        cpu.setDelayTimer(0xFF);

        emulator.runSteps(20);

        assertEquals(0x02, cpu.readRegister(0x0),
            "a second add and draw should have run after the tick");
        assertEquals(0x204, cpu.getProgramCounter(),
            "PC should again be stalled just after the second DXYN");
        assertEquals(0xFD, cpu.getDelayTimer(),
            "both ticks should have counted the delay timer down");
    }

    @Test
    void runStepsRunsThroughDrawWithoutDisplayWaitQuirk() {
        // The same draw ROM as the two tests above, but under OCTO: no display wait,
        // so every step runs an instruction. In ten steps the loop gets through add,
        // draw, jump three times and a fourth add.
        memory.loadRom(new byte[]{0x70, 0x01, (byte) 0xD0, 0x01, 0x12, 0x00});
        emulator = new Emulator(cpu, Quirks.OCTO);

        emulator.runSteps(10);

        assertEquals(0x04, cpu.readRegister(0x0),
            "with no wait, all four adds should have run");
        assertEquals(0x202, cpu.getProgramCounter(),
            "PC should have advanced through add, draw and jump each time");
    }
}
