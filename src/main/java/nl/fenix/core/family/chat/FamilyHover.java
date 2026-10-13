package nl.fenix.core.family.chat;

import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.placeholder.FamilyInfo;
import nl.fenix.core.family.placeholder.FamilyPlaceholders;
import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.entity.PlatformPlayer;
import com.gufli.kingdomcraft.api.events.PlayerChatEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * Makes {family_prefix} in KingdomCraft chat hoverable (family info) and clickable.
 *
 * KingdomCraft sends chat lines as plain text to the receivers of its PlayerChatEvent. We swap those
 * receivers for wrappers that send the same line as chat components, with a hover on the family prefix.
 * Everything else (cooldowns, social spy, console log, DiscordSRV) stays with KingdomCraft.
 */
public class FamilyHover {

    private final Plugin plugin;
    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final FamilyPlaceholders placeholders;
    private final FamilyInfo info;
    private final List<String> lines;
    private final String clickCommand;

    public FamilyHover(Plugin plugin, KingdomCraft kc, FamilyManager manager, FamilyPlaceholders placeholders,
                       FamilyInfo info, List<String> lines, String clickCommand) {
        this.plugin = plugin;
        this.kc = kc;
        this.manager = manager;
        this.placeholders = placeholders;
        this.info = info;
        this.lines = lines;
        this.clickCommand = clickCommand;
    }

    public void register() {
        // register on the first tick, after every plugin has enabled, so we run after all other chat
        // listeners (e.g. KingdomCraftPremium mutes) and see the final receivers
        Bukkit.getScheduler().runTask(plugin, () -> kc.getEventManager().addListener(PlayerChatEvent.class, this::onChat));
    }

    private void onChat(PlayerChatEvent event) {
        if (event.isCancelled() || event.getReceivers() == null || event.getFormat() == null
                || !event.getFormat().toLowerCase(Locale.ROOT).contains("{family_prefix}")) {
            return;
        }
        Family family = manager.getFamily(event.getPlayer().getUniqueId());
        if (family == null) {
            return;
        }
        String prefix = placeholders.prefixFor(family);
        if (prefix.trim().isEmpty()) {
            return;
        }

        String kingdom = info.kingdomOf(family);
        StringBuilder hoverText = new StringBuilder();
        for (String line : lines) {
            if (hoverText.length() > 0) {
                hoverText.append('\n');
            }
            hoverText.append(info.format(line, family, kingdom));
        }
        HoverEvent hover = hoverText.length() == 0 ? null
                : new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hoverText.toString()));
        ClickEvent click = clickCommand == null || clickCommand.trim().isEmpty() ? null
                : new ClickEvent(ClickEvent.Action.RUN_COMMAND, info.format(clickCommand, family, kingdom));
        if (hover == null && click == null) {
            return;
        }

        ReceiverList wrapped = new ReceiverList();
        for (PlatformPlayer receiver : event.getReceivers()) {
            wrapped.add(new HoverReceiver(receiver, prefix, hover, click));
        }
        event.setReceivers(wrapped);
    }

    /** Splits a finished chat line around the family prefix and puts the hover/click on the prefix part. */
    static BaseComponent[] build(String line, String prefix, HoverEvent hover, ClickEvent click) {
        int index = line.indexOf(prefix);
        if (index < 0) {
            return null;
        }
        String before = line.substring(0, index);
        String after = line.substring(index + prefix.length());

        BaseComponent[] first = TextComponent.fromLegacyText(before);
        // separate components lose the legacy color state, so carry the active colors over
        BaseComponent[] middle = TextComponent.fromLegacyText(org.bukkit.ChatColor.getLastColors(before) + prefix);
        BaseComponent[] last = TextComponent.fromLegacyText(org.bukkit.ChatColor.getLastColors(before + prefix) + after);
        for (BaseComponent c : middle) {
            if (hover != null) {
                c.setHoverEvent(hover);
            }
            if (click != null) {
                c.setClickEvent(click);
            }
        }

        BaseComponent[] result = new BaseComponent[first.length + middle.length + last.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(middle, 0, result, first.length, middle.length);
        System.arraycopy(last, 0, result, first.length + middle.length, last.length);
        return result;
    }
}
