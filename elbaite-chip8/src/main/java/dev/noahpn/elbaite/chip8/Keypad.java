package dev.noahpn.elbaite.chip8;

/**
 * The CHIP-8 hexadecimal keypad: sixteen keys, {@code 0x0} to {@code 0xF}, each held or released.
 *
 * <p>A new keypad has every key released.
 */
public final class Keypad {

    private static final int KEY_COUNT = 16;

    private final boolean[] keys = new boolean[KEY_COUNT];

    private void checkKey(int key) {
        if (key < 0 || key >= KEY_COUNT) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 key out of range: " + key + " (valid: 0x0-0xF)");
        }
    }

    /**
     * Marks a key as held.
     *
     * @param key the key to press, {@code 0x0} to {@code 0xF}
     * @throws IndexOutOfBoundsException if {@code key} is outside {@code 0x0} to {@code 0xF}
     */
    public void press(int key) {
        checkKey(key);
        keys[key] = true;
    }

    /**
     * Marks a key as released.
     *
     * @param key the key to release, {@code 0x0} to {@code 0xF}
     * @throws IndexOutOfBoundsException if {@code key} is outside {@code 0x0} to {@code 0xF}
     */
    public void release(int key) {
        checkKey(key);
        keys[key] = false;
    }

    /**
     * Returns whether a key is held.
     *
     * @param key the key to read, {@code 0x0} to {@code 0xF}
     * @return {@code true} if the key is held, {@code false} if it is released
     * @throws IndexOutOfBoundsException if {@code key} is outside {@code 0x0} to {@code 0xF}
     */
    public boolean isPressed(int key) {
        checkKey(key);
        return keys[key];
    }
}
