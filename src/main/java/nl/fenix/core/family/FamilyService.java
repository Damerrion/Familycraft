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

import java.util.List;
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
    private final CrestBans crestBans;

    public FamilyService(FamilyModule module, KingdomCraft kc, FamilyManager manager, FamilyStorage storage,
                         DisplayNames displayNames, FamilyInfo info) {
        this.module = module;
        this.kc = kc;
        this.manager = manager;
        this.storage = storage;
        this.displayNames = displayNames;
        this.info = info;
        this.crestBans = new CrestBans(module);
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

    /**
     * Admin mode (/family force, staff met family.admin) of dev mode aan: alles mogen wat een hoofd mag in elke
     * familie. Zonder admin mode is ook een op/staflid gewoon een speler: een hoofd bewerkt alleen zijn eigen familie.
     * De console geldt altijd als admin.
     */
    public boolean isAdmin(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return true;
        }
        return isAdminMode(p) || isDev(p);
    }

    /** Spelers met admin mode aan (/family force). Alleen in het geheugen. */
    private final java.util.Set<UUID> admins = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public boolean isAdminMode(Player player) {
        if (!admins.contains(player.getUniqueId())) {
            return false;
        }
        if (!player.hasPermission("family.admin")) {
            admins.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void setAdminMode(Player player, boolean on) {
        if (on) {
            admins.add(player.getUniqueId());
        } else {
            admins.remove(player.getUniqueId());
        }
    }

    /**
     * Rechten per functie (LuckPerms): {@code family.<functie>}. Admin/dev mode slaat de controle over.
     */
    public void require(CommandSender sender, String permission) throws FamilyException {
        if (!sender.hasPermission(permission) && !isAdmin(sender)) {
            throw new FamilyException("no-permission");
        }
    }

    // ------------------------------------------------------------------ dev mode

    /** Spelers met dev mode aan. Alleen in het geheugen: na een herstart staat het uit. */
    private final java.util.Set<UUID> devs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Dev mode aan? Vervalt meteen als de speler family.dev kwijt is. */
    public boolean isDev(Player player) {
        if (!devs.contains(player.getUniqueId())) {
            return false;
        }
        if (!player.hasPermission("family.dev")) {
            devs.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void setDev(Player player, boolean on) {
        if (on) {
            devs.add(player.getUniqueId());
        } else {
            devs.remove(player.getUniqueId());
        }
    }

    /** Hoofd van deze familie (alleen de eigen familie), of admin/dev mode. */
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
        require(actor, "family.rank");
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
        require(actor, "family.tree");
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.link(family, actor.getUniqueId(), bypass, relation, a, b);
        save();
        send(actor, "tree-linked-" + relation.name().toLowerCase(java.util.Locale.ROOT),
                "%a%", nameOf(a), "%b%", nameOf(b));
    }

    public void unlink(Player actor, Family family, UUID a, UUID b) throws FamilyException {
        require(actor, "family.tree");
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.unlink(family, actor.getUniqueId(), bypass, a, b);
        save();
        send(actor, "tree-unlinked", "%a%", nameOf(a), "%b%", nameOf(b));
    }

    public void unlinkSelf(Player actor, UUID other) throws FamilyException {
        require(actor, "family.tree");
        manager.unlinkSelf(actor.getUniqueId(), other);
        save();
        send(actor, "tree-unlinked", "%a%", actor.getName(), "%b%", nameOf(other));
    }

    public void setCrest(Player actor, Family family, ItemStack banner) throws FamilyException {
        require(actor, "family.crest");
        if (!GuiItems.isBanner(banner)) {
            throw new FamilyException("crest-no-banner");
        }
        if (crestBans.isBanned(banner)) {
            throw new FamilyException("crest-banned");
        }
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        manager.setCrest(family, actor.getUniqueId(), bypass, GuiItems.encodeCrest(banner));
        save();
        send(actor, "crest-set", "%name%", family.getName());
    }

    // ------------------------------------------------------------------ verboden wapens (admin)

    /** Alleen in admin mode (of dev mode / console). */
    public void requireAdmin(CommandSender sender) throws FamilyException {
        if (!isAdmin(sender)) {
            throw new FamilyException(sender.hasPermission("family.admin") ? "admin-required" : "no-permission");
        }
    }

    /** /family crest ban: de banner in je hand verbieden; families met dit wapen krijgen weer de standaard. */
    public void banCrest(Player actor, ItemStack banner) throws FamilyException {
        requireAdmin(actor);
        String fingerprint = bannerFingerprint(banner);
        if (!crestBans.add(fingerprint)) {
            throw new FamilyException("crest-ban-exists");
        }
        int reset = 0;
        for (Family family : manager.getFamilies()) {
            if (crestBans.isBannedCrest(family.getCrest())) {
                family.setCrest(null);
                reset++;
                notifyHead(family, "crest-reset-target");
            }
        }
        if (reset > 0) {
            save();
        }
        send(actor, "crest-ban-added", "%count%", String.valueOf(reset));
    }

    public void unbanCrest(Player actor, ItemStack banner) throws FamilyException {
        requireAdmin(actor);
        if (!crestBans.remove(bannerFingerprint(banner))) {
            throw new FamilyException("crest-ban-missing");
        }
        send(actor, "crest-ban-removed");
    }

    private static String bannerFingerprint(ItemStack banner) throws FamilyException {
        if (!GuiItems.isBanner(banner)) {
            throw new FamilyException("crest-no-banner");
        }
        String fingerprint = CrestBans.fingerprint(banner);
        if (fingerprint == null) {
            throw new FamilyException("crest-ban-empty");
        }
        return fingerprint;
    }

    /** /family crest reset &lt;tag&gt;: wapen terug naar de standaard-banner. */
    public void resetCrest(CommandSender actor, Family family) throws FamilyException {
        requireAdmin(actor);
        family.setCrest(null);
        save();
        send(actor, "crest-reset", "%name%", family.getName());
        notifyHead(family, "crest-reset-target");
    }

    private void notifyHead(Family family, String key) {
        Player head = Bukkit.getPlayer(family.getOwner());
        if (head != null) {
            send(head, key, "%name%", family.getName());
        }
    }

    // ------------------------------------------------------------------ archief (admin)

    /** Familie uit het archief terugzetten (zie {@link FamilyManager#unarchive}). */
    public void unarchive(CommandSender actor, String id) throws FamilyException {
        requireAdmin(actor);
        FamilyManager.Restored restored = manager.unarchive(id);
        Family family = restored.getFamily();
        save();
        // ook wie bij het terugdraaien van een fusie uit de familie ging
        Bukkit.getOnlinePlayers().forEach(p -> refresh(p.getUniqueId()));
        send(actor, "archive-restored", "%name%", family.getName(), "%tag%", family.getTag(),
                "%count%", String.valueOf(family.getMembers().size()));
        if (!restored.getSkipped().isEmpty()) {
            List<String> names = new java.util.ArrayList<>();
            restored.getSkipped().forEach(u -> names.add(nameOf(u)));
            send(actor, "archive-skipped", "%players%", String.join(", ", names));
        }
        UUID except = actor instanceof Player p ? p.getUniqueId() : null;
        broadcast(family, except, "archive-restored-target", "%name%", family.getName());
    }

    public void deleteArchived(CommandSender actor, String id) throws FamilyException {
        requireAdmin(actor);
        nl.fenix.core.family.model.ArchivedFamily a = manager.getArchived(id);
        manager.deleteArchived(id);
        save();
        send(actor, "archive-deleted", "%name%", a.getName(), "%tag%", a.getTag());
    }

    /** Reden in leesbare tekst (gui.archive-reason-&lt;reden&gt;). */
    public String archiveReason(String reason) {
        return raw("gui.archive-reason-" + reason, reason);
    }

    public static String formatDate(java.time.Instant instant) {
        return java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                .withZone(java.time.ZoneId.systemDefault()).format(instant);
    }

    public void rename(Player actor, Family family, String rawName, String rawTag) throws FamilyException {
        require(actor, "family.rename");
        boolean bypass = !family.isOwner(actor.getUniqueId()) && isAdmin(actor);
        String oldName = family.getName();
        manager.rename(family, actor.getUniqueId(), bypass, rawName, rawTag);
        save();
        refreshAll(family);
        broadcast(family, null, "renamed", "%old%", oldName, "%name%", family.getName(), "%tag%", family.getTag());
    }

    // ------------------------------------------------------------------ verloving en trouwerij

    /** /enlope &lt;speler&gt;: iemand ten huwelijk vragen. */
    public void engage(Player from, UUID to) throws FamilyException {
        require(from, "family.marriage");
        manager.requestEngagement(from.getUniqueId(), to);
        send(from, "engage-sent", "%player%", nameOf(to));
        if (manager.isFake(to) && isDev(from)) {
            // dev mode: een nep-speler zegt meteen ja
            FamilyManager.Engagement e = manager.acceptEngagement(to);
            save();
            send(from, "dev-engaged", "%groom%", nameOf(e.getGroom()), "%bride%", nameOf(e.getBride()));
            openWeddingForCouple(e);
            return;
        }
        Player target = Bukkit.getPlayer(to);
        if (target != null) {
            sendWithButtons(target, "engage-request", new String[]{"%player%", from.getName()},
                    "engage-button-accept", "/enlope accept", "engage-button-deny", "/enlope deny");
        }
    }

    public void acceptEngagement(Player player) throws FamilyException {
        FamilyManager.Engagement e = manager.acceptEngagement(player.getUniqueId());
        save();
        String[] repl = {"%groom%", nameOf(e.getGroom()), "%bride%", nameOf(e.getBride())};
        for (UUID uuid : new UUID[]{e.getGroom(), e.getBride()}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                sendWithButtons(p, "engage-accepted", repl, "wedding-button-open", "/family marriage");
            }
        }
        notifyBrideHead(e);
        openWeddingForCouple(e);
    }

    /** Trouwmenu openen bij de bruidegom en de bruid (wie online is). */
    public void openWeddingForCouple(FamilyManager.Engagement e) {
        for (UUID uuid : new UUID[]{e.getGroom(), e.getBride()}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                openWedding(p, e);
            }
        }
    }

    // ------------------------------------------------------------------ scheiding

    /** /divorce: vraag je partner om te scheiden; die moet bevestigen. */
    public void requestDivorce(Player player) throws FamilyException {
        require(player, "family.divorce");
        UUID partner = manager.requestDivorce(player.getUniqueId());
        send(player, "divorce-sent", "%player%", nameOf(partner));
        if (manager.isFake(partner) && isDev(player)) {
            // dev mode: een nep-partner gaat meteen akkoord
            manager.acceptDivorce(partner);
            divorced(player.getUniqueId(), partner);
            return;
        }
        Player target = Bukkit.getPlayer(partner);
        if (target != null) {
            sendWithButtons(target, "divorce-request", new String[]{"%player%", player.getName()},
                    "divorce-button-accept", "/divorce accept", "divorce-button-deny", "/divorce deny");
        }
    }

    public void acceptDivorce(Player player) throws FamilyException {
        UUID ex = manager.acceptDivorce(player.getUniqueId());
        divorced(player.getUniqueId(), ex);
    }

    public void denyDivorce(Player player) throws FamilyException {
        UUID from = manager.denyDivorce(player.getUniqueId());
        send(player, "divorce-denied");
        Player requester = Bukkit.getPlayer(from);
        if (requester != null) {
            send(requester, "divorce-denied-other", "%player%", player.getName());
        }
    }

    /** Admin: twee spelers direct scheiden. */
    public void forceDivorce(UUID a) throws FamilyException {
        UUID partner = manager.getGenealogy().partnerOf(a);
        if (partner == null) {
            throw new FamilyException("divorce-not-married");
        }
        manager.divorce(a, partner);
        divorced(a, partner);
    }

    private void divorced(UUID a, UUID b) {
        save();
        refresh(a);
        refresh(b);
        for (UUID uuid : new UUID[]{a, b}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                send(p, "divorce-done", "%a%", nameOf(a), "%b%", nameOf(b));
            }
        }
    }

    public void denyEngagement(Player player) throws FamilyException {
        UUID from = manager.denyEngagement(player.getUniqueId());
        send(player, "engage-denied");
        Player requester = Bukkit.getPlayer(from);
        if (requester != null) {
            send(requester, "engage-denied-other", "%player%", player.getName());
        }
    }

    public void cancelEngagement(Player player) throws FamilyException {
        cancelEngagement(player, manager.getEngagement(player.getUniqueId()));
    }

    /** Verloving verbreken (het paar zelf, of admin/dev). */
    public void cancelEngagement(Player player, FamilyManager.Engagement engagement) throws FamilyException {
        if (engagement == null) {
            throw new FamilyException("engage-not-engaged");
        }
        FamilyManager.Engagement e = manager.cancelEngagement(engagement.getGroom());
        save();
        for (UUID uuid : new UUID[]{e.getGroom(), e.getBride()}) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                send(p, "engage-cancelled", "%player%", player.getName());
            }
        }
    }

    /** Het hoofd van de familie van de bruid moet de trouwerij starten: laat het weten (klikbaar). */
    public void notifyBrideHead(FamilyManager.Engagement e) {
        Family bf = manager.getFamily(e.getBride());
        if (bf == null) {
            return;
        }
        Player head = Bukkit.getPlayer(bf.getOwner());
        if (head != null && !e.involves(head.getUniqueId())) {
            sendWithButtons(head, "wedding-head-notice",
                    new String[]{"%groom%", nameOf(e.getGroom()), "%bride%", nameOf(e.getBride())},
                    "wedding-button-open", "/family marriage " + nameOf(e.getBride()));
        }
    }

    /** Mag deze speler het trouwmenu van deze verloving zien? (paar, beide familiehoofden, admin) */
    public boolean canView(Player player, FamilyManager.Engagement e) {
        UUID uuid = player.getUniqueId();
        if (e.involves(uuid) || isAdmin(player)) {
            return true;
        }
        Family gf = manager.getFamily(e.getGroom());
        Family bf = manager.getFamily(e.getBride());
        return (gf != null && gf.isOwner(uuid)) || (bf != null && bf.isOwner(uuid));
    }

    public void openWedding(Player player, FamilyManager.Engagement e) {
        Bukkit.getScheduler().runTask(module.plugin(), () -> new nl.fenix.core.family.gui.WeddingGui(this, e).open(player));
    }

    public void swapRoles(Player actor, FamilyManager.Engagement e) throws FamilyException {
        if (!e.involves(actor.getUniqueId()) && !isAdmin(actor)) {
            throw new FamilyException("wedding-not-couple");
        }
        e.swapRoles();
        save();
    }

    public void toggleChooser(Player actor, FamilyManager.Engagement e) throws FamilyException {
        if (!e.involves(actor.getUniqueId()) && !isAdmin(actor)) {
            throw new FamilyException("wedding-not-couple");
        }
        e.setGroomChooses(!e.isGroomChooses());
        if (!manager.isAllowed(e, e.getOption())) {
            e.setOption(FamilyManager.MarriageOption.BRANCH);
        }
        save();
    }

    public void chooseOption(Player actor, FamilyManager.Engagement e, FamilyManager.MarriageOption option)
            throws FamilyException {
        boolean bypass = isAdmin(actor) && !e.getChooser().equals(actor.getUniqueId());
        manager.chooseOption(bypass ? e.getChooser() : actor.getUniqueId(), bypass, option);
        save();
    }

    /** Het hoofd van de familie van de bruid start de trouwerij. */
    public void startWedding(Player actor, FamilyManager.Engagement e) throws FamilyException {
        Family bf = manager.getFamily(e.getBride());
        boolean bypass = isAdmin(actor) && (bf == null || !bf.isOwner(actor.getUniqueId()));
        String groom = nameOf(e.getGroom());
        String bride = nameOf(e.getBride());
        List<String> before = new java.util.ArrayList<>();
        for (Family f : new Family[]{manager.getFamily(e.getGroom()), bf}) {
            if (f != null) {
                before.add(f.getName());
            }
        }
        FamilyManager.Wedding w = manager.startWedding(actor.getUniqueId(), bypass, e);
        save();
        Family family = w.getFamily();
        refreshAll(family);
        w.getNewHeads().forEach((f, head) -> {
            refresh(head);
            notifyNewHead(f, head);
        });
        String outcome = message("wedding-outcome-" + (w.getOption() == null
                ? w.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                : w.getOption().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')),
                "%name%", family.getName(), "%tag%", family.getTag(), "%lines%", String.join(" & ", before));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (family.isMember(online.getUniqueId()) || manager.getFamily(online.getUniqueId()) != null
                    && before.contains(manager.getFamily(online.getUniqueId()).getName())) {
                send(online, "wedding-done", "%groom%", groom, "%bride%", bride, "%outcome%", outcome);
            }
        }
        if (family.needsRename()) {
            promptRename(Bukkit.getPlayer(family.getOwner()), family);
        }
    }

    /** Admin: direct een aftakking maken zonder verloving/akkoord (bv. tijdens een event). */
    public void forceMarriage(UUID groom, UUID bride) throws FamilyException {
        FamilyManager.Marriage m = manager.marry(groom, bride);
        Family branch = m.getBranch();
        save();
        refreshAll(branch);
        refresh(m.getNewGroomFamilyHead());
        refresh(m.getNewBrideFamilyHead());
        notifyNewHead(m.getGroomFamily(), m.getNewGroomFamilyHead());
        notifyNewHead(m.getBrideFamily(), m.getNewBrideFamilyHead());
        String outcome = message("wedding-outcome-branch", "%name%", branch.getName(), "%tag%", branch.getTag(),
                "%lines%", m.getGroomFamily().getName() + " & " + m.getBrideFamily().getName());
        broadcast(branch, null, "wedding-done", "%groom%", nameOf(groom), "%bride%", nameOf(bride), "%outcome%", outcome);
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
