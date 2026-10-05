package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.awt.*;

/**
 * A Swing panel that draws a {@link Display} as a grid of squares, one per CHIP-8 pixel.
 *
 * <p>Each CHIP-8 pixel becomes a 15 by 15 screen-pixel square, so the panel asks for
 * 960 by 480. A lit pixel is white; an unlit pixel is the black background.
 *
 * <p>The panel holds the display and reads it every time it paints: it keeps no copy
 * of the pixels. The display stays the one place the picture lives, so the window and the emulator
 * cannot drift apart, and a test that paints this panel into an image sees exactly what a window
 * would show.
 */
public final class DisplayPanel extends JPanel {

    private static final int SCALE = 15;

    private final Display display;

    /**
     * Creates a panel that draws the given display on a black background.
     *
     * @param display the display to draw, not {@code null}
     */
    public DisplayPanel(Display display) {
        this.display = display;
        setBackground(Color.BLACK);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Display.WIDTH * SCALE, Display.HEIGHT * SCALE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(Color.WHITE);
        for (int y = 0; y < Display.HEIGHT; y++) {
            for (int x = 0; x < Display.WIDTH; x++) {
                if (display.getPixel(x, y)) {
                    g.fillRect(x * SCALE, y * SCALE, SCALE, SCALE);
                }
            }
        }
    }
}
