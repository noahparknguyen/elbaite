package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugViewTest {

    private Memory memory;
    private Cpu cpu;
    private DebugView view;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        Display display = new Display();
        Keypad keypad = new Keypad();
        cpu = new Cpu(memory, display, keypad);
        view = new DebugView(cpu, memory);
    }

    @Test
    void refreshShowsRegistersThenMemoryAtCounter() {
        memory.loadRom(new byte[]{0x60, 0x2A, 0x12, 0x02});
        cpu.step();

        view.refresh();

        List<String> lines = view.getText().lines().toList();

        assertEquals(13, lines.size());
        assertEquals("V0 2A  V1 00  V2 00  V3 00", lines.get(0));
        assertEquals("PC 0202", lines.get(5));
        assertEquals("", lines.get(8));
        assertEquals("0200  60 2A 12 02 00 00 00 00 00 00 00 00 00 00 00 00",
            lines.get(9));
        assertTrue(lines.get(12).startsWith("0230  "));
    }

    @Test
    void refreshStopsAtEndOfMemory() {
        cpu.setProgramCounter(0xFE4);

        view.refresh();

        List<String> lines = view.getText().lines().toList();

        assertEquals(11, lines.size());
        assertTrue(lines.get(9).startsWith("0FE0  "));
        assertTrue(lines.get(10).startsWith("0FF0  "));
    }
}
