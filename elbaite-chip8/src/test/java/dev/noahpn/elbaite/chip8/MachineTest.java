package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineTest {

    @Test
    void loadPlacesRomAtProgramStart() {
        byte[] rom = {0x60, 0x2A};

        Machine machine = Machine.load(rom, Quirks.VIP);

        assertEquals(0x60, machine.memory().read(0x200));
        assertEquals(0x2A, machine.memory().read(0x201));
    }

    @Test
    void loadWiresCpuToMemoryAndDisplay() {
        // I = 0x206, draw one row at (V0, V1) = (0, 0), spin. The sprite, 0x80, sits at
        // 0x206, so the pixel lights only if the CPU fetches and reads this machine's
        // memory and draws to this machine's display.
        byte[] rom = {(byte) 0xA2, 0x06, (byte) 0xD0, 0x11, 0x12, 0x04, (byte) 0x80};
        Machine machine = Machine.load(rom, Quirks.VIP);

        machine.cpu().step();
        machine.cpu().step();

        assertTrue(machine.display().getPixel(0, 0));
    }

    @Test
    void loadWiresKeypadToCpu() {
        // E09E skips the next instruction while the key in V0, key 0, is held.
        byte[] rom = {(byte) 0xE0, (byte) 0x9E};
        Machine machine = Machine.load(rom, Quirks.VIP);
        machine.keypad().press(0x0);

        machine.cpu().step();

        assertEquals(0x204, machine.cpu().getProgramCounter());
    }

    @Test
    void loadGivesQuirksToCpu() {
        // VF = 0x42, then 8011. OCTO has no vF reset, so the sentinel survives; the VIP
        // would leave 0x00.
        byte[] rom = {0x6F, 0x42, (byte) 0x80, 0x11};
        Machine machine = Machine.load(rom, Quirks.OCTO);

        machine.cpu().step();
        machine.cpu().step();

        assertEquals(0x42, machine.cpu().readRegister(0xF));
    }

    @Test
    void loadGivesQuirksToEmulator() {
        // Add, draw, jump back, as in EmulatorTest. OCTO has no display wait, so ten steps
        // get through four adds; the VIP would stop at the first draw with V0 at 0x01.
        byte[] rom = {0x70, 0x01, (byte) 0xD0, 0x01, 0x12, 0x00};
        Machine machine = Machine.load(rom, Quirks.OCTO);

        machine.emulator().runSteps(10);

        assertEquals(0x04, machine.cpu().readRegister(0x0));
    }
}
