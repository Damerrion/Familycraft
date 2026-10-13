package com.familycraft.chat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.junit.Test;

import static org.junit.Assert.*;

public class FamilyHoverTest {

    private static final String PREFIX = "§8[§7TW§8] ";
    private final HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("info"));
    private final ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/family members TW");

    @Test
    public void hoverOnlyOnPrefixAndTextUnchanged() {
        String line = "§f§7[§cA§7damantium] " + PREFIX + "§r§fOverheerlijk§7: §f.";
        BaseComponent[] components = FamilyHover.build(line, PREFIX, hover, click);
        assertNotNull(components);

        StringBuilder all = new StringBuilder();
        StringBuilder hovered = new StringBuilder();
        for (BaseComponent c : components) {
            String text = c.toPlainText();
            all.append(text);
            if (c.getHoverEvent() != null) {
                assertSame(click, c.getClickEvent());
                hovered.append(text);
            } else {
                assertNull(c.getClickEvent());
            }
        }
        assertEquals("[Adamantium] [TW] Overheerlijk: .", all.toString());
        assertEquals("[TW] ", hovered.toString());
    }

    @Test
    public void textAfterPrefixKeepsPrefixColor() {
        // no color code right after the prefix: it must stay dark gray like in plain legacy chat
        BaseComponent[] components = FamilyHover.build("A " + PREFIX + "naam", PREFIX, hover, null);
        BaseComponent last = components[components.length - 1];
        assertEquals("naam", last.toPlainText());
        assertEquals(net.md_5.bungee.api.ChatColor.DARK_GRAY, last.getColor());
    }

    @Test
    public void noPrefixInLineFallsBack() {
        assertNull(FamilyHover.build("[Adamantium] Overheerlijk: hoi", PREFIX, hover, click));
    }
}
