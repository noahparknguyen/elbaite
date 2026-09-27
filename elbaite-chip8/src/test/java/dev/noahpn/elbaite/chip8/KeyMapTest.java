package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyMapTest {

    @Test
    void keypadKeyFollowsLayout() {
        assertEquals(0x1, KeyMap.keypadKey(KeyEvent.VK_1));
        assertEquals(0xC, KeyMap.keypadKey(KeyEvent.VK_4));
        assertEquals(0x4, KeyMap.keypadKey(KeyEvent.VK_Q));
        assertEquals(0xD, KeyMap.keypadKey(KeyEvent.VK_R));
        assertEquals(0x7, KeyMap.keypadKey(KeyEvent.VK_A));
        assertEquals(0xE, KeyMap.keypadKey(KeyEvent.VK_F));
        assertEquals(0xA, KeyMap.keypadKey(KeyEvent.VK_Z));
        assertEquals(0x0, KeyMap.keypadKey(KeyEvent.VK_X));
        assertEquals(0xF, KeyMap.keypadKey(KeyEvent.VK_V));
    }

    @Test
    void unmappedKeyReturnsMinusOne() {
        assertEquals(-1, KeyMap.keypadKey(KeyEvent.VK_P));
        assertEquals(-1, KeyMap.keypadKey(KeyEvent.VK_SPACE));
    }
}
