package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayPanelTest {

    private Display display;
    private DisplayPanel panel;

    @BeforeEach
    void setUp() {
        display = new Display();
        panel = new DisplayPanel(display);
    }

    private BufferedImage render() {
        BufferedImage image = new BufferedImage(960, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 960, 480);
        panel.setSize(960, 480);
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void preferredSizeScalesDisplayByFifteen() {
        assertEquals(960, panel.getPreferredSize().width);
        assertEquals(480, panel.getPreferredSize().height);
    }

    @Test
    void blankDisplayPaintsBlack() {
        BufferedImage image = render();

        assertEquals(Color.BLACK.getRGB(), image.getRGB(0, 0));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(959, 479));
    }

    @Test
    void litPixelPaintsWhiteSquare() {
        display.setPixel(3, 2, true);

        BufferedImage image = render();

        assertEquals(Color.WHITE.getRGB(), image.getRGB(45, 30));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(59, 44));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(60, 30));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(45, 45));
    }

    @Test
    void lastPixelPaintsBottomRightSquare() {
        display.setPixel(63, 31, true);

        BufferedImage image = render();

        assertEquals(Color.WHITE.getRGB(), image.getRGB(945, 465));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(959, 479));
    }
}
