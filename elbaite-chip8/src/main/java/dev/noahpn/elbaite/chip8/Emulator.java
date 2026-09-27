package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * The emulator program: it loads a ROM, runs it, and shows the result in a window titled Achroite.
 *
 * <p>For now it runs a fixed 1000 steps, ticking the timers every ten steps as the terminal harness
 * in {@link Cpu} does, then opens the window on whatever the display holds. The picture is still:
 * nothing advances it once it is up.
 *
 * <p>Never instantiated: it is a program, not a thing.
 */
public final class Emulator {

    private static final int STEPS = 1000;
    private static final int STEPS_PER_TICK = 10;

    private Emulator() {
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
        if (args.length != 1) {
            IO.println("Usage: Emulator <rom-path>");
            return;
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

        for (int i = 1; i <= STEPS; i++) {
            cpu.step();
            if (i % STEPS_PER_TICK == 0) {
                cpu.tick();
            }
        }

        SwingUtilities.invokeLater(() -> openWindow(display));
    }
}
