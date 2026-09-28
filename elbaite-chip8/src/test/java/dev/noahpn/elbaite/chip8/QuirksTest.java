package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuirksTest {

    @Test
    void forNameFindsEachPreset() {
        assertSame(Quirks.VIP, Quirks.forName("vip"));
        assertSame(Quirks.SUPER_CHIP, Quirks.forName("schip"));
        assertSame(Quirks.OCTO, Quirks.forName("octo"));
    }

    @Test
    void unknownPresetNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> Quirks.forName("chip8"));
        assertThrows(IllegalArgumentException.class, () -> Quirks.forName("VIP"));
    }
}
