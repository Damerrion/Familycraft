package com.familycraft.placeholder;

import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.entity.PlatformPlayer;
import com.gufli.kingdomcraft.api.events.PlayerLoadedEvent;
import com.gufli.kingdomcraft.api.events.UserJoinKingdomEvent;
import com.gufli.kingdomcraft.api.events.UserLeaveKingdomEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * KingdomCraft hardcodes the display name as {kingdom_prefix} name {kingdom_suffix}. Chat plugins that use
 * the display name (Essentials, ...) would never show the family tag, so we rebuild it with our own format
 * right after KingdomCraft has set it.
 */
public class DisplayNames {

    private final Plugin plugin;
    private final KingdomCraft kc;
    private final String format;

    public DisplayNames(Plugin plugin, KingdomCraft kc, String format) {
        this.plugin = plugin;
        this.kc = kc;
        this.format = format;
    }

    public void register() {
        // run a tick later so we always overwrite the display name KingdomCraft sets for these events
        kc.getEventManager().addListener(PlayerLoadedEvent.class, e -> updateLater(e.getPlayer().getUniqueId()));
        kc.getEventManager().addListener(UserJoinKingdomEvent.class, e -> updateLater(e.getUser().getUniqueId()));
        kc.getEventManager().addListener(UserLeaveKingdomEvent.class, e -> updateLater(e.getUser().getUniqueId()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateLater(player.getUniqueId());
        }
    }

    public void updateLater(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> update(uuid));
    }

    public void update(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlatformPlayer pp = kc.getPlayer(uuid);
        if (player == null || pp == null || pp.getUser() == null) {
            return;
        }
        String text = kc.colorify(kc.getPlaceholderManager().handle(pp, format));
        // an empty {family_prefix} or {kingdom_prefix} leaves double spaces behind
        text = text.replaceAll(" {2,}", " ").trim();
        player.setDisplayName(text.replace("%player%", player.getName()));
    }
}
