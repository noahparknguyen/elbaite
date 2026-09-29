<!--suppress HtmlDeprecatedAttribute, CheckImageSize: GitHub strips CSS from a README, so align is the only
    way to centre, and the screenshots are shown smaller than they are on purpose. -->

# Elbaite

<p align="center">
  <img src="docs/elbaite.png" alt="The Elbaite logo: a tourmaline crystal in colour zones" width="256">
</p>

Emulators written in Java, each named for a variety of elbaite, a kind of tourmaline. The first, **Achroite**, is a
CHIP-8 emulator, and it's done. Achroite is elbaite's colourless variety, which suits CHIP-8's one-bit screen.
**Verdelite** (Game Boy) and **Paraíba** (Game Boy Color) are planned.

<p align="center">
  <img src="docs/br8kout.png" alt="Achroite running Br8kout, with the debug view beside the screen" width="720">
</p>

## Why

I was replaying a lot of old Game Boy games recently, mostly the Pokémon ones, and I was itching for a new project
when it hit me: why not build an emulator myself? I'd used plenty of emulators over the years, but I'd never actually
stopped to ask how they work. So I started reading, and I realized pretty quickly that a Game Boy emulator was out of
reach for my current skills.

More reading led me to CHIP-8, a small virtual machine from 1977 that ran simple games on hobby computers. It has 35
instructions, sixteen one-byte registers and a 64 by 32 screen of one bit per pixel, which makes it tiny next to the
Game Boy. That made it the perfect warm-up: a whole machine small enough to finish, and a way to build the skills the
Game Boy is going to need.

## Achroite

- **Every CHIP-8 instruction** of the original COSMAC VIP interpreter, except `0NNN` (see Limits).
- **Runs in real time** in a Swing window: sixty frames a second, up to ten instructions a frame, with the keyboard
  mapped onto the VIP's hex keypad.
- **Sound:** a 441 Hz tone for as long as the sound timer runs.
- **Quirks:** where CHIP-8 interpreters disagree, a preset picks whose behaviour to follow: the original VIP (the
  default), SUPER-CHIP, or Octo.
- **A debugger:** pause, step one instruction at a time, and a live view of the registers, timers, stack and the memory
  at the program counter.
- **A terminal mode** that runs a fixed number of steps and prints the screen and registers, for checking a ROM's
  output exactly.
- **159 unit tests.**

## Requirements

Java 25 to run the jar. Java 25 and Maven 3.9 to build it.

## Build

```
mvn package
```

This runs the tests, then writes `elbaite-chip8/target/elbaite-chip8-1.0.0.jar`. A failing test means no jar.

## Run

The jar needs nothing but a Java 25 runtime. From the root of the repo:

**In a window:**

```
java -jar elbaite-chip8/target/elbaite-chip8-1.0.0.jar <rom-path>
```

The debug view beside the screen shows the registers, the timers, the call stack, and four lines of memory starting at
the line that holds the program counter, refreshed every frame.

**In the terminal:**

```
java -jar elbaite-chip8/target/elbaite-chip8-1.0.0.jar <rom-path> <steps>
```

This runs the given number of steps, ticking the timers after every tenth, then prints the screen and the registers.
It doesn't open a window.

<p align="center">
  <img src="docs/terminal.png" alt="The terminal mode printing the IBM logo and the registers" width="480">
</p>

**Quirks:**

```
java -jar elbaite-chip8/target/elbaite-chip8-1.0.0.jar --quirks vip|schip|octo <rom-path> [steps]
```

`vip` follows the original COSMAC VIP, `schip` follows SUPER-CHIP as modern emulators run it, and `octo` follows
Octo. The default is `vip`. The option has to come first.

**Version:**

```
java -jar elbaite-chip8/target/elbaite-chip8-1.0.0.jar --version
```

From the jar, this prints `Achroite 1.0.0`.

## Keys

The keyboard's `1234` / `QWER` / `ASDF` / `ZXCV` block is the keypad's `123C` / `456D` / `789E` / `A0BF`, the layout
the VIP's hex keypad had:

```
1 2 3 4          1 2 3 C
Q W E R          4 5 6 D
A S D F          7 8 9 E
Z X C V          A 0 B F
```

`P` pauses and resumes. While paused, `N` runs one instruction and prints the address it ran from, the opcode and the
registers to the terminal. Time stands still while paused: the timers don't tick and the tone stops.

## ROMs

No ROMs are included. What Achroite was tested against comes from two public collections:

- [Timendus's CHIP-8 test suite](https://github.com/Timendus/chip8-test-suite) (GPLv3).
- [John Earnest's CHIP-8 archive](https://github.com/JohnEarnest/chip8Archive), where the game in the screenshot,
  Br8kout by SharpenedSpoon, is released under CC0.

## What passes

From Timendus's suite, Achroite passes the IBM logo, the opcode test (22 checks), the flags test (47 checks), the
quirks test's CHIP-8 path, and the keypad test. The beep test has no pass or fail: it plays SOS in Morse code, and
Achroite plays it.

<p align="center">
  <img src="docs/quirks-test.png" alt="The quirks test's result screen: all six rows ticked for CHIP-8" width="720">
</p>

## Tests

```
mvn test
```

GitHub Actions runs the tests and builds the jar on every push to `main`.

## Limits

- **CHIP-8 only.** Achroite doesn't implement the SUPER-CHIP or XO-CHIP instruction sets, and has no high-resolution
  mode.
- **No `0NNN`.** It called a routine in the host computer's own machine code, which has no meaning outside that
  computer. A ROM that reaches it stops with an error rather than guessing.
- **Sound under WSL** needs ALSA's PulseAudio plugin (`libasound2-plugins`) and an `~/.asoundrc` that makes it the
  default. Without a sound device, the window runs silently.

## References

- Tobias V. Langhoff's guide, [Guide to making a CHIP-8 emulator](https://tobiasvl.github.io/blog/write-a-chip-8-emulator/).
- Laurence Scotford's disassembly of the original interpreter,
  [CHIP-8 on the COSMAC VIP](https://laurencescotford.net/chip-8-on-the-cosmac-vip-index/).

## Licence

MIT. See [`LICENSE`](LICENSE).
