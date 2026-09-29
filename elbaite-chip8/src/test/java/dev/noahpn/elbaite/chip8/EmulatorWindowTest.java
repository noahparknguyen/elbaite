package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmulatorWindowTest {

    @Test
    void iconsLoadSmallestFirst() {
        List<Image> icons = EmulatorWindow.icons();

        assertEquals(3, icons.size());
        assertEquals(16, icons.get(0).getWidth(null));
        assertEquals(32, icons.get(1).getWidth(null));
        assertEquals(96, icons.get(2).getWidth(null));
    }
}
