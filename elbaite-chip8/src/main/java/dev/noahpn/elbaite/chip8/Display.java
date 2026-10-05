package dev.noahpn.elbaite.chip8;

import java.util.Arrays;

/**
 * The CHIP-8 screen: a 64 by 32 grid of pixels, each either lit or dark.
 *
 * <p>A new display has every pixel dark.
 *
 * <p>Coordinates run from the top-left corner. {@code x} goes {@code 0} to {@code 63}
 * left to right, {@code y} goes {@code 0} to {@code 31} top to bottom. The pixel at {@code (x, y)}
 * is stored at {@code y * WIDTH + x} in a flat array — row after row, left to right, the way text
 * is laid out on a page.
 *
 * <p>Pixels are {@code boolean}, not {@code int}. A pixel has exactly two states and a
 * {@code boolean} has exactly two values, so an invalid pixel cannot be stored. That is why nothing
 * here range-checks a pixel value the way {@link Cpu} checks a register.
 *
 * <p>The screen is its own class rather than a region of {@link Memory}. On the original
 * machine it sat inside RAM, but no CHIP-8 program reads it directly — only {@code 00E0} and the
 * draw instruction touch it.
 */
public final class Display {

    public static final int WIDTH = 64;

    public static final int HEIGHT = 32;

    private final boolean[] pixelBuffer = new boolean[WIDTH * HEIGHT];

    /**
     * Returns whether one pixel is lit.
     *
     * @param x the column, {@code 0} to {@code 63}
     * @param y the row, {@code 0} to {@code 31}
     * @return {@code true} if the pixel is lit, {@code false} if it is dark
     * @throws IndexOutOfBoundsException if {@code x} is outside {@code 0} to {@code 63} or
     *                                   {@code y} is outside {@code 0} to {@code 31}
     */
    public boolean getPixel(int x, int y) {
        checkPixel(x, y);
        return pixelBuffer[y * WIDTH + x];
    }

    /**
     * Turns one pixel on or off.
     *
     * @param x  the column, {@code 0} to {@code 63}
     * @param y  the row, {@code 0} to {@code 31}
     * @param on {@code true} to light the pixel, {@code false} to darken it
     * @throws IndexOutOfBoundsException if {@code x} is outside {@code 0} to {@code 63} or
     *                                   {@code y} is outside {@code 0} to {@code 31}
     */
    public void setPixel(int x, int y, boolean on) {
        checkPixel(x, y);
        pixelBuffer[y * WIDTH + x] = on;
    }

    private void checkPixel(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 pixel out of range: " + x + ", " + y + " (valid: 0-63, 0-31)");
        }
    }

    /**
     * Inverts one pixel and reports what it was before the flip.
     *
     * <p>A dark pixel becomes lit and this returns {@code false}; a lit pixel becomes
     * dark and this returns {@code true}. It is the <em>previous</em> state, not the new one,
     * because that is what {@code DXYN} needs: a pixel that was lit and just got switched off means
     * two sprites overlapped, which is a collision.
     *
     * @param x the column, {@code 0} to {@code 63}
     * @param y the row, {@code 0} to {@code 31}
     * @return whether the pixel was lit before the flip
     * @throws IndexOutOfBoundsException if {@code x} or {@code y} is outside the display
     */
    public boolean flipPixel(int x, int y) {
        boolean pixel = getPixel(x, y);
        setPixel(x, y, !pixel);
        return pixel;
    }

    /**
     * Turns every pixel off.
     *
     * <p>This is what {@code 00E0} does.
     */
    public void clear() {
        Arrays.fill(pixelBuffer, false);
    }

    /**
     * Returns the whole screen as text: {@link #HEIGHT} lines of {@link #WIDTH} characters, a
     * filled block for a lit pixel and a space for a dark one. Every line ends with a line
     * separator, the last included.
     *
     * <p>The same two characters {@link Font} uses, so the screen and the font viewer
     * read the same way. A blank background rather than a dotted one reads as a picture, at the
     * cost of showing where the screen ends.
     *
     * <p>This returns the text; it does not print it.
     *
     * @return the screen as text
     */
    public String dump() {
        StringBuilder result = new StringBuilder();

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                result.append(getPixel(x, y) ? '█' : ' ');
            }
            result.append(System.lineSeparator());
        }

        return result.toString();
    }

    static void main() {
        Display display = new Display();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1) {
                    display.setPixel(x, y, true);
                }
            }
        }
        IO.print(display.dump());
    }
}
