package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * The emulator program: it loads a ROM and runs it, either in the terminal for a given number of
 * steps or in a window titled Achroite.
 *
 * <p>With a step count, it runs that many steps and prints the screen and registers to the
 * terminal. Without one, it runs 1000 steps and shows the result in the window, as a still picture.
 *
 * <p>Instances drive a {@link Cpu}; both modes share {@link #runSteps(int)}.
 */
public final class Emulator {

    private static final int STEPS = 1000;
    private static final int STEPS_PER_FRAME = 10;

    private final Cpu cpu;

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
     * @param steps the number of steps to run
     */
    public void runSteps(int steps) {
        for (int step = 1; step <= steps; step++) {
            cpu.step();
            if (step % STEPS_PER_FRAME == 0) {
                cpu.tick();
            }
        }
    }

    private static void openWindow(Display display) {
        JFrame frame = new JFrame("Achroite");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new DisplayPanel(display));
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }

    static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            IO.println("Usage: Emulator <rom-path> [steps]");
            return;
        }

        int steps = STEPS;

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
        Cpu cpu = new Cpu(memory, display);
        Emulator emulator = new Emulator(cpu);

        emulator.runSteps(steps);

        if (args.length == 1) {
            SwingUtilities.invokeLater(() -> openWindow(display));
        } else {
            display.dump();
            IO.println();
            cpu.dump();
        }
    }
}
