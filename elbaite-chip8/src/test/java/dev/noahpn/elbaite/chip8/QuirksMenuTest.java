package dev.noahpn.elbaite.chip8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuirksMenuTest {

    private List<Quirks> reported;
    private QuirksMenu menu;

    @BeforeEach
    void setUp() {
        reported = new ArrayList<>();
        menu = new QuirksMenu(Quirks.VIP, reported::add);
    }

    private JMenuItem item(String name) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null && item.getText().equals(name)) {
                return item;
            }
        }
        return fail("No menu item named " + name);
    }

    private void click(String name) {
        item(name).doClick(0);
    }

    // The quirks the checkboxes tick, each found by its name.
    private Quirks ticked() {
        return new Quirks(item("vF reset").isSelected(), item("Memory").isSelected(),
            item("Display wait").isSelected(), item("Clipping").isSelected(),
            item("Shifting").isSelected(), item("Jumping").isSelected());
    }

    // The name of the preset marked, or null for none.
    private String marked() {
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (menu.getItem(i) instanceof JRadioButtonMenuItem preset && preset.isSelected()) {
                return preset.getText();
            }
        }
        return null;
    }

    @Test
    void menuListsPresetsThenQuirks() {
        // getItem gives null for the separator, written here as "-"
        List<String> names = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            names.add(item != null ? item.getText() : "-");
        }

        assertEquals("Quirks", menu.getText());
        assertEquals(List.of("COSMAC VIP", "SUPER-CHIP", "Octo", "-", "vF reset", "Memory",
            "Display wait", "Clipping", "Shifting", "Jumping"), names);
    }

    @Test
    void menuShowsGivenPreset() {
        menu = new QuirksMenu(Quirks.OCTO, reported::add);

        assertEquals("Octo", marked());
        assertEquals(Quirks.OCTO, ticked());
    }

    @Test
    void menuShowsCustomQuirksWithoutPreset() {
        Quirks custom = new Quirks(true, true, true, true, true, false);

        menu = new QuirksMenu(custom, reported::add);

        assertNull(marked());
        assertEquals(custom, ticked());
    }

    @Test
    void pickingPresetTicksItsQuirks() {
        click("SUPER-CHIP");

        assertEquals(List.of(Quirks.SUPER_CHIP), reported);
        assertEquals("SUPER-CHIP", marked());
        assertEquals(Quirks.SUPER_CHIP, ticked());
    }

    @Test
    void pickingCurrentPresetReportsNothing() {
        click("COSMAC VIP");

        assertEquals(List.of(), reported);
        assertEquals("COSMAC VIP", marked());
    }

    @Test
    void switchingQuirkLeavesPreset() {
        Quirks vipWithShifting = new Quirks(true, true, true, true, true, false);

        click("Shifting");

        assertEquals(List.of(vipWithShifting), reported);
        assertNull(marked());
        assertEquals(vipWithShifting, ticked());
    }

    @Test
    void switchingQuirksIntoPresetMarksIt() {
        // The VIP and Octo differ in exactly these three
        click("vF reset");
        click("Display wait");
        click("Clipping");

        assertEquals(3, reported.size());
        assertEquals(Quirks.OCTO, reported.getLast());
        assertEquals("Octo", marked());
    }
}
