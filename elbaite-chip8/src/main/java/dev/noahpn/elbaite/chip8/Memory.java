package dev.noahpn.elbaite.chip8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * The CHIP-8 address space: 4 KB of byte-addressable memory holding the font, the loaded
 * program, and everything the running machine reads or writes.
 *
 * <p>Values are stored in a {@code byte[]}, but the byte-ness stays inside this class.
 * {@link #read} hands back {@code 0} to {@code 255}, so no caller ever deals with a
 * sign-extended byte.
 *
 * <p>This class fails loudly rather than quietly doing something plausible. An address
 * outside {@code 0x000} to {@code 0xFFF} and a value outside {@code 0} to {@code 255}
 * both throw, instead of wrapping or truncating.
 */
public final class Memory {

    public static final int SIZE = 4096;

    /**
     * The address a ROM is loaded at.
     *
     * <p>On the COSMAC VIP the interpreter itself occupied the first 512 bytes, so
     * programs were assembled to start above it. Every ROM still assumes it.
     */
    public static final int PROGRAM_START = 0x200;

    /**
     * The address the font is loaded at.
     *
     * <p>Conventional rather than architectural. It sits in the same low region the
     * interpreter once occupied, which no ROM uses.
     */
    public static final int FONT_START = 0x050;

    private final byte[] memory = new byte[SIZE];

    /**
     * Creates a zeroed address space with {@link Font#GLYPHS} already loaded at
     * {@link #FONT_START}, so callers never load the font themselves.
     */
    public Memory() {
        loadFont();
    }

    private void loadFont() {
        for (int i = 0; i < Font.GLYPHS.length; i++) {
            write(FONT_START + i, Font.GLYPHS[i]);
        }
    }

    /**
     * Loads a ROM image into memory starting at {@link #PROGRAM_START}.
     *
     * <p>Everything below {@link #PROGRAM_START}, including the font, is left untouched.
     *
     * @param rom the ROM image, at most {@code SIZE - PROGRAM_START} bytes
     * @throws IllegalArgumentException if {@code rom} is too large to fit above
     *                                  {@link #PROGRAM_START}
     */
    public void loadRom(byte[] rom) {
        if (rom.length > SIZE - PROGRAM_START) {
            throw new IllegalArgumentException(
                "CHIP-8 ROM too large: " + rom.length
                    + " bytes (max: " + (SIZE - PROGRAM_START) + ")");
        }
        for (int i = 0; i < rom.length; i++) {
            write(PROGRAM_START + i, Byte.toUnsignedInt(rom[i]));
        }
    }

    /**
     * Reads the file at {@code path} and loads its contents as a ROM image.
     *
     * @param path the ROM file to read
     * @return the number of bytes loaded
     * @throws IOException              if the file cannot be read, including when it
     *                                  does not exist
     * @throws IllegalArgumentException if the file is too large to fit above
     *                                  {@link #PROGRAM_START}
     */
    public int loadRom(Path path) throws IOException {
        byte[] rom = Files.readAllBytes(path);
        loadRom(rom);
        return rom.length;
    }

    /**
     * Returns the byte at the given address as an unsigned value.
     *
     * <p>The result is always {@code 0} to {@code 255}. Reading through this method
     * rather than the array is what keeps a sign-extended byte from escaping.
     *
     * @param address the address to read, {@code 0x000} to {@code 0xFFF}
     * @return the byte value, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code address} is outside {@code 0x000} to
     *                                   {@code 0xFFF}
     */
    public int read(int address) {
        checkAddress(address);
        return Byte.toUnsignedInt(memory[address]);
    }

    /**
     * Stores a single byte value at the given address.
     *
     * <p>The value must fit in a byte, and this method rejects rather than truncates:
     * passing {@code 300} throws instead of quietly storing {@code 44}.
     *
     * @param address the address to write, {@code 0x000} to {@code 0xFFF}
     * @param value   the value to store, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if {@code address} is outside {@code 0x000} to
     *                                   {@code 0xFFF}
     * @throws IllegalArgumentException  if {@code value} is outside {@code 0} to
     *                                   {@code 255}
     */
    public void write(int address, int value) {
        checkAddress(address);

        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException(
                "CHIP-8 memory value out of range: " + value + " (valid: 0x00-0xFF)");
        }
        memory[address] = (byte) value;
    }

    private void checkAddress(int address) {
        if (address < 0 || address >= SIZE) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 address out of range: " + address + " (valid: 0x000-0xFFF)");
        }
    }

    /**
     * Prints a hex dump of a region of memory to standard output.
     *
     * <p>Each line is the address of its first byte as four hex digits, then up to
     * sixteen byte values as two hex digits each. The last line is short if
     * {@code length} is not a multiple of sixteen.
     *
     * @param start  the address of the first byte to print
     * @param length how many bytes to print
     * @throws IndexOutOfBoundsException if any address in the region is outside
     *                                   {@code 0x000} to {@code 0xFFF}
     */
    public void dump(int start, int length) {
        for (int offset = 0; offset < length; offset += 16) {
            IO.print(String.format("%04X  ", start + offset));
            for (int column = 0; column < 16 && offset + column < length; column++) {
                if (column > 0) {
                    IO.print(" ");
                }
                IO.print(String.format("%02X", read(start + offset + column)));
            }
            IO.println();
        }
    }

    static void main(String[] args) {
        if (args.length == 0) {
            IO.println("Usage: Memory <rom-path>");
            return;
        }

        Memory memory = new Memory();
        Path path = Path.of(args[0]);
        try {
            int length = memory.loadRom(path);
            IO.println("Loaded " + length + " bytes at 0x200");
            IO.println();
            memory.dump(PROGRAM_START, length);
        } catch (NoSuchFileException e) {
            IO.println("ROM not found: " + args[0]);
        } catch (IOException e) {
            IO.println("Could not read ROM '" + args[0] + "': " + e.getMessage());
        }
    }
}
