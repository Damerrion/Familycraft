package nl.fenix.core.family;

import com.gufli.kingdomcraft.api.KingdomCraft;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.fenix.core.family.gui.GuiItems;
import nl.fenix.core.family.gui.RenameGui;
import nl.fenix.core.family.gui.Text;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import nl.fenix.core.family.placeholder.DisplayNames;
import nl.fenix.core.family.placeholder.FamilyInfo;
import nl.fenix.core.family.storage.FamilyStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Acties die zowel via /family als via de GUI's kunnen (rang, stamboom, familiewapen, huwelijk, hernoemen),
 * plus opslaan, berichten en display names bijwerken. Alles op de main thread.
 */
public final class FamilyService {

    private final FamilyModule module;
    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final FamilyStorage storage;
    private final DisplayNames displayNames;
    private final FamilyInfo info;

    public FamilyService(FamilyModule module, KingdomCraft kc, FamilyManager manager, FamilyStorage storage,
                         DisplayNames displayNames, FamilyInfo info) {
        this.module = module;
        this.kc = kc;
        this.manager = manager;
        this.storage = storage;
        this.displayNames = displayNames;
        this.info = info;
    }

    public FamilyModule module() {
        return module;
    }

    public FamilyManager manager() {
        return manager;
    }

    public FamilyInfo info() {
        return info;
    }

    public FileConfiguration config() {
        return module.getConfig();
    }

    // ------------------------------------------------------------------ opslaan / bijwerken

    public void save() {
        storage.save(manager);
    }

    public void refresh(UUID player) {
        if (displayNames != null && player != null) {
            displayNames.update(player);
        }
    }

    public void refreshAll(Family family) {
        for (UUID member : family.getMembers().keySet()) {
            refresh(member);
        }
    }

    // ------------------------------------------------------------------ berichten

    public void send(CommandSender to, String key, String... replacements) {
        to.sendMessage(color(config().getString("messages.prefix", "")) + message(key, replacements));
    }

    public String message(String key, String... replacements) {
        String text = config().getString("messages." + key);
        if (text == null) {
            text = key;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return color(text);
    }

    /** Ruwe (niet gekleurde) tekst uit family.yml, voor GUI-items. */
    public String raw(String path, String def) {
        String value = config().getString(path);
        return value == null ? def : value;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Bericht met een klikbare knop erachter (bv. [Accepteren]). */
    public void sendWithButtons(Player to, String key, String[] replacements, String... buttonKeyAndCommand) {
        Component line = Text.legacy(config().getString("messages.prefix", "") + message(key, replacements));
        for (int i = 0; i + 1 < buttonKeyAndCommand.length; i += 2) {
            line = line.append(Component.space()).append(Text.legacy(message(buttonKeyAndCommand[i]))
                    .clickEvent(ClickEvent.runCommand(buttonKeyAndCommand[i + 1])));
        }
        to.sendMessage(line);
    }

    public String rankName(FamilyRank rank) {
        String name = config().getString("ranks." + rank.id() + ".name");
        return name == null ? rank.defaultName() : name;
    }

    /** Kleurcode voor de naam van een lid met deze rang (gui.rank-colors). */
    public String rankColor(FamilyRank rank) {
        String color = config().getString("gui.rank-colors." + rank.id());
        return color == null ? "&f" : color;
    }

    /** Rang op id, Nederlandse naam, ingestelde naam of oude Engelse naam. */
    public FamilyRank parseRank(String raw) {
        String key = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}]", "");
        for (FamilyRank rank : FamilyRank.values()) {
            if (rankName(rank).toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}]", "").equals(key)) {
                return rank;
            }
        }
        return FamilyRank.parse(raw);
    }

    public void broadcast(Family family, UUID except, String key, String... replacements) {
        for (UUID member : family.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && !member.equals(except)) {
                send(online, key, replacements);
            }
        }
    }

    // ------------------------------------------------------------------ rechten / spelers zoeken

    public boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("family.admin");
    }

    /** Hoofd van de familie, of admin. */
    public boolean canEdit(Player player, Family family) {
        return family.isOwner(player.getUniqueId()) || isAdmin(player);
    }

    /** Lid van de familie op naam (hoofdletterongevoelig) of UUID; null als het geen lid is. */
    public UUID findMember(Family family, String arg) {
        UUID uuid = parseUuid(arg);
        if (uuid != null) {
            return family.isMember(uuid) ? uuid : null;
        }
        for (Map.Entry<UUID, String> e : family.getMembers().entrySet()) {
            if (e.getValue().equalsIgnoreCase(arg)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Speler op UUID, online naam, of naam van een lid van een familie; null als onbekend. */
    public UUID findPlayer(String arg) {
        UUID uuid = parseUuid(arg);
        if (uuid != null) {
            return uuid;
        }
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Family family : manager.getFamilies()) {
            UUID member = findMember(family, arg);
            if (member != null) {
                return member;
            }
        }
        return null;
    }

    private static UUID parseUuid(String arg) {
        try {
            return arg.length() == 36 ? UUID.fromString(arg) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String nameOf(UUID player) {
        Family family = manager.getFamily(player);
        if (family != null && family.getMembers().get(player) != null) {
            return family.getMembers().get(player);
        }
        return manager.getGenealogy().name(player);
    }

    // ------------------------------------------------------------------ acties

    public void setRank(Player actor, Family family, UUID target, FamilyRank rank) throws FamilyException {
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        UUID oldHead = family.getOwner();
        manager.setRank(family, actor.getUniqueId(), bypass, target, rank);
        save();
        refresh(target);
        refresh(oldHead);
        String name = nameOf(target);
        send(actor, "rank-given", "%player%", name, "%rank%", rankName(rank));
        Player online = Bukkit.getPlayer(target);
        if (online != null && !online.equals(actor)) {
            send(online, "rank-received", "%rank%", rankName(rank), "%name%", family.getName());
        }
    }

    public void link(Player actor, Family family, Genealogy.Relation relation, UUID a, UUID b) throws FamilyException {
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.link(family, actor.getUniqueId(), bypass, relation, a, b);
        save();
        send(actor, "tree-linked-" + relation.name().toLowerCase(java.util.Locale.ROOT),
                "%a%", nameOf(a), "%b%", nameOf(b));
    }

    public void unlink(Player actor, Family family, UUID a, UUID b) throws FamilyException {
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.unlink(family, actor.getUniqueId(), bypass, a, b);
        save();
        send(actor, "tree-unlinked", "%a%", nameOf(a), "%b%", nameOf(b));
    }

    public void setCrest(Player actor, Family family, ItemStack banner) throws FamilyException {
        if (!GuiItems.isBanner(banner)) {
            throw new FamilyException("crest-no-banner");
        }
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.setCrest(family, actor.getUniqueId(), bypass, GuiItems.encodeCrest(banner));
        save();
        send(actor, "crest-set", "%name%", family.getName());
    }

    public void rename(Player actor, Family family, String rawName, String rawTag) throws FamilyException {
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        String oldName = family.getName();
        manager.rename(family, actor.getUniqueId(), bypass, rawName, rawTag);
        save();
        refreshAll(family);
        broadcast(family, null, "renamed", "%old%", oldName, "%name%", family.getName(), "%tag%", family.getTag());
    }

    public void propose(Player actor, UUID groom, UUID bride) throws FamilyException {
        boolean bypass = isAdmin(actor) && (manager.getFamily(groom) == null || !manager.getFamily(groom).isOwner(actor.getUniqueId()));
        FamilyManager.Proposal p = manager.propose(actor.getUniqueId(), bypass, groom, bride);
        String[] repl = {"%groom%", nameOf(groom), "%bride%", nameOf(bride), "%groomfamily%", p.getGroomFamily().getName(),
                "%bridefamily%", p.getBrideFamily().getName()};
        send(actor, "marriage-proposed", repl);
        Player head = Bukkit.getPlayer(p.getBrideFamily().getOwner());
        if (head != null) {
            sendWithButtons(head, "marriage-proposal", repl,
                    "marriage-button-accept", "/family marriage accept", "marriage-button-deny", "/family marriage deny");
        }
    }

    public void denyMarriage(Player actor) throws FamilyException {
        FamilyManager.Proposal p = manager.deny(actor.getUniqueId());
        send(actor, "marriage-denied");
        Player head = Bukkit.getPlayer(p.getGroomFamily().getOwner());
        if (head != null) {
            send(head, "marriage-denied-groom", "%bridefamily%", p.getBrideFamily().getName());
        }
    }

    public void acceptMarriage(Player actor) throws FamilyException {
        completeMarriage(manager.acceptMarriage(actor.getUniqueId()));
    }

    /** Admin: direct trouwen zonder akkoord (bv. tijdens een event). */
    public void forceMarriage(UUID groom, UUID bride) throws FamilyException {
        completeMarriage(manager.marry(groom, bride));
    }

    private void completeMarriage(FamilyManager.Marriage m) {
        Family branch = m.getBranch();
        save();
        refreshAll(branch);
        refresh(m.getNewGroomFamilyHead());
        refresh(m.getNewBrideFamilyHead());
        String[] repl = {"%name%", branch.getName(), "%tag%", branch.getTag(),
                "%groomfamily%", m.getGroomFamily().getName(), "%bridefamily%", m.getBrideFamily().getName()};
        broadcast(branch, null, "marriage-done", repl);
        broadcast(m.getGroomFamily(), null, "marriage-done-family", repl);
        broadcast(m.getBrideFamily(), null, "marriage-done-family", repl);
        notifyNewHead(m.getGroomFamily(), m.getNewGroomFamilyHead());
        notifyNewHead(m.getBrideFamily(), m.getNewBrideFamilyHead());
        if (branch.needsRename()) {
            promptRename(Bukkit.getPlayer(branch.getOwner()), branch);
        }
    }

    private void notifyNewHead(Family family, UUID newHead) {
        if (newHead != null) {
            broadcast(family, null, "new-head", "%player%", nameOf(newHead), "%name%", family.getName());
        }
    }

    /** "Popup": het hernoem-menu voor het hoofd van een aftakking die een nieuwe naam moet kiezen. */
    public void promptRename(Player head, Family family) {
        if (head == null || !family.needsRename()) {
            return;
        }
        send(head, "rename-needed", "%name%", family.getName());
        Bukkit.getScheduler().runTask(module.plugin(), () -> new RenameGui(this, family).open(head));
    }

    public KingdomCraft kingdomCraft() {
        return kc;
    }
}
