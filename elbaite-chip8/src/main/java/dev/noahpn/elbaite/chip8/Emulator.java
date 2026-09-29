package dev.noahpn.elbaite.chip8;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The emulator program: it loads a ROM and runs it, either in the terminal for a given number of
 * steps or live in a window titled Achroite.
 *
 * <p>The command line is {@code Emulator [--quirks vip|schip|octo] <rom-path> [steps]}. The
 * optional {@code --quirks} must come first and picks a preset from {@link Quirks#forName};
 * without it, {@link Quirks#VIP}. The same preset goes to both the {@link Cpu} and the
 * emulator.
 *
 * <p>{@code --version} on its own prints the name and version and exits.
 *
 * <p>With a step count, it runs that many steps and prints the screen and registers to the
 * terminal. Without one, it opens the window and runs in real time, sixty frames a second,
 * driven by a Swing timer, with the keyboard mapped onto the keypad by {@link KeyMap} and the
 * machine beeping while the sound timer is above zero. The window shows the screen with a
 * {@link DebugView} beside it, refreshed on every timer firing. A frame is ten steps and a
 * tick, and with display wait on, a draw ends its frame.
 *
 * <p>In the window, {@code P} pauses and resumes: the title becomes {@code Achroite (paused)}
 * and the registers print to the terminal. While paused, {@code N} runs one instruction, prints
 * the address it ran from and the opcode it ran, then the registers, and repaints the screen.
 * Each of these ends with a blank line, so every press reads as a block of its own. Time
 * stands still while paused: no ticks, no display wait, and no sound.
 *
 * <p>Instances drive a {@link Cpu}; both modes share {@link #runSteps(int)}.
 */
public final class Emulator {

    private static final int STEPS_PER_FRAME = 10;
    private static final int FRAMES_PER_SECOND = 60;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int CATCH_UP_LIMIT = 5;
    private static final int TIMER_DELAY_MS = 16;

    private static final String USAGE =
        "Usage: Emulator [--quirks vip|schip|octo] <rom-path> [steps]";

    private final Cpu cpu;
    private final Quirks quirks;
    private long framesRun;
    private boolean paused;

    /**
     * Creates an emulator that drives the given CPU with the {@link Quirks#VIP} preset.
     *
     * @param cpu the processor to step and tick, not {@code null}
     */
    public Emulator(Cpu cpu) {
        this(cpu, Quirks.VIP);
    }

    /**
     * Creates an emulator that drives the given CPU with the given quirks. Only
     * {@link Quirks#displayWait()} is read here; the rest belong to the {@link Cpu}.
     *
     * @param cpu    the processor to step and tick, not {@code null}
     * @param quirks the behaviours to use where interpreters differ, not {@code null}
     */
    public Emulator(Cpu cpu, Quirks quirks) {
        this.cpu = cpu;
        this.quirks = quirks;
    }

    /**
     * Runs the given number of steps, ticking the timers after every tenth step of this run:
     * after steps 10, 20, 30 and so on.
     *
     * <p>When the quirks have display wait on, the rest of the frame after a step that runs a
     * {@code DXYN} passes without running instructions: later steps up to the frame's tick
     * do nothing. The tick itself still happens, on its usual step, and the step after it
     * runs normally again. The wait never outlives this call.
     *
     * @param steps the number of steps to run
     */
    public void runSteps(int steps) {
        boolean waiting = false;
        for (int step = 1; step <= steps; step++) {
            if (!waiting) {
                Opcode opcode = cpu.step();
                if (quirks.displayWait() && opcode.high() == 0xD) {
                    waiting = true;
                }
            }
            if (step % STEPS_PER_FRAME == 0) {
                cpu.tick();
                waiting = false;
            }
        }
    }

    /**
     * Returns how many whole frames fit in the given elapsed time at 60 frames a second,
     * rounding down.
     *
     * @param elapsedNanos nanoseconds since the loop started
     * @return the number of whole frames due
     */
    public static long framesDue(long elapsedNanos) {
        return Math.floorDiv(elapsedNanos * FRAMES_PER_SECOND, NANOS_PER_SECOND);
    }

    /**
     * Runs the frames that are due and have not run yet, at most five.
     *
     * <p>Frames beyond the limit are skipped rather than saved: they count as done and never
     * run. {@code elapsedNanos} counts from the loop's start and never goes down between
     * calls.
     *
     * <p>While paused, no frames run: the frames due count as done and {@code 0} is
     * returned, so resuming never runs a burst of saved-up frames.
     *
     * @param elapsedNanos nanoseconds since the loop started
     * @return how many frames were run
     */
    public int catchUp(long elapsedNanos) {
        long due = framesDue(elapsedNanos);
        if (paused) {
            framesRun = due;
            return 0;
        }
        long behind = due - framesRun;
        int toRun = (int) Math.min(behind, CATCH_UP_LIMIT);

        for (int frame = 0; frame < toRun; frame++) {
            runSteps(STEPS_PER_FRAME);
        }

        framesRun = due;
        return toRun;
    }

    /**
     * Returns whether the tone should be sounding: true while the CPU's sound timer is
     * above zero. Always {@code false} while paused, so a frozen sound timer does not
     * drone through a pause.
     *
     * @return {@code true} if the sound timer is above zero and the emulator is running
     */
    public boolean isSounding() {
        return !paused && cpu.getSoundTimer() > 0;
    }

    /**
     * Returns whether the emulator is paused. A new emulator is running.
     *
     * @return {@code true} if the emulator is paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses or resumes the emulator. While paused, {@link #catchUp(long)} runs no frames
     * (the frames due count as done) and {@link #isSounding()} is {@code false}.
     *
     * @param paused {@code true} to pause, {@code false} to resume
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Runs exactly one instruction through the CPU and returns the opcode it ran. The
     * timers never move: this is {@link Cpu#step()}, not {@link #runSteps(int)}, so there
     * is no tick and no display wait. It does not check the pause itself; the window only
     * calls it while paused.
     *
     * @return the opcode the CPU executed
     */
    public Opcode stepInstruction() {
        return cpu.step();
    }

    /**
     * Returns the program's name and version. When running from the jar, the version
     * comes from the manifest's {@code Implementation-Version}, which the jar plugin
     * fills in from the POM, so nothing here hard-codes it. When running from plain
     * class files, as every test and every {@code exec:java} run does, there is no
     * manifest, and this returns {@code Achroite (development build)}.
     *
     * @return the name and version
     */
    public static String version() {
        String version = Emulator.class.getPackage().getImplementationVersion();
        if (version == null) {
            return "Achroite (development build)";
        }
        return "Achroite " + version;
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
            URL url = Emulator.class.getResource(name);
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

    private static void openWindow(Memory memory, Display display, Keypad keypad,
                                   Cpu cpu, Emulator emulator) {
        JFrame frame = new JFrame("Achroite");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        DisplayPanel panel = new DisplayPanel(display);
        DebugView debugView = new DebugView(cpu, memory);

        JPanel debugPanel = new JPanel(new GridBagLayout());
        debugPanel.setBackground(Color.BLACK);
        debugPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(32, 32, 32, 32)));
        debugPanel.add(debugView);

        frame.add(panel, BorderLayout.CENTER);
        frame.add(debugPanel, BorderLayout.EAST);
        frame.setResizable(false);
        frame.pack();

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int code = event.getKeyCode();

                if (code == KeyEvent.VK_P) {
                    if (emulator.isPaused()) {
                        emulator.setPaused(false);
                        frame.setTitle("Achroite");
                        IO.println("Running");
                    } else {
                        emulator.setPaused(true);
                        frame.setTitle("Achroite (paused)");
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
                        panel.repaint();
                    }
                    return;
                }

                int key = KeyMap.keypadKey(code);
                if (key != -1) {
                    keypad.press(key);
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                int key = KeyMap.keypadKey(event.getKeyCode());
                if (key != -1) {
                    keypad.release(key);
                }
            }
        });

        frame.setIconImages(icons());
        frame.setVisible(true);

        Beeper beeper;
        try {
            beeper = new Beeper();
        } catch (LineUnavailableException e) {
            IO.println("Sound unavailable: " + e.getMessage());
            beeper = null;
        }
        final Beeper tone = beeper;

        long start = System.nanoTime();
        Timer timer = new Timer(TIMER_DELAY_MS, _ -> {
            if (emulator.catchUp(System.nanoTime() - start) > 0) {
                panel.repaint();
            }
            if (tone != null) {
                tone.setOn(emulator.isSounding());
            }
            debugView.refresh();
        });
        timer.start();
    }

    static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--version")) {
            IO.println(version());
            return;
        }

        Quirks quirks = Quirks.VIP;
        int next = 0;

        if (next < args.length && args[next].equals("--quirks")) {
            next++;
            if (next >= args.length) {
                IO.println(USAGE);
                return;
            }
            try {
                quirks = Quirks.forName(args[next]);
            } catch (IllegalArgumentException e) {
                IO.println(USAGE);
                return;
            }
            next++;
        }

        int remaining = args.length - next;
        if (remaining < 1 || remaining > 2) {
            IO.println(USAGE);
            return;
        }

        String romPath = args[next];
        int steps = 0;

        if (remaining == 2) {
            try {
                steps = Integer.parseInt(args[next + 1]);
            } catch (NumberFormatException e) {
                IO.println(USAGE);
                return;
            }
            if (steps < 1) {
                IO.println(USAGE);
                return;
            }
        }

        Memory memory = new Memory();

        try {
            memory.loadRom(Path.of(romPath));
        } catch (NoSuchFileException e) {
            IO.println("ROM not found: " + romPath);
            return;
        } catch (IOException e) {
            IO.println("Could not read ROM '" + romPath + "': " + e.getMessage());
            return;
        }

        Display display = new Display();
        Keypad keypad = new Keypad();
        Cpu cpu = new Cpu(memory, display, keypad, quirks);
        Emulator emulator = new Emulator(cpu, quirks);

        if (remaining == 1) {
            SwingUtilities.invokeLater(() ->
                openWindow(memory, display, keypad, cpu, emulator));
        } else {
            emulator.runSteps(steps);
            IO.print(display.dump());
            IO.println();
            IO.print(cpu.dump());
        }
    }
}
