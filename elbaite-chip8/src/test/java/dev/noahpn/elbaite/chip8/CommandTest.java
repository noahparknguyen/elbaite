package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandTest {

    @Test
    void loneVersionShowsVersion() {
        assertEquals(new Command.ShowVersion(), Command.parse(new String[]{"--version"}));
    }

    @Test
    void noArgumentsOpensEmptyWindow() {
        // What a double-clicked app gets: no arguments at all.
        assertEquals(new Command.OpenWindow(Quirks.VIP, null), Command.parse(new String[]{}));
    }

    @Test
    void quirksAloneOpensEmptyWindow() {
        Command command = Command.parse(new String[]{"--quirks", "schip"});

        assertEquals(new Command.OpenWindow(Quirks.SUPER_CHIP, null), command);
    }

    @Test
    void romAloneOpensWindow() {
        Command command = Command.parse(new String[]{"roms/ibm-logo.ch8"});

        assertEquals(new Command.OpenWindow(Quirks.VIP, Path.of("roms/ibm-logo.ch8")), command);
    }

    @Test
    void romAndStepsRunInTerminal() {
        Command command = Command.parse(new String[]{"roms/ibm-logo.ch8", "200"});

        assertEquals(
            new Command.RunInTerminal(Quirks.VIP, Path.of("roms/ibm-logo.ch8"), 200), command);
    }

    @Test
    void quirksOptionPicksPreset() {
        String[] args = {"--quirks", "octo", "roms/sprite-rate.ch8", "1000"};

        Command command = Command.parse(args);

        assertEquals(
            new Command.RunInTerminal(Quirks.OCTO, Path.of("roms/sprite-rate.ch8"), 1000),
            command);
    }

    @Test
    void malformedCommandLineThrows() {
        // Each of these prints the usage line. No arguments, and --quirks with a preset but no
        // ROM, open the window empty instead (Lab 27).
        String[][] malformed = {
            {"--quirks"},
            {"--quirks", "turbo", "roms/ibm-logo.ch8"},
            {"roms/ibm-logo.ch8", "200", "--quirks", "schip"},
            {"roms/ibm-logo.ch8", "zero"},
            {"roms/ibm-logo.ch8", "0"},
            {"a", "b", "c"},
            {"--version", "roms/ibm-logo.ch8"}
        };

        for (String[] args : malformed) {
            assertThrows(IllegalArgumentException.class, () -> Command.parse(args),
                String.join(" ", args));
        }
    }
}
