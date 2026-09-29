package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.awt.*;
import java.awt.Font;

/**
 * A Swing text area that shows the CPU's registers and timers beside the screen,
 * with the four lines of memory around the program counter underneath.
 *
 * <p>It is a view, not a model: it holds the {@link Cpu} and {@link Memory} it reads,
 * keeps no copy of anything, and rebuilds its text from scratch on each
 * {@link #refresh()}. Thirteen short lines sixty times a second costs nothing, so
 * there is nothing to keep in sync.
 *
 * <p>It is 13 rows of 53 columns, monospaced, white on black. 53 is the width of a
 * memory line; 13 is the eight register lines, a blank line, and four memory lines.
 *
 * <p>It does not take keyboard focus. A text area normally does, which would take
 * every key away from the frame's key listener, so {@code P}, {@code N} and the
 * whole keypad would go dead the moment the window opened.
 */
public final class DebugView extends JTextArea {

    private static final int ROWS = 13;
    private static final int COLUMNS = 53;
    private static final int MEMORY_BYTES = 64;

    private final Cpu cpu;
    private final Memory memory;

    /**
     * Creates a view that reads the given CPU and memory. It starts empty; the first
     * {@link #refresh()} fills it.
     *
     * @param cpu    the CPU to read registers and the program counter from, not
     *               {@code null}
     * @param memory the memory to read around the program counter, not {@code null}
     */
    public DebugView(Cpu cpu, Memory memory) {
        this.cpu = cpu;
        this.memory = memory;

        setRows(ROWS);
        setColumns(COLUMNS);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setEditable(false);
        setFocusable(false);
    }

    /**
     * Rebuilds the view's text: the CPU's register dump, a blank line, then the four
     * lines of memory around the program counter.
     *
     * <p>The memory starts at the line that holds the counter, its last hex digit
     * made {@code 0}, and runs for 64 bytes, or fewer when that would pass
     * {@code 0xFFF}. {@link Memory#dump} throws past the end of the address space,
     * so the length is clamped to what is left.
     */
    public void refresh() {
        int start = cpu.getProgramCounter() & ~0xF;
        int length = Math.min(MEMORY_BYTES, Memory.SIZE - start);

        setText(cpu.dump() + System.lineSeparator() + memory.dump(start, length));
    }
}
