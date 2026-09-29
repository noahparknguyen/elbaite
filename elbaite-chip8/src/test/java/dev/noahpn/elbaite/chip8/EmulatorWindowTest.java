package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmulatorWindowTest {

    @Test
    void iconsLoadSmallestFirst() {
        List<Image> icons = EmulatorWindow.icons();

        assertEquals(3, icons.size());
        assertEquals(16, icons.get(0).getWidth(null));
        assertEquals(32, icons.get(1).getWidth(null));
        assertEquals(96, icons.get(2).getWidth(null));
    }

    @Test
    void titleFollowsRomAndState() {
        Path rom = Path.of("roms/br8kout.ch8");

        assertEquals("Achroite", EmulatorWindow.title(null, false, false));
        assertEquals("Achroite — br8kout.ch8", EmulatorWindow.title(rom, false, false));
        assertEquals("Achroite — br8kout.ch8 (paused)", EmulatorWindow.title(rom, true, false));
        assertEquals("Achroite — br8kout.ch8 (stopped)", EmulatorWindow.title(rom, true, true));
    }

    @Test
    void romFilterAcceptsChipEightFiles() {
        FileNameExtensionFilter filter = EmulatorWindow.romFilter();

        assertTrue(filter.accept(new File("br8kout.ch8")));
        assertTrue(filter.accept(new File("PONG.C8")));
        assertFalse(filter.accept(new File("notes.txt")));
    }
}
