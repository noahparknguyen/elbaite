package dev.noahpn.elbaite.chip8;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * Plays the CHIP-8 tone: a 441 Hz square wave, on or off.
 *
 * <p>The tone is one second of samples, looped: 8-bit signed mono at 44,100 samples a
 * second. At 441 Hz a second holds exactly 441 periods, so the loop has no seam.
 */
public final class Beeper {

    private static final int SAMPLE_RATE = 44_100;
    private static final int FREQUENCY = 441;
    private static final int AMPLITUDE = 32;

    private final Clip clip;
    private boolean on;

    /**
     * Creates a beeper with its tone loaded, silent until {@link #setOn(boolean)} starts it.
     *
     * @throws LineUnavailableException if no line can play the tone, including when the system has
     *                                  no sound device at all
     */
    public Beeper() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        byte[] tone = squareWave(SAMPLE_RATE, SAMPLE_RATE / FREQUENCY, AMPLITUDE);

        try {
            clip = AudioSystem.getClip();
        } catch (IllegalArgumentException e) {
            throw new LineUnavailableException(e.getMessage());
        }

        clip.open(format, tone, 0, tone.length);
    }

    /**
     * Starts or stops the tone. Does nothing if it is already in that state.
     *
     * @param on {@code true} to sound, {@code false} to be silent
     */
    public void setOn(boolean on) {
        if (on == this.on) {
            return;
        }

        if (on) {
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } else {
            clip.stop();
        }

        this.on = on;
    }

    /**
     * Returns the samples of a square wave. Each period is {@code period / 2} samples at
     * {@code +amplitude} followed by the rest at {@code -amplitude}, starting high; a partial
     * period at the end is cut off where {@code length} runs out.
     *
     * @param length    number of samples to return
     * @param period    samples per full period, positive
     * @param amplitude high and low level, {@code 0} to {@code 127}
     * @return {@code length} samples, starting high
     * @throws IllegalArgumentException if {@code amplitude} is outside {@code 0} to {@code 127}
     */
    public static byte[] squareWave(int length, int period, int amplitude) {
        if (amplitude < 0 || amplitude > 127) {
            throw new IllegalArgumentException(
                "CHIP-8 amplitude out of range: " + amplitude + " (valid: 0-127)");
        }

        byte[] samples = new byte[length];
        int halfPeriod = period / 2;

        for (int i = 0; i < length; i++) {
            if (i % period < halfPeriod) {
                samples[i] = (byte) amplitude;
            } else {
                samples[i] = (byte) -amplitude;
            }
        }

        return samples;
    }

    static void main() {
        try {
            Beeper beeper = new Beeper();
            beeper.setOn(true);
            Thread.sleep(1000);
            beeper.setOn(false);
        } catch (LineUnavailableException e) {
            IO.println("Sound unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
