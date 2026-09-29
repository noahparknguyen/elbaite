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
        // Each of these printed the usage line before Command existed, and still must.
        String[][] malformed = {
            {},
            {"--quirks"},
            {"--quirks", "schip"},
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
