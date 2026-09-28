package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeeperTest {

    @Test
    void squareWaveAlternatesHalfPeriods() {
        assertArrayEquals(
            new byte[]{20, 20, -20, -20, 20, 20, -20, -20},
            Beeper.squareWave(8, 4, 20));
    }

    @Test
    void squareWaveReachesFullAmplitude() {
        assertArrayEquals(
            new byte[]{127, -127},
            Beeper.squareWave(2, 2, 127));
    }

    @Test
    void outOfRangeAmplitudeThrows() {
        assertThrows(IllegalArgumentException.class, () -> Beeper.squareWave(2, 2, 128));
        assertThrows(IllegalArgumentException.class, () -> Beeper.squareWave(2, 2, -1));
    }
}
