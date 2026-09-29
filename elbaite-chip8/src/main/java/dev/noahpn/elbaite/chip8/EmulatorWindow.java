package dev.noahpn.elbaite.chip8;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The window ROMs run in, titled Achroite: a menu bar, the screen, and a {@link DebugView}
 * beside it. Both sit in 32 pixels of black, so a lit pixel at the screen's edge stands
 * clear of the frame and of the grey line between the two.
 *
 * <p>File → Open ROM (Ctrl+O) picks a ROM with a file chooser and runs it in place of
 * whatever was running, with the preset the window was opened with; File → Exit closes the
 * window. Started without a ROM, the window opens empty, with a hint where the debug view
 * goes. The title names the ROM running, as in {@code Achroite — br8kout.ch8}.
 *
 * <p>A Swing timer fires every 16 ms. Each firing runs the frames the real clock says are
 * due, sixty a second, through {@link Emulator#catchUp(long)}, repaints the screen if any
 * ran, sounds the {@link Beeper} while the sound timer is above zero, and refreshes the
 * debug view. The keyboard is mapped onto the keypad by {@link KeyMap}. A key pressed with
 * Ctrl, Alt or Meta is a shortcut, not a keypad key, and every keypad key is let go when
 * the window loses focus, since a key released elsewhere never reaches it.
 *
 * <p>{@code P} pauses and resumes, and the title ends in {@code (paused)} meanwhile; the
 * registers print to the terminal. While paused, {@code N} runs one instruction, prints the
 * address it ran from and the opcode it ran, then the registers, and repaints the screen.
 * Each of these ends with a blank line, so every press reads as a block of its own. Time
 * stands still while paused: no ticks, no display wait, and no sound.
 *
 * <p>Nothing here needs a terminal. A ROM that cannot be loaded, or that stops with an
 * error while it runs, says so in a dialogue. A stopped ROM stays on screen with its
 * registers and memory in the debug view as they were, the title ends in
 * {@code (stopped)}, and another ROM can be opened as usual. While the file chooser is
 * open, a running ROM holds still.
 *
 * <p>The window outlives the machines it runs. The screen, the debug view and the clock
 * belong to a machine and are replaced with it; the frame, the menus, the keys, the beeper
 * and the timer belong to the window and stay. A machine's clock starts at the first timer
 * firing after it arrives, so it never inherits time that passed before it.
 *
 * <p>Everything here runs on the event dispatch thread, keys, menus and timer alike, so
 * nothing needs a lock.
 */
public final class EmulatorWindow {

    private static final String TITLE = "Achroite";
    private static final int TIMER_DELAY_MS = 16;
    private static final int MARGIN = 32;
    private static final String NO_ROM_HINT = """
        No ROM loaded.

        File > Open ROM, or Ctrl+O.""";

    private final JFrame frame = new JFrame(TITLE);
    private final JPanel screenPanel = new JPanel(new BorderLayout());
    private final JPanel debugPanel = new JPanel(new GridBagLayout());
    private final Quirks quirks;
    private final Beeper beeper;

    private JFileChooser chooser;
    private Machine machine;
    private Path rom;
    private boolean stopped;
    private DisplayPanel displayPanel;
    private DebugView debugView;
    private boolean clockStarted;
    private long clockStart;

    private EmulatorWindow(Quirks quirks) {
        this.quirks = quirks;
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar());

        screenPanel.setBackground(Color.BLACK);
        screenPanel.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
        frame.add(screenPanel, BorderLayout.CENTER);

        debugPanel.setBackground(Color.BLACK);
        debugPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN)));
        frame.add(debugPanel, BorderLayout.EAST);

        show(null, null);

        frame.setResizable(false);
        frame.pack();

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (machine == null || event.isControlDown() || event.isAltDown()
                    || event.isMetaDown()) {
                    return;
                }

                int code = event.getKeyCode();
                if (code == KeyEvent.VK_P) {
                    togglePause();
                } else if (code == KeyEvent.VK_N) {
                    stepPaused();
                } else {
                    int key = KeyMap.keypadKey(code);
                    if (key != -1) {
                        machine.keypad().press(key);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                int key = KeyMap.keypadKey(event.getKeyCode());
                if (machine != null && key != -1) {
                    machine.keypad().release(key);
                }
            }
        });
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) {
                releaseKeys();
            }
        });

        frame.setIconImages(icons());
        frame.setVisible(true);

        beeper = createBeeper();

        new Timer(TIMER_DELAY_MS, _ -> runFrames()).start();
    }

    /**
     * Opens the window with the given preset, running the ROM at {@code rom}, or empty when
     * {@code rom} is {@code null}. A ROM that cannot be loaded leaves the window empty, with
     * a dialogue saying why. Call this on the event dispatch thread.
     *
     * @param quirks the preset for every ROM the window runs, not {@code null}
     * @param rom    the ROM to run first, or {@code null}
     */
    public static void open(Quirks quirks, Path rom) {
        EmulatorWindow window = new EmulatorWindow(quirks);
        if (rom != null) {
            window.openRom(rom);
        }
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

    /**
     * Returns the window's title: {@code Achroite}, then the ROM's file name after a dash
     * when one is loaded, then {@code (stopped)} or {@code (paused)}.
     *
     * @param rom     the ROM running, or {@code null} for none
     * @param paused  whether the ROM is paused
     * @param stopped whether it stopped with an error, which wins over {@code paused}
     * @return the title, as in {@code Achroite — br8kout.ch8 (paused)}
     */
    static String title(Path rom, boolean paused, boolean stopped) {
        if (rom == null) {
            return TITLE;
        }

        String title = TITLE + " — " + rom.getFileName();
        if (stopped) {
            return title + " (stopped)";
        }
        if (paused) {
            return title + " (paused)";
        }
        return title;
    }

    /**
     * Returns the file chooser's filter: CHIP-8 ROMs, by their usual extensions {@code .ch8}
     * and {@code .c8} in either case. The chooser keeps its "All Files" choice beside it.
     *
     * @return the filter
     */
    static FileNameExtensionFilter romFilter() {
        return new FileNameExtensionFilter("CHIP-8 ROMs (*.ch8, *.c8)", "ch8", "c8");
    }

    private JMenuBar menuBar() {
        JMenuItem open = new JMenuItem("Open ROM…");
        open.setMnemonic(KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        open.addActionListener(_ -> chooseRom());

        JMenuItem exit = new JMenuItem("Exit");
        exit.setMnemonic(KeyEvent.VK_X);
        exit.addActionListener(_ ->
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(open);
        file.addSeparator();
        file.add(exit);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    // Shows the given machine, loaded from rom, or the empty screen and the hint when
    // machine is null. The screen and the side view are replaced, the title follows, and the
    // machine's clock starts at the next timer firing. The frame, the menus, the keys, the
    // beeper and the timer stay as they are.
    private void show(Machine machine, Path rom) {
        screenPanel.removeAll();
        debugPanel.removeAll();

        this.machine = machine;
        this.rom = rom;
        stopped = false;
        clockStarted = false;

        if (machine == null) {
            displayPanel = new DisplayPanel(new Display());
            debugView = null;
            debugPanel.add(hint());
        } else {
            displayPanel = new DisplayPanel(machine.display());
            debugView = new DebugView(machine.cpu(), machine.memory());
            debugPanel.add(debugView);
        }
        screenPanel.add(displayPanel);

        updateTitle();
        frame.revalidate();
        frame.repaint();
    }

    private static JTextArea hint() {
        JTextArea hint = new JTextArea(NO_ROM_HINT);
        DebugView.configure(hint);
        return hint;
    }

    // File > Open ROM. A running ROM holds still while the chooser is open, and carries on
    // if nothing new is opened.
    private void chooseRom() {
        if (chooser == null) {
            chooser = romChooser(rom);
        }

        Machine held = machine;
        boolean holding = held != null && !stopped && !held.emulator().isPaused();
        if (holding) {
            held.emulator().setPaused(true);
        }

        boolean opened = chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION
            && openRom(chooser.getSelectedFile().toPath());

        if (holding && !opened) {
            held.emulator().setPaused(false);
        }
    }

    // The chooser starts in the folder of the ROM running, if there is one.
    private static JFileChooser romChooser(Path current) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open ROM");
        chooser.setFileFilter(romFilter());
        if (current != null && current.toAbsolutePath().getParent() != null) {
            chooser.setCurrentDirectory(current.toAbsolutePath().getParent().toFile());
        }
        return chooser;
    }

    // Opens the ROM at path in place of whatever is running and returns true, or shows why
    // it could not in a dialogue and returns false, leaving the window as it was. The dialogue
    // names the file, not its whole path: from the chooser that path is absolute, and a
    // one-line message as long as it can be wider than the screen.
    private boolean openRom(Path path) {
        Machine loaded;
        try {
            loaded = Machine.load(path, quirks);
        } catch (IOException | IllegalArgumentException e) {
            Path name = path.getFileName() != null ? path.getFileName() : path;
            JOptionPane.showMessageDialog(frame, Emulator.loadFailure(name, e),
                "Could not open ROM", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        show(loaded, path);
        return true;
    }

    // P: pause or resume, and print a block to the terminal.
    private void togglePause() {
        if (stopped) {
            return;
        }

        Emulator emulator = machine.emulator();
        if (emulator.isPaused()) {
            emulator.setPaused(false);
            IO.println("Running");
        } else {
            emulator.setPaused(true);
            IO.println("Paused");
            IO.print(machine.cpu().dump());
        }
        IO.println();
        updateTitle();
    }

    // N: while paused, run one instruction and print it, then the registers.
    private void stepPaused() {
        if (stopped || !machine.emulator().isPaused()) {
            return;
        }

        Cpu cpu = machine.cpu();
        int address = cpu.getProgramCounter();
        Opcode opcode;
        try {
            opcode = machine.emulator().stepInstruction();
        } catch (RuntimeException e) {
            stop(e);
            return;
        }

        IO.println(String.format("%04X: %04X", address, opcode.value()));
        IO.print(cpu.dump());
        IO.println();
        displayPanel.repaint();
    }

    // The ROM hit an error, such as an opcode CHIP-8 does not define: it runs no more, but
    // stays on screen with its registers and memory in the debug view. The dialogue opens
    // once this timer firing or key press has finished, so the timer keeps firing
    // underneath it and the tone stops.
    private void stop(RuntimeException e) {
        stopped = true;
        updateTitle();
        displayPanel.repaint();

        String reason = e.getMessage() != null ? e.getMessage() : e.toString();
        String message = rom.getFileName() + " stopped with an error:\n" + reason
            + "\n\nIts registers and memory are beside the screen, as they were when it"
            + " stopped.";
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, message,
            "ROM stopped", JOptionPane.ERROR_MESSAGE));
    }

    // A key released while another window has focus never reaches this one, and would stay
    // held: so every key is let go when the window loses focus.
    private void releaseKeys() {
        if (machine == null) {
            return;
        }

        for (int key = 0x0; key <= 0xF; key++) {
            machine.keypad().release(key);
        }
    }

    private void updateTitle() {
        boolean paused = machine != null && machine.emulator().isPaused();
        frame.setTitle(title(rom, paused, stopped));
    }

    // One timer firing: the frames due, the tone, and the debug view. A stopped machine,
    // or none at all, runs nothing and is silent.
    private void runFrames() {
        long now = System.nanoTime();
        if (!clockStarted) {
            clockStart = now;
            clockStarted = true;
        }

        boolean sounding = false;
        if (machine != null && !stopped) {
            Emulator emulator = machine.emulator();
            try {
                if (emulator.catchUp(now - clockStart) > 0) {
                    displayPanel.repaint();
                }
                sounding = emulator.isSounding();
            } catch (RuntimeException e) {
                stop(e);
            }
        }

        if (beeper != null) {
            beeper.setOn(sounding);
        }
        if (debugView != null) {
            debugView.refresh();
        }
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
