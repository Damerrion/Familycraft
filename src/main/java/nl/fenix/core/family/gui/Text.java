package nl.fenix.core.family.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** &amp;-kleurcodes (zoals in family.yml) naar Adventure-componenten, zonder cursief. */
public final class Text {

    private Text() {
    }

    public static Component legacy(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text.replace('§', '&'));
    }

    public static Component item(String text) {
        return legacy(text).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lines(List<String> lines) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(item(line));
        }
        return result;
    }
}
