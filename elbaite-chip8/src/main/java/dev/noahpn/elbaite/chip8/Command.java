package dev.noahpn.elbaite.chip8;

import java.nio.file.Path;

/**
 * What the command line asks the emulator to do: show its version, run a ROM in the
 * terminal, or open the window.
 *
 * <p>{@link #parse(String[])} reads {@code Emulator [--quirks vip|schip|octo] [<rom-path>
 * [steps]]}, or {@code --version} on its own. The three answers carry different things, so
 * each is a record of its own, and {@code main} switches on which one it got.
 */
public sealed interface Command {

    /**
     * The line printed for any command line that does not parse.
     */
    String USAGE = "Usage: Emulator [--quirks vip|schip|octo] [<rom-path> [steps]]";

    /**
     * Print the name and version, then exit: {@code --version} on its own.
     */
    record ShowVersion() implements Command {
    }

    /**
     * Run a ROM for a number of steps and print the screen and registers.
     *
     * @param quirks the preset to run it with
     * @param rom    the ROM file
     * @param steps  how many steps to run, at least {@code 1}
     */
    record RunInTerminal(Quirks quirks, Path rom, int steps) implements Command {
    }

    /**
     * Open the window, running a ROM or empty and ready to open one.
     *
     * @param quirks the preset its Quirks menu starts on
     * @param rom    the ROM file, or {@code null} to open the window empty
     */
    record OpenWindow(Quirks quirks, Path rom) implements Command {
    }

    /**
     * Reads a command line. {@code --quirks} and its preset name may only come first;
     * without them the preset is {@link Quirks#VIP}. With no ROM path, the window opens
     * empty. A step count needs a ROM before it and must be a whole number of at least
     * {@code 1}.
     *
     * @param args the arguments given to {@code main}
     * @return what they ask for
     * @throws IllegalArgumentException if they do not follow the usage line: an unknown
     *                                  preset, {@code --quirks} without one or anywhere
     *                                  but first, a bad step count, or too many arguments
     */
    static Command parse(String[] args) {
        if (args.length == 1 && args[0].equals("--version")) {
            return new ShowVersion();
        }

        Quirks quirks = Quirks.VIP;
        int next = 0;

        if (next < args.length && args[next].equals("--quirks")) {
            next++;
            if (next >= args.length) {
                throw new IllegalArgumentException("CHIP-8 command line: --quirks needs a preset");
            }
            quirks = Quirks.forName(args[next]);
            next++;
        }

        int remaining = args.length - next;
        if (remaining == 0) {
            return new OpenWindow(quirks, null);
        }
        if (remaining > 2) {
            throw new IllegalArgumentException(
                "CHIP-8 command line: expected at most a ROM path and a step count");
        }

        Path rom = Path.of(args[next]);
        if (remaining == 1) {
            return new OpenWindow(quirks, rom);
        }

        int steps;
        try {
            steps = Integer.parseInt(args[next + 1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "CHIP-8 command line: steps must be a whole number: " + args[next + 1]);
        }
        if (steps < 1) {
            throw new IllegalArgumentException(
                "CHIP-8 command line: steps must be at least 1: " + steps);
        }

        return new RunInTerminal(quirks, rom, steps);
    }
}
