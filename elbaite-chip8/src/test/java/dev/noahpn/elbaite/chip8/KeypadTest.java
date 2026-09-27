package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeypadTest {

    private Keypad keypad;

    @BeforeEach
    void setUp() {
        keypad = new Keypad();
    }

    @Test
    void pressedKeyReadsPressed() {
        keypad.press(0xA);

        assertTrue(keypad.isPressed(0xA));
        assertFalse(keypad.isPressed(0xB));
    }

    @Test
    void releasedKeyReadsReleased() {
        keypad.press(0xA);
        keypad.release(0xA);

        assertFalse(keypad.isPressed(0xA));
    }

    @Test
    void outOfRangeKeyThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.press(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.press(16));
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.release(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.release(16));
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.isPressed(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> keypad.isPressed(16));
    }
}
