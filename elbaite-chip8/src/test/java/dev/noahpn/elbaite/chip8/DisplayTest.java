package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplayTest {

    private Display display;

    @BeforeEach
    void setUp() {
        display = new Display();
    }

    @Test
    void newDisplayStartsBlank() {
        for (int y = 0; y < Display.HEIGHT; y++) {
            for (int x = 0; x < Display.WIDTH; x++) {
                assertFalse(display.getPixel(x, y),
                    "pixel (" + x + ", " + y + ") should start off");
            }
        }
    }

    @Test
    void pixelRoundTrips() {
        display.setPixel(63, 31, true);
        assertTrue(display.getPixel(63, 31));

        display.setPixel(63, 31, false);
        assertFalse(display.getPixel(63, 31));
    }

    @Test
    void flipPixelReturnsPreviousState() {
        display.setPixel(0, 0, true);
        assertTrue(display.getPixel(0, 0));

        boolean previous = display.flipPixel(0, 0);
        assertTrue(previous);
        assertFalse(display.getPixel(0, 0));
    }

    @Test
    void outOfRangePixelThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> display.getPixel(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> display.getPixel(64, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> display.getPixel(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> display.getPixel(0, 32));

        assertThrows(IndexOutOfBoundsException.class, () -> display.setPixel(-1, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> display.setPixel(64, 0, true));
        assertThrows(IndexOutOfBoundsException.class, () -> display.setPixel(0, -1, true));
        assertThrows(IndexOutOfBoundsException.class, () -> display.setPixel(0, 32, true));
    }

    @Test
    void clearTurnsEveryPixelOff() {
        display.setPixel(0, 0, true);
        display.setPixel(10, 10, true);
        display.setPixel(63, 31, true);

        display.clear();

        for (int y = 0; y < Display.HEIGHT; y++) {
            for (int x = 0; x < Display.WIDTH; x++) {
                assertFalse(display.getPixel(x, y),
                    "pixel (" + x + ", " + y + ") should be off after clear");
            }
        }
    }
}
