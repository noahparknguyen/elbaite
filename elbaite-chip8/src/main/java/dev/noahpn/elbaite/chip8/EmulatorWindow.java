package dev.noahpn.elbaite.chip8;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The window a {@link Machine} runs in, titled Achroite: the screen, with a
 * {@link DebugView} beside it.
 *
 * <p>A Swing timer fires every 16 ms. Each firing runs the frames the real clock says are
 * due, sixty a second, through {@link Emulator#catchUp(long)}, repaints the screen if any
 * ran, sounds the {@link Beeper} while the sound timer is above zero, and refreshes the
 * debug view. The keyboard is mapped onto the keypad by {@link KeyMap}.
 *
 * <p>{@code P} pauses and resumes: the title becomes {@code Achroite (paused)} and the
 * registers print to the terminal. While paused, {@code N} runs one instruction, prints the
 * address it ran from and the opcode it ran, then the registers, and repaints the screen.
 * Each of these ends with a blank line, so every press reads as a block of its own. Time
 * stands still while paused: no ticks, no display wait, and no sound.
 *
 * <p>The window outlives the machine it shows. The screen, the debug view and the clock
 * belong to the machine and are replaced with it; the frame, the keys, the beeper and the
 * timer belong to the window and stay. A machine's clock starts at the first timer firing
 * after it arrives, so it never inherits time that passed before it.
 *
 * <p>Everything here runs on the event dispatch thread, keys and timer alike, so nothing
 * needs a lock.
 */
public final class EmulatorWindow {

    private static final String TITLE = "Achroite";
    private static final int TIMER_DELAY_MS = 16;

    private final JFrame frame = new JFrame(TITLE);
    private final JPanel debugPanel = new JPanel(new GridBagLayout());
    private final Beeper beeper;

    private Machine machine;
    private DisplayPanel displayPanel;
    private DebugView debugView;
    private boolean clockStarted;
    private long clockStart;

    // The parameter is not called machine: inside the key listener below, that name has to
    // mean the field, the machine running now, not the first one.
    private EmulatorWindow(Machine first) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        debugPanel.setBackground(Color.BLACK);
        debugPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(32, 32, 32, 32)));
        frame.add(debugPanel, BorderLayout.EAST);

        setMachine(first);

        frame.setResizable(false);
        frame.pack();

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int code = event.getKeyCode();
                Emulator emulator = machine.emulator();
                Cpu cpu = machine.cpu();

                if (code == KeyEvent.VK_P) {
                    if (emulator.isPaused()) {
                        emulator.setPaused(false);
                        frame.setTitle(TITLE);
                        IO.println("Running");
                    } else {
                        emulator.setPaused(true);
                        frame.setTitle(TITLE + " (paused)");
                        IO.println("Paused");
                        IO.print(cpu.dump());
                    }
                    IO.println();
                    return;
                }

                if (code == KeyEvent.VK_N) {
                    if (emulator.isPaused()) {
                        int address = cpu.getProgramCounter();
                        Opcode opcode = emulator.stepInstruction();
                        IO.println(String.format("%04X: %04X", address, opcode.value()));
                        IO.print(cpu.dump());
                        IO.println();
                        displayPanel.repaint();
                    }
                    return;
                }

                int key = KeyMap.keypadKey(code);
                if (key != -1) {
                    machine.keypad().press(key);
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                int key = KeyMap.keypadKey(event.getKeyCode());
                if (key != -1) {
                    machine.keypad().release(key);
                }
            }
        });

        frame.setIconImages(icons());
        frame.setVisible(true);

        beeper = createBeeper();

        new Timer(TIMER_DELAY_MS, _ -> runFrames()).start();
    }

    /**
     * Opens a window running the given machine, and starts it. Call this on the event
     * dispatch thread.
     *
     * @param machine the machine to run, not {@code null}
     */
    public static void open(Machine machine) {
        new EmulatorWindow(machine);
    }

    /**
     * Loads the three Achroite icon sizes from the class's own resource folder, smallest
     * for the title bar and largest for the taskbar. Returns an empty list if any of them
     * is missing or unreadable, in which case the window runs without an icon.
     *
     * <p>{@code getResource} returns {@code null} for a missing file rather than throwing,
     * so the null check names the file in the message. {@code ImageIO.read} on a
     * {@code null} URL fails with a message that does not.
     *
     * @return the three icons, or an empty list if any could not be loaded
     */
    static List<Image> icons() {
        String[] names = {"achroite-16.png", "achroite-32.png", "achroite-96.png"};
        List<Image> images = new ArrayList<>();
        for (String name : names) {
            URL url = EmulatorWindow.class.getResource(name);
            if (url == null) {
                IO.println("Icon unavailable: " + name);
                return List.of();
            }
            try {
                images.add(ImageIO.read(url));
            } catch (IOException e) {
                IO.println("Icon unavailable: " + name);
                return List.of();
            }
        }
        return images;
    }

    // Hands the window a machine: its own screen and debug view replace any old ones, the
    // title drops a stale "(paused)", and its clock starts at the next timer firing. The
    // frame, the keys, the beeper and the timer stay as they are.
    private void setMachine(Machine machine) {
        if (displayPanel != null) {
            frame.remove(displayPanel);
            debugPanel.remove(debugView);
        }

        this.machine = machine;
        displayPanel = new DisplayPanel(machine.display());
        debugView = new DebugView(machine.cpu(), machine.memory());
        frame.add(displayPanel, BorderLayout.CENTER);
        debugPanel.add(debugView);

        frame.setTitle(TITLE);
        clockStarted = false;
        frame.revalidate();
        frame.repaint();
    }

    // One timer firing: the frames due, the tone, and the debug view.
    private void runFrames() {
        long now = System.nanoTime();
        if (!clockStarted) {
            clockStart = now;
            clockStarted = true;
        }

        Emulator emulator = machine.emulator();
        if (emulator.catchUp(now - clockStart) > 0) {
            displayPanel.repaint();
        }
        if (beeper != null) {
            beeper.setOn(emulator.isSounding());
        }
        debugView.refresh();
    }

    private static Beeper createBeeper() {
        try {
            return new Beeper();
        } catch (LineUnavailableException e) {
            IO.println("Sound unavailable: " + e.getMessage());
            return null;
        }
    }
}
