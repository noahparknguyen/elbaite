package dev.noahpn.elbaite.chip8;

public final class Memory {

    public static final int SIZE = 4096;
    public static final int FONT_START = 0x050;

    private final byte[] memory = new byte[SIZE];

    public Memory() {
        loadFont();
    }

    private void loadFont() {
        for (int i = 0; i < Font.GLYPHS.length; i++) {
            write(FONT_START + i, Font.GLYPHS[i]);
        }
    }

    public int read(int address) {
        checkAddress(address);
        return Byte.toUnsignedInt(memory[address]);
    }

    public void write(int address, int value) {
        checkAddress(address);

        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(
                "CHIP-8 value out of range: " + value + " (valid: 0x00-0xFF)"
            );
        }
        memory[address] = (byte) value;
    }

    private void checkAddress(int address) {
        if (address < 0 || address >= SIZE) {
            throw new IndexOutOfBoundsException(
                "CHIP-8 address out of range: " + address + " (valid: 0x000-0xFFF)"
            );
        }
    }

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
        Memory memory = new Memory();
        memory.dump(0x000, 16);
        IO.println();
        memory.dump(FONT_START, 80);
    }
}
