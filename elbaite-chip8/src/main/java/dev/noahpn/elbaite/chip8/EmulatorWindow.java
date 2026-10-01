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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The window ROMs run in, titled Achroite: a menu bar, the screen, and, when View → Debug view is
 * ticked, a {@link DebugView} beside it. Both sit in 32 pixels of black, so a lit pixel at the
 * screen's edge stands clear of the frame and of the grey line between the two.
 *
 * <p>File → Open ROM (Ctrl+O) picks a ROM with a file chooser and runs it in place of whatever was
 * running; File → Exit closes the window. Started without a ROM, the window opens empty, with a
 * hint on the screen. The title names the ROM running, as in {@code Achroite — br8kout.ch8}.
 *
 * <p>View → Debug view (Ctrl+D) shows and hides the debug view. It starts hidden, so the window is
 * only as wide as the screen and its margin, and fits a small display. Shown, it widens the window
 * to the right, and the screen stays where it is, unless the wider window would run off the
 * display: then the window moves left just enough to fit. With no ROM loaded, the view is blank.
 *
 * <p>The {@link QuirksMenu} picks a preset, or switches the six quirks one by one, starting from
 * the preset the window was opened with, and every ROM runs with what it shows. A program picks its
 * machine when it starts, so a change restarts the ROM running from its beginning, with the new
 * quirks; a paused ROM restarts paused. The restart uses the bytes read when the ROM was opened,
 * and never reads the file again.
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
 * <p>The window outlives the machines it runs. The screen, the debug view and the clock belong to a
 * machine and are replaced with it; the frame, the menus, the quirks, the keys, the beeper and the
 * timer belong to the window and stay. A machine's clock starts at the first timer firing after it
 * arrives, so it never inherits time that passed before it.
 *
 * <p>On Windows the window takes Windows' own look: its menus, and a file chooser like every other
 * program's there. Elsewhere, it keeps Swing's own look, Metal, which is the same on every system.
 * The system decides where the window opens.
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
    private final Beeper beeper;

    private JFileChooser chooser;
    private Quirks quirks;
    private Machine machine;
    private Path rom;
    private byte[] romBytes;
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
        debugPanel.setVisible(false);
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
        // Where a new window opens is the system's call, as it is for every other program there,
        // rather than Java's default of the screen's top-left corner.
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        beeper = createBeeper();

        new Timer(TIMER_DELAY_MS, _ -> runFrames()).start();
    }

    /**
     * Opens the window with the given quirks ticked in its Quirks menu, running the ROM at
     * {@code rom}, or empty when {@code rom} is {@code null}. A ROM that cannot be loaded leaves
     * the window empty, with a dialogue saying why. Call this on the event dispatch thread.
     *
     * @param quirks the quirks to start with, usually a preset, not {@code null}
     * @param rom    the ROM to run first, or {@code null}
     */
    public static void open(Quirks quirks, Path rom) {
        useWindowsLook();
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

    // A look and feel applies to the components made after it is set, so this comes before the
    // window is built. Linux's own look, GTK, was never tried, so Linux keeps Metal; if Windows'
    // look cannot be loaded, so does Windows.
    private static void useWindowsLook() {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            IO.println("Windows look unavailable: " + e.getMessage());
            return;
        }

        // Windows' look leaves room for a check mark beside every plain menu item, as classic
        // Windows menus do, so File's items sat behind an empty column. An icon of no width, as
        // tall as the check mark it replaces so the rows keep their height, and no minimum text
        // offset take the column away. Ticked and dotted items keep theirs.
        Icon check = UIManager.getIcon("MenuItem.checkIcon");
        int height = check != null ? check.getIconHeight() : 0;
        UIManager.put("MenuItem.checkIcon", new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 0;
            }

            @Override
            public int getIconHeight() {
                return height;
            }
        });
        UIManager.put("MenuItem.minimumTextOffset", 0);
    }

    private JMenuBar menuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenuItem open = new JMenuItem("Open ROM…");
        open.setMnemonic(KeyEvent.VK_O);
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut));
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

        JCheckBoxMenuItem debug = new JCheckBoxMenuItem("Debug view");
        debug.setMnemonic(KeyEvent.VK_D);
        debug.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, shortcut));
        debug.addActionListener(_ -> showDebugView(debug.isSelected()));

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        view.add(debug);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(view);
        bar.add(new QuirksMenu(quirks, this::changeQuirks));
        return bar;
    }

    // View > Debug view. The window is packed to its new width; its top-left corner, and the screen
    // with it, stays where it is unless the wider window would run off the screen.
    private void showDebugView(boolean shown) {
        debugPanel.setVisible(shown);
        frame.pack();
        keepOnScreen();
    }

    // The system may have opened the window anywhere, so the debug view can push its right edge
    // past the screen's, or past a taskbar there. It moves left just enough to fit, and no further
    // than the screen's left edge.
    private void keepOnScreen() {
        GraphicsConfiguration screen = frame.getGraphicsConfiguration();
        Rectangle bounds = screen.getBounds();
        Insets taskbars = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int left = bounds.x + taskbars.left;
        int right = bounds.x + bounds.width - taskbars.right;

        int overflow = frame.getX() + frame.getWidth() - right;
        if (overflow > 0) {
            frame.setLocation(Math.max(left, frame.getX() - overflow), frame.getY());
        }
    }

    // Shows the given machine, loaded from rom, or, when machine is null, an empty screen with the
    // hint on it and a blank side view. The screen and the side view are replaced, the title
    // follows, and the machine's clock starts at the next timer firing. The frame, the menus, the
    // keys, the beeper and the timer stay as they are.
    private void show(Machine machine, Path rom) {
        screenPanel.removeAll();
        debugPanel.removeAll();

        this.machine = machine;
        this.rom = rom;
        stopped = false;
        clockStarted = false;

        if (machine == null) {
            displayPanel = new DisplayPanel(new Display());
            displayPanel.setLayout(new GridBagLayout());
            displayPanel.add(hint());
            debugView = null;
            // Blank, but the view's size: a ROM opened while the view is shown never resizes the
            // window.
            JTextArea blank = new JTextArea();
            DebugView.configure(blank);
            debugPanel.add(blank);
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

    // The hint, centred on the empty screen in the debug view's font. Like the view, it never takes
    // keyboard focus.
    private static JTextArea hint() {
        JTextArea hint = new JTextArea(NO_ROM_HINT);
        hint.setFont(DebugView.font());
        hint.setForeground(Color.WHITE);
        hint.setBackground(Color.BLACK);
        hint.setEditable(false);
        hint.setFocusable(false);
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

    // Opens the ROM at path in place of whatever is running and returns true, or shows why it could
    // not in a dialogue and returns false, leaving the window as it was. The dialogue names the
    // file, not its whole path: from the chooser that path is absolute, and a one-line message as
    // long as it can be wider than the screen. The bytes are kept for a restart.
    private boolean openRom(Path path) {
        byte[] bytes;
        Machine loaded;
        try {
            bytes = Files.readAllBytes(path);
            loaded = Machine.load(bytes, quirks);
        } catch (IOException | IllegalArgumentException e) {
            Path name = path.getFileName() != null ? path.getFileName() : path;
            JOptionPane.showMessageDialog(frame, Emulator.loadFailure(name, e),
                "Could not open ROM", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        romBytes = bytes;
        show(loaded, path);
        return true;
    }

    // Quirks menu. A program picks its machine when it starts, so the ROM running starts again from
    // its beginning, on a new machine with the new quirks, from the bytes it was opened with. A ROM
    // paused with P restarts paused, ready to step from its first instruction.
    private void changeQuirks(Quirks changed) {
        quirks = changed;
        if (romBytes == null) {
            return;
        }

        Machine restarted = Machine.load(romBytes, quirks);
        restarted.emulator().setPaused(machine.emulator().isPaused());
        show(restarted, rom);
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
            + "\n\nIts registers and memory stay as they were when it stopped, in the debug"
            + " view (View > Debug view).";
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
