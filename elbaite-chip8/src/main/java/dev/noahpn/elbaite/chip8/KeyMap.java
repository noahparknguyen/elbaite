package dev.noahpn.elbaite.chip8;

import java.awt.event.KeyEvent;

/**
 * Maps a {@link KeyEvent} key code onto a CHIP-8 keypad key.
 *
 * <p>The VIP's sixteen-key keypad has the same four-by-four shape as the top-left
 * block of a modern keyboard, so the two are lined up directly:
 *
 * <pre>
 *   VK_1 VK_2 VK_3 VK_4      0x1 0x2 0x3 0xC
 *   VK_Q VK_W VK_E VK_R  ->  0x4 0x5 0x6 0xD
 *   VK_A VK_S VK_D VK_F      0x7 0x8 0x9 0xE
 *   VK_Z VK_X VK_C VK_V      0xA 0x0 0xB 0xF
 * </pre>
 */
public final class KeyMap {

    private KeyMap() {
    }

    /**
     * Returns the CHIP-8 keypad key for a {@link KeyEvent} key code.
     *
     * @param keyCode a {@code KeyEvent.VK_*} key code
     * @return the keypad key, {@code 0x0} through {@code 0xF}, or {@code -1} if the key is not on
     * the keypad
     */
    public static int keypadKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_1 -> 0x1;
            case KeyEvent.VK_2 -> 0x2;
            case KeyEvent.VK_3 -> 0x3;
            case KeyEvent.VK_4 -> 0xC;
            case KeyEvent.VK_Q -> 0x4;
            case KeyEvent.VK_W -> 0x5;
            case KeyEvent.VK_E -> 0x6;
            case KeyEvent.VK_R -> 0xD;
            case KeyEvent.VK_A -> 0x7;
            case KeyEvent.VK_S -> 0x8;
            case KeyEvent.VK_D -> 0x9;
            case KeyEvent.VK_F -> 0xE;
            case KeyEvent.VK_Z -> 0xA;
            case KeyEvent.VK_X -> 0x0;
            case KeyEvent.VK_C -> 0xB;
            case KeyEvent.VK_V -> 0xF;
            default -> -1;
        };
    }
}
