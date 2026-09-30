package dev.noahpn.elbaite.chip8;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * The window's Quirks menu: the three presets, then the six {@link Quirks} as checkboxes, named and
 * ordered as the quirks test's rows, and ticked when on.
 *
 * <p>A preset is a starting point. Picking one ticks its quirks, and each quirk can then be
 * switched on its own. The preset the ticks match exactly is marked, and a combination that matches
 * none marks none. Each quirk's tooltip says what it does when ticked.
 *
 * <p>Every change the user makes is reported, once, to the listener the menu is built with, as the
 * quirks now ticked. Picking the preset already in effect is no change and reports nothing. Nothing
 * but the user changes the menu.
 */
public final class QuirksMenu extends JMenu {

    private final Consumer<Quirks> onChange;
    private final ButtonGroup presets = new ButtonGroup();
    private final JRadioButtonMenuItem vip;
    private final JRadioButtonMenuItem superChip;
    private final JRadioButtonMenuItem octo;
    private final JCheckBoxMenuItem vfReset;
    private final JCheckBoxMenuItem memory;
    private final JCheckBoxMenuItem displayWait;
    private final JCheckBoxMenuItem clipping;
    private final JCheckBoxMenuItem shifting;
    private final JCheckBoxMenuItem jumping;

    private Quirks quirks;

    /**
     * Creates the menu with the given quirks ticked, and the preset they match marked, if any.
     *
     * @param quirks   the quirks to tick at first, not {@code null}
     * @param onChange called with the quirks now ticked each time the user changes them, not
     *                 {@code null}
     */
    public QuirksMenu(Quirks quirks, Consumer<Quirks> onChange) {
        super("Quirks");
        this.onChange = onChange;
        setMnemonic(KeyEvent.VK_Q);

        vip = addPreset("COSMAC VIP", KeyEvent.VK_V, Quirks.VIP);
        superChip = addPreset("SUPER-CHIP", KeyEvent.VK_S, Quirks.SUPER_CHIP);
        octo = addPreset("Octo", KeyEvent.VK_O, Quirks.OCTO);
        addSeparator();
        vfReset = addQuirk("vF reset", KeyEvent.VK_R, "8XY1, 8XY2 and 8XY3 set VF to zero");
        memory = addQuirk("Memory", KeyEvent.VK_M, "FX55 and FX65 leave I at I + X + 1");
        displayWait = addQuirk("Display wait", KeyEvent.VK_D,
            "A draw ends its frame: at most 60 sprites a second");
        clipping = addQuirk("Clipping", KeyEvent.VK_C,
            "The part of a sprite past an edge is dropped; when off, it wraps to the far edge");
        shifting = addQuirk("Shifting", KeyEvent.VK_H,
            "8XY6 and 8XYE shift VX in place and ignore VY");
        jumping = addQuirk("Jumping", KeyEvent.VK_J, "BNNN adds VX instead of V0");

        tick(quirks);
    }

    private JRadioButtonMenuItem addPreset(String name, int mnemonic, Quirks preset) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(name);
        item.setMnemonic(mnemonic);
        item.addActionListener(_ -> pick(preset));
        presets.add(item);
        add(item);
        return item;
    }

    private JCheckBoxMenuItem addQuirk(String name, int mnemonic, String description) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
        item.setMnemonic(mnemonic);
        item.setToolTipText(description);
        item.addActionListener(_ -> pick(ticked()));
        add(item);
        return item;
    }

    // The user picked a preset or switched a quirk: show the result and report it, unless it is
    // what was already in effect.
    private void pick(Quirks picked) {
        if (picked.equals(quirks)) {
            return;
        }

        tick(picked);
        onChange.accept(picked);
    }

    // The quirks the checkboxes tick. A checkbox has already switched when its click reports.
    private Quirks ticked() {
        return new Quirks(vfReset.isSelected(), memory.isSelected(), displayWait.isSelected(),
            clipping.isSelected(), shifting.isSelected(), jumping.isSelected());
    }

    // Ticks the given quirks and marks the preset they match. Setting an item from code is not a
    // click, so nothing here reports.
    private void tick(Quirks shown) {
        quirks = shown;
        vfReset.setSelected(shown.vfReset());
        memory.setSelected(shown.memory());
        displayWait.setSelected(shown.displayWait());
        clipping.setSelected(shown.clipping());
        shifting.setSelected(shown.shifting());
        jumping.setSelected(shown.jumping());

        // A button group ignores being told to unmark its choice; it has to be cleared.
        presets.clearSelection();
        vip.setSelected(shown.equals(Quirks.VIP));
        superChip.setSelected(shown.equals(Quirks.SUPER_CHIP));
        octo.setSelected(shown.equals(Quirks.OCTO));
    }
}
