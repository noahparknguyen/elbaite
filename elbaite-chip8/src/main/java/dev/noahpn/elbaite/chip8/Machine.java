package dev.noahpn.elbaite.chip8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One CHIP-8 machine running one ROM: its memory, display, keypad and processor, and the emulator
 * that clocks them, built together and wired to each other.
 *
 * <p>{@link #load(byte[], Quirks)} is how a machine is made. It creates every part, connects them,
 * and loads the ROM, so the five always belong together: the CPU fetches from this memory, draws to
 * this display and reads this keypad, and the emulator drives this CPU. The same {@link Quirks} go
 * to the CPU and to the emulator. A machine is never reused: another ROM, or the same ROM with
 * other quirks, gets a new machine.
 *
 * @param memory   the address space, with the font and the ROM loaded
 * @param display  the screen the CPU draws to
 * @param keypad   the keys the CPU reads
 * @param cpu      the processor, wired to the three parts above
 * @param emulator the clock that runs the CPU in frames
 */
public record Machine(Memory memory, Display display, Keypad keypad, Cpu cpu,
                      Emulator emulator) {

    /**
     * Builds a machine with the given ROM loaded at {@link Memory#PROGRAM_START} and the given
     * quirks, ready to run from its first instruction.
     *
     * @param rom    the ROM image, at most {@code Memory.SIZE - Memory.PROGRAM_START} bytes
     * @param quirks the behaviours to use where interpreters differ, not {@code null}
     * @return the new machine
     * @throws IllegalArgumentException if {@code rom} is too large to fit above
     *                                  {@link Memory#PROGRAM_START}
     */
    public static Machine load(byte[] rom, Quirks quirks) {
        Memory memory = new Memory();
        memory.loadRom(rom);
        Display display = new Display();
        Keypad keypad = new Keypad();
        Cpu cpu = new Cpu(memory, display, keypad, quirks);

        return new Machine(memory, display, keypad, cpu, new Emulator(cpu, quirks));
    }

    /**
     * Reads the ROM file at {@code rom} and builds a machine running it, as
     * {@link #load(byte[], Quirks)} does.
     *
     * @param rom    the ROM file to read
     * @param quirks the behaviours to use where interpreters differ, not {@code null}
     * @return the new machine
     * @throws IOException              if the file cannot be read, including when it does not
     *                                  exist
     * @throws IllegalArgumentException if the file is too large to fit above
     *                                  {@link Memory#PROGRAM_START}
     */
    public static Machine load(Path rom, Quirks quirks) throws IOException {
        return load(Files.readAllBytes(rom), quirks);
    }
}
