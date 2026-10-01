package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void configureSizesAreaAndRefusesFocus() {
        // The window's hint takes the view's place with no ROM loaded: the same size keeps
        // the window from resizing, and a text area that took focus would deafen the keys.
        JTextArea area = new JTextArea();

        DebugView.configure(area);

        assertEquals(13, area.getRows());
        assertEquals(53, area.getColumns());
        assertEquals(18, area.getFont().getSize());
        assertFalse(area.isEditable());
        assertFalse(area.isFocusable());
    }

    @Test
    void configuredAreaKeepsWidthWhenFull() {
        // A memory line fills all 53 columns. Swing adds a pixel for the caret to a text
        // area's width once its text is that wide, and in 1.0.0 that pixel came off the
        // screen. 424 wide empty became 425 at the first refresh.
        JTextArea area = new JTextArea();
        DebugView.configure(area);
        int emptyWidth = area.getPreferredSize().width;

        area.setText("0200  60 2A 12 02 00 00 00 00 00 00 00 00 00 00 00 00");

        assertEquals(emptyWidth, area.getPreferredSize().width);
    }

    @Test
    void fontIsMonospaced() {
        // Columns of hex only line up if every character is as wide as every other. A Consolas that
        // was not found would come back as Java's fallback font, which is not.
        FontMetrics metrics = new JTextArea().getFontMetrics(DebugView.font());

        assertEquals(metrics.charWidth('0'), metrics.charWidth('i'));
        assertEquals(metrics.charWidth('0'), metrics.charWidth('W'));
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
