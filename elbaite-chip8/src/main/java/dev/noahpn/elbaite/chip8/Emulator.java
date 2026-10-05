package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * The emulator program and its clock: it loads a ROM and runs it, either in the terminal for a
 * given number of steps or live in an {@link EmulatorWindow}.
 *
 * <p>The command line is {@code Emulator [--quirks vip|schip|octo] [<rom-path> [steps]]}, read by
 * {@link Command#parse}. The optional {@code --quirks} must come first and picks a preset from
 * {@link Quirks#forName}; without it, {@link Quirks#VIP}. The same preset goes to both the
 * {@link Cpu} and the emulator. In the window it is where the Quirks menu starts.
 *
 * <p>{@code --version} on its own prints the name and version and exits.
 *
 * <p>With a step count, it runs that many steps and prints the screen and registers to the
 * terminal. Without one, it opens the window, which runs the ROM in real time, sixty frames a
 * second, or opens empty when there is no ROM path. Either way the ROM runs on a {@link Machine},
 * built from it and the preset. A ROM that cannot be loaded is reported in one message,
 * {@code loadFailure}: printed in the terminal, shown in a dialogue by the window.
 *
 * <p>Instances are the machine's clock. They drive a {@link Cpu} in frames: a frame is ten
 * steps and a tick, and with display wait on, a draw ends its frame. Both modes share
 * {@link #runSteps(int)}; the window paces it with {@link #catchUp(long)}, and pauses and steps
 * it.
 */
public final class Emulator {

    private static final int STEPS_PER_FRAME = 10;
    private static final int FRAMES_PER_SECOND = 60;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int CATCH_UP_LIMIT = 5;

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
     * Runs the given number of steps, ticking the timers after every tenth step of this run: after
     * steps 10, 20, 30 and so on.
     *
     * <p>When the quirks have display wait on, the rest of the frame after a step that runs a
     * {@code DXYN} passes without running instructions: later steps up to the frame's tick do
     * nothing. The tick itself still happens, on its usual step, and the step after it runs
     * normally again. The wait never outlives this call.
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
     * Returns how many whole frames fit in the given elapsed time at 60 frames a second, rounding
     * down.
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
     * run. {@code elapsedNanos} counts from the loop's start and never goes down between calls.
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
     * Returns whether the tone should be sounding: true while the CPU's sound timer is above zero.
     * Always {@code false} while paused, so a frozen sound timer does not drone through a pause.
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
     * Pauses or resumes the emulator. While paused, {@link #catchUp(long)} runs no frames (the
     * frames due count as done) and {@link #isSounding()} is {@code false}.
     *
     * @param paused {@code true} to pause, {@code false} to resume
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Runs exactly one instruction through the CPU and returns the opcode it ran. The timers never
     * move: this is {@link Cpu#step()}, not {@link #runSteps(int)}, so there is no tick and no
     * display wait. It does not check the pause itself; the window only calls it while paused.
     *
     * @return the opcode the CPU executed
     */
    public Opcode stepInstruction() {
        return cpu.step();
    }

    /**
     * Returns the program's name and version. When running from the jar, the version comes from the
     * manifest's {@code Implementation-Version}, which the jar plugin fills in from the POM, so
     * nothing here hard-codes it. When running from plain class files, as every test and every
     * {@code exec:java} run does, there is no manifest, and this returns
     * {@code Achroite (development build)}.
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
     * Returns the one-line message for a ROM that {@link Machine#load(Path, Quirks)} could not
     * load: the terminal prints it, and the window shows it in a dialogue. A missing file gives
     * {@code ROM not found: <rom>}, another read failure gives
     * {@code Could not read ROM '<rom>': <reason>}, and a ROM that does not fit gives
     * {@code Could not load ROM '<rom>': <reason>}.
     *
     * @param rom the ROM file that failed
     * @param e   what {@code load} threw: an {@link IOException} if the file could not be read, or
     *            an {@link IllegalArgumentException} if it was too large
     * @return the message
     */
    static String loadFailure(Path rom, Exception e) {
        return switch (e) {
            case NoSuchFileException _ -> "ROM not found: " + rom;
            case IOException _ -> "Could not read ROM '" + rom + "': " + e.getMessage();
            default -> "Could not load ROM '" + rom + "': " + e.getMessage();
        };
    }

    // Loads the ROM, or prints why it could not and returns null.
    private static Machine load(Path rom, Quirks quirks) {
        try {
            return Machine.load(rom, quirks);
        } catch (IOException | IllegalArgumentException e) {
            IO.println(loadFailure(rom, e));
            return null;
        }
    }

    static void main(String[] args) {
        Command command;
        try {
            command = Command.parse(args);
        } catch (IllegalArgumentException e) {
            IO.println(Command.USAGE);
            return;
        }

        switch (command) {
            case Command.ShowVersion _ -> IO.println(version());
            case Command.RunInTerminal(Quirks quirks, Path rom, int steps) -> {
                Machine machine = load(rom, quirks);
                if (machine != null) {
                    machine.emulator().runSteps(steps);
                    IO.print(machine.display().dump());
                    IO.println();
                    IO.print(machine.cpu().dump());
                }
            }
            case Command.OpenWindow(Quirks quirks, Path rom) ->
                SwingUtilities.invokeLater(() -> EmulatorWindow.open(quirks, rom));
        }
    }
}
