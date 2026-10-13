package nl.fenix.core.family.placeholder;

import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.storage.FamilyStorage;
import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.domain.Kingdom;
import com.gufli.kingdomcraft.api.domain.User;
import com.gufli.kingdomcraft.api.events.PlayerLoadedEvent;
import com.gufli.kingdomcraft.api.events.UserJoinKingdomEvent;
import com.gufli.kingdomcraft.api.events.UserLeaveKingdomEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The info shown by /family check and the chat hover. Keeps the owner's kingdom ("familie afstamming")
 * cached on the family so it can be shown synchronously, also while the owner is offline.
 */
public class FamilyInfo {

    private final FamilyModule plugin;
    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final FamilyStorage storage;

    public FamilyInfo(FamilyModule plugin, KingdomCraft kc, FamilyManager manager, FamilyStorage storage) {
        this.plugin = plugin;
        this.kc = kc;
        this.manager = manager;
        this.storage = storage;
    }

    public void register() {
        kc.getEventManager().addListener(PlayerLoadedEvent.class, e -> {
            User user = e.getPlayer().getUser();
            if (user != null) {
                updateKingdom(user.getUniqueId(), user.getKingdom());
            }
        });
        kc.getEventManager().addListener(UserJoinKingdomEvent.class, e -> updateKingdom(e.getUser().getUniqueId(), e.getUser().getKingdom()));
        kc.getEventManager().addListener(UserLeaveKingdomEvent.class, e -> updateKingdom(e.getUser().getUniqueId(), null));
    }

    /** Stores the kingdom of a family owner; no-op for players that do not own a family. */
    public void updateKingdom(UUID player, Kingdom kingdom) {
        Family family = manager.getFamily(player);
        if (family == null || !family.isOwner(player)) {
            return;
        }
        String name = kingdom == null ? null : kingdom.getName();
        if (!Objects.equals(name, family.getKingdomName())) {
            family.setKingdomName(name);
            Bukkit.getScheduler().runTask(plugin.plugin(), () -> storage.save(manager));
        }
    }

    /** Colored kingdom display, from the online owner or the cached kingdom; null if unknown. */
    public String kingdomOf(Family family) {
        User online = kc.getOnlineUser(family.getOwner());
        if (online != null) {
            updateKingdom(family.getOwner(), online.getKingdom());
            return display(online.getKingdom());
        }
        if (family.getKingdomName() == null) {
            return null;
        }
        Kingdom kingdom = kc.getKingdom(family.getKingdomName());
        return kingdom == null ? family.getKingdomName() : display(kingdom);
    }

    /** Like {@link #kingdomOf} but looks the offline owner up in the database when nothing is cached. */
    public void resolveKingdom(Family family, Consumer<String> callback) {
        String known = kingdomOf(family);
        if (known != null || kc.getOnlineUser(family.getOwner()) != null) {
            callback.accept(known);
            return;
        }
        kc.getUser(family.getOwner()).whenComplete((user, err) -> Bukkit.getScheduler().runTask(plugin.plugin(), () -> {
            Kingdom kingdom = user == null || err != null ? null : user.getKingdom();
            if (kingdom != null) {
                family.setKingdomName(kingdom.getName());
                storage.save(manager);
            }
            callback.accept(display(kingdom));
        }));
    }

    private String display(Kingdom kingdom) {
        if (kingdom == null) {
            return null;
        }
        String display = kingdom.getDisplay();
        return kc.colorify(display == null || display.isEmpty() ? kingdom.getName() : display);
    }

    /** Fills %name% %tag% %kingdom% %date% %owner% %members% in a config text and colors it. */
    public String format(String text, Family family, String kingdom) {
        String date = DateTimeFormatter.ofPattern(plugin.getConfig().getString("messages.date-format", "dd/MM/yyyy HH:mm:ss"))
                .withZone(ZoneId.systemDefault()).format(family.getCreatedAt());
        String noKingdom = plugin.getConfig().getString("messages.no-kingdom", "Geen");
        String owner = family.getOwnerName();
        return ChatColor.translateAlternateColorCodes('&', text
                .replace("%name%", family.getName())
                .replace("%tag%", family.getTag())
                .replace("%kingdom%", kingdom == null ? noKingdom : kingdom)
                .replace("%date%", date)
                .replace("%owner%", owner == null ? "?" : owner)
                .replace("%members%", String.valueOf(family.getMembers().size())));
    }
}
