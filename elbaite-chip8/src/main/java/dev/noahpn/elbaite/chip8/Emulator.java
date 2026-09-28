package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * The emulator program: it loads a ROM and runs it, either in the terminal for a given number of
 * steps or live in a window titled Achroite.
 *
 * <p>With a step count, it runs that many steps and prints the screen and registers to the
 * terminal. Without one, it opens the window and runs in real time, sixty frames a second, driven
 * by a Swing timer, with the keyboard mapped onto the keypad by {@link KeyMap}. A frame is ten
 * steps and a tick, and a draw ends its frame.
 *
 * <p>Instances drive a {@link Cpu}; both modes share {@link #runSteps(int)}.
 */
public final class Emulator {

    private static final int STEPS_PER_FRAME = 10;
    private static final int FRAMES_PER_SECOND = 60;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int CATCH_UP_LIMIT = 5;
    private static final int TIMER_DELAY_MS = 16;

    private final Cpu cpu;
    private long framesRun;

    /**
     * Creates an emulator that drives the given CPU.
     *
     * @param cpu the processor to step and tick, not {@code null}
     */
    public Emulator(Cpu cpu) {
        this.cpu = cpu;
    }

    /**
     * Runs the given number of steps, ticking the timers after every tenth step of this run:
     * after steps 10, 20, 30 and so on.
     *
     * <p>After a step that runs a {@code DXYN}, the rest of the frame passes without running
     * instructions: later steps up to the frame's tick do nothing. The tick itself still
     * happens, on its usual step, and the step after it runs normally again. The wait never
     * outlives this call.
     *
     * @param steps the number of steps to run
     */
    public void runSteps(int steps) {
        boolean waiting = false;
        for (int step = 1; step <= steps; step++) {
            if (!waiting) {
                Opcode opcode = cpu.step();
                if (opcode.high() == 0xD) {
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
     * @param elapsedNanos nanoseconds since the loop started
     * @return how many frames were run
     */
    public int catchUp(long elapsedNanos) {
        long due = framesDue(elapsedNanos);
        long behind = due - framesRun;
        int toRun = (int) Math.min(behind, CATCH_UP_LIMIT);

        for (int frame = 0; frame < toRun; frame++) {
            runSteps(STEPS_PER_FRAME);
        }

        framesRun = due;
        return toRun;
    }

    private static void openWindow(Display display, Keypad keypad, Emulator emulator) {
        JFrame frame = new JFrame("Achroite");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        DisplayPanel panel = new DisplayPanel(display);
        frame.add(panel);
        frame.setResizable(false);
        frame.pack();

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int key = KeyMap.keypadKey(event.getKeyCode());
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

        frame.setVisible(true);

        long start = System.nanoTime();
        Timer timer = new Timer(TIMER_DELAY_MS, _ -> {
            if (emulator.catchUp(System.nanoTime() - start) > 0) {
                panel.repaint();
            }
        });
        timer.start();
    }

    static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            IO.println("Usage: Emulator <rom-path> [steps]");
            return;
        }

        int steps = 0;

        if (args.length == 2) {
            try {
                steps = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                IO.println("Usage: Emulator <rom-path> [steps]");
                return;
            }
            if (steps < 1) {
                IO.println("Usage: Emulator <rom-path> [steps]");
                return;
            }
        }

        Memory memory = new Memory();

        try {
            memory.loadRom(Path.of(args[0]));
        } catch (NoSuchFileException e) {
            IO.println("ROM not found: " + args[0]);
            return;
        } catch (IOException e) {
            IO.println("Could not read ROM '" + args[0] + "': " + e.getMessage());
            return;
        }

        Display display = new Display();
        Keypad keypad = new Keypad();
        Cpu cpu = new Cpu(memory, display, keypad);
        Emulator emulator = new Emulator(cpu);

        if (args.length == 1) {
            SwingUtilities.invokeLater(() -> openWindow(display, keypad, emulator));
        } else {
            emulator.runSteps(steps);
            display.dump();
            IO.println();
            cpu.dump();
        }
    }
}
