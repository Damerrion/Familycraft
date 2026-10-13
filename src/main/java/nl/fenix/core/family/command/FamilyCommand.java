package nl.fenix.core.family.command;

import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.gui.EditorGui;
import nl.fenix.core.family.gui.OverviewGui;
import nl.fenix.core.family.gui.TreeGui;
import nl.fenix.core.family.economy.VaultEconomy;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import nl.fenix.core.family.placeholder.DisplayNames;
import nl.fenix.core.family.placeholder.FamilyInfo;
import nl.fenix.core.family.storage.FamilyStorage;
import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.domain.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class FamilyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "accept", "cancel", "create", "disband", "check", "invite", "kick", "leave", "members", "info", "help",
            "list", "editor", "rank", "tree", "marriage", "rename", "crest");

    private final FamilyModule plugin;
    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final FamilyStorage storage;
    private volatile VaultEconomy economy;
    private final DisplayNames displayNames;
    private final FamilyInfo info;
    private FamilyService service;

    /** owner -> time until which /family disband is confirmed */
    private final Map<UUID, Long> pendingDisband = new HashMap<>();

    public FamilyCommand(FamilyModule plugin, KingdomCraft kc, FamilyManager manager,
                         FamilyStorage storage, VaultEconomy economy, DisplayNames displayNames,
                         FamilyInfo info) {
        this.plugin = plugin;
        this.kc = kc;
        this.manager = manager;
        this.storage = storage;
        this.economy = economy;
        this.displayNames = displayNames;
        this.info = info;
    }

    public void setEconomy(VaultEconomy economy) {
        this.economy = economy;
    }

    public void setService(FamilyService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ dispatch

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("family.use")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (sub) {
                case "create":
                    create(player(sender), rest);
                    break;
                case "invite":
                    invite(player(sender), rest);
                    break;
                case "accept":
                    accept(player(sender));
                    break;
                case "cancel":
                    cancel(player(sender));
                    break;
                case "leave":
                    leave(player(sender));
                    break;
                case "kick":
                    kick(sender, rest);
                    break;
                case "disband":
                    disband(sender, rest);
                    break;
                case "check":
                case "info":
                    check(sender, rest);
                    break;
                case "members":
                    members(sender, rest);
                    break;
                case "list":
                case "overzicht":
                    list(player(sender), rest);
                    break;
                case "editor":
                    editor(player(sender), rest);
                    break;
                case "rank":
                    rank(player(sender), rest);
                    break;
                case "tree":
                case "stamboom":
                    tree(player(sender), rest);
                    break;
                case "marriage":
                case "huwelijk":
                    marriage(player(sender), rest);
                    break;
                case "rename":
                    rename(player(sender), rest);
                    break;
                case "crest":
                    service.setCrest(player(sender), editableFamily(player(sender), rest, 0),
                            player(sender).getInventory().getItemInMainHand());
                    break;
                case "help":
                    for (String line : plugin.getConfig().getStringList("messages.help")) {
                        sender.sendMessage(color(line));
                    }
                    // nieuwe commando's apart, zodat ze ook verschijnen met een oude family.yml
                    for (String line : plugin.getConfig().getStringList("messages.help-extra")) {
                        sender.sendMessage(color(line));
                    }
                    break;
                default:
                    send(sender, "unknown-subcommand");
            }
        } catch (FamilyException ex) {
            send(sender, ex.getKey(), "%max%", String.valueOf(max(ex.getKey())));
        }
        return true;
    }

    private int max(String key) {
        return key.equals("tag-invalid") ? manager.getMaxTagLength() : manager.getMaxNameLength();
    }

    // ------------------------------------------------------------------ subcommands

    private void create(Player p, String[] args) throws FamilyException {
        if (!p.hasPermission("family.create") && !p.hasPermission("family.admin")) {
            throw new FamilyException("no-permission");
        }
        if (args.length < 2) {
            throw new FamilyException("usage-create");
        }
        double cost = plugin.getConfig().getDouble("economy.create-cost", 0);
        boolean pays = cost > 0 && !p.hasPermission("family.admin");
        if (pays) {
            if (economy == null) {
                throw new FamilyException("economy-unavailable");
            }
            if (!economy.has(p, cost)) {
                throw new FamilyException("not-enough-money");
            }
        }

        Family family = manager.create(p.getUniqueId(), p.getName(), args[0], args[1]);
        if (pays && !economy.withdraw(p, cost)) {
            manager.disband(family);
            throw new FamilyException("not-enough-money");
        }
        User user = kc.getOnlineUser(p.getUniqueId());
        family.setKingdomName(user == null || user.getKingdom() == null ? null : user.getKingdom().getName());
        save();
        refresh(p.getUniqueId());
        send(p, "created", "%name%", family.getName(), "%tag%", family.getTag(),
                "%cost%", economy == null ? "" : economy.format(cost));
    }

    private void invite(Player p, String[] args) throws FamilyException {
        Family family = ownFamily(p);
        if (args.length < 1) {
            throw new FamilyException("usage-invite");
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            throw new FamilyException("player-not-found");
        }
        manager.invite(family, p.getUniqueId(), target.getUniqueId());
        send(p, "invite-sent", "%player%", target.getName());
        send(target, "invite-received", "%owner%", p.getName(), "%name%", family.getName(), "%tag%", family.getTag());
    }

    private void accept(Player p) throws FamilyException {
        Family family = manager.accept(p.getUniqueId(), p.getName());
        save();
        refresh(p.getUniqueId());
        send(p, "joined", "%name%", family.getName(), "%tag%", family.getTag());
        broadcast(family, p.getUniqueId(), "joined-broadcast", "%player%", p.getName());
    }

    /** Declines an invite, or cancels a pending disband / the owner's outgoing invites. */
    private void cancel(Player p) throws FamilyException {
        if (pendingDisband.remove(p.getUniqueId()) != null) {
            send(p, "disband-cancelled");
            return;
        }
        FamilyManager.Invite invite = manager.decline(p.getUniqueId());
        if (invite != null) {
            send(p, "invite-declined");
            Player inviter = Bukkit.getPlayer(invite.getInviter());
            if (inviter != null) {
                send(inviter, "invite-declined-owner", "%player%", p.getName());
            }
            return;
        }
        Family family = manager.getFamily(p.getUniqueId());
        if (family != null && family.isOwner(p.getUniqueId()) && manager.cancelInvitesOf(family) > 0) {
            send(p, "invites-cancelled");
            return;
        }
        throw new FamilyException("nothing-to-cancel");
    }

    private void leave(Player p) throws FamilyException {
        Family family = manager.leave(p.getUniqueId());
        save();
        refresh(p.getUniqueId());
        send(p, "left", "%name%", family.getName());
        broadcast(family, null, "left-broadcast", "%player%", p.getName());
    }

    private void kick(CommandSender sender, String[] args) throws FamilyException {
        boolean admin = sender.hasPermission("family.admin");
        Family family;
        String targetName;
        if (admin && args.length >= 2 && manager.getFamilyByTag(args[0]) != null) {
            family = manager.getFamilyByTag(args[0]);
            targetName = args[1];
        } else {
            family = ownFamily(player(sender));
            if (args.length < 1) {
                throw new FamilyException("usage-kick");
            }
            targetName = args[0];
        }
        UUID target = null;
        for (Map.Entry<UUID, String> e : family.getMembers().entrySet()) {
            if (e.getValue().equalsIgnoreCase(targetName)) {
                target = e.getKey();
            }
        }
        if (target == null) {
            throw new FamilyException("kick-not-member");
        }
        UUID actor = sender instanceof Player ? ((Player) sender).getUniqueId() : family.getOwner();
        String name = family.getMembers().get(target);
        manager.kick(family, actor, admin, target);
        save();
        refresh(target);
        send(sender, "kicked", "%player%", name);
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            send(kicked, "kicked-target", "%name%", family.getName());
        }
    }

    private void disband(CommandSender sender, String[] args) throws FamilyException {
        boolean admin = sender.hasPermission("family.admin");
        Family family;
        if (admin && args.length >= 1) {
            family = manager.getFamilyByTag(args[0]);
            if (family == null) {
                throw new FamilyException("family-not-found");
            }
        } else {
            Player p = player(sender);
            family = ownFamily(p);
            long now = System.currentTimeMillis();
            Long until = pendingDisband.get(p.getUniqueId());
            if (until == null || until < now) {
                long seconds = plugin.getConfig().getLong("settings.disband-confirm-seconds", 15);
                pendingDisband.put(p.getUniqueId(), now + seconds * 1000);
                send(p, "disband-confirm", "%seconds%", String.valueOf(seconds));
                return;
            }
            pendingDisband.remove(p.getUniqueId());
        }

        List<UUID> members = new ArrayList<>(family.getMembers().keySet());
        manager.disband(family);
        save();
        send(sender, "disbanded", "%name%", family.getName());
        for (UUID m : members) {
            refresh(m);
            Player online = Bukkit.getPlayer(m);
            if (online != null && online != sender) {
                send(online, "disbanded-target", "%name%", family.getName());
            }
        }
    }

    private void check(CommandSender sender, String[] args) throws FamilyException {
        Family family;
        if (args.length == 0) {
            family = manager.getFamily(player(sender).getUniqueId());
            if (family == null) {
                throw new FamilyException("usage-check");
            }
        } else {
            family = manager.getFamilyByTag(args[0]);
            if (family == null) {
                sender.sendMessage(message("family-not-found", "%tag%", args[0]));
                return;
            }
        }
        final Family f = family;
        info.resolveKingdom(f, kingdom -> {
            for (String key : new String[]{"info-border", "info-name", "info-tag", "info-kingdom", "info-created", "info-border"}) {
                sender.sendMessage(info.format(plugin.getConfig().getString("messages." + key, key), f, kingdom));
            }
        });
    }

    private void members(CommandSender sender, String[] args) throws FamilyException {
        Family family = args.length == 0 ? manager.getFamily(player(sender).getUniqueId()) : manager.getFamilyByTag(args[0]);
        if (family == null) {
            if (args.length == 0) {
                throw new FamilyException("not-in-family");
            }
            sender.sendMessage(message("family-not-found", "%tag%", args[0]));
            return;
        }
        sender.sendMessage(message("members-header", "%name%", family.getName(), "%tag%", family.getTag(),
                "%count%", String.valueOf(family.getMembers().size())));
        for (Map.Entry<UUID, String> e : family.getMembers().entrySet()) {
            String role = message("role-rank", "%rank%", service.rankName(family.getRank(e.getKey())));
            sender.sendMessage(message("members-entry", "%player%", e.getValue(), "%role%", role).trim());
        }
    }

    /** /family list [kingdom|all]: families van je eigen kingdom, een ander kingdom, of alle. */
    private void list(Player p, String[] args) throws FamilyException {
        if (args.length == 0) {
            String own = OverviewGui.kingdomOf(service, p);
            if (own.isEmpty()) {
                send(p, "list-no-kingdom");
            }
            new OverviewGui(service, p, own, 0).open(p);
            return;
        }
        String arg = String.join(" ", args);
        if (arg.equalsIgnoreCase("all") || arg.equalsIgnoreCase("alle")) {
            new OverviewGui(service, p, OverviewGui.ALL, 0).open(p);
            return;
        }
        for (com.gufli.kingdomcraft.api.domain.Kingdom kingdom : kc.getKingdoms()) {
            if (kingdom.getName().equalsIgnoreCase(arg)) {
                new OverviewGui(service, p, kingdom.getName(), 0).open(p);
                return;
            }
        }
        Family family = manager.getFamilyByTag(arg);
        if (family != null) {
            new EditorGui(service, family, p).open(p);
            return;
        }
        send(p, "list-unknown", "%arg%", arg);
    }

    /** /family editor [tag] */
    private void editor(Player p, String[] args) throws FamilyException {
        Family family = args.length > 0 ? manager.getFamilyByTag(args[0]) : manager.getFamily(p.getUniqueId());
        if (family == null) {
            throw new FamilyException(args.length > 0 ? "family-not-found" : "not-in-family");
        }
        new EditorGui(service, family, p).open(p);
    }

    /** /family rank give &lt;speler|uuid&gt; &lt;rang&gt; | /family rank list */
    private void rank(Player p, String[] args) throws FamilyException {
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            for (FamilyRank rank : FamilyRank.values()) {
                p.sendMessage(message("rank-list-entry", "%id%", rank.dutchId(), "%rank%", service.rankName(rank)));
            }
            return;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            throw new FamilyException("usage-rank");
        }
        FamilyRank rank = service.parseRank(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (rank == null) {
            throw new FamilyException("rank-unknown");
        }
        Family family = targetFamily(p, args[1]);
        service.setRank(p, family, memberOf(family, args[1]), rank);
    }

    /** /family tree [tag] | link &lt;parent|partner|sibling&gt; &lt;a&gt; &lt;b&gt; | unlink &lt;a&gt; &lt;b&gt; */
    private void tree(Player p, String[] args) throws FamilyException {
        if (args.length >= 4 && args[0].equalsIgnoreCase("link")) {
            Genealogy.Relation relation = Genealogy.Relation.parse(args[1]);
            if (relation == null) {
                throw new FamilyException("usage-tree");
            }
            Family family = targetFamily(p, args[2]);
            UUID a = memberOf(family, args[2]);
            UUID b = memberOf(family, args[3]);
            // "child" = omgekeerd: a is het kind van b
            boolean reversed = args[1].equalsIgnoreCase("child") || args[1].equalsIgnoreCase("kind");
            service.link(p, family, relation, reversed ? b : a, reversed ? a : b);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("unlink")) {
            Family family = targetFamily(p, args[1]);
            service.unlink(p, family, memberOf(family, args[1]), memberOf(family, args[2]));
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("link") || args[0].equalsIgnoreCase("unlink"))) {
            throw new FamilyException("usage-tree");
        }
        Family family = args.length > 0 ? manager.getFamilyByTag(args[0]) : manager.getFamily(p.getUniqueId());
        if (family == null) {
            throw new FamilyException(args.length > 0 ? "family-not-found" : "not-in-family");
        }
        new TreeGui(service, family, p).open(p);
    }

    /** /family marriage [speler]: trouwmenu | force &lt;groom&gt; &lt;bride&gt; (admin, direct een aftakking) */
    private void marriage(Player p, String[] args) throws FamilyException {
        if (args.length == 3 && args[0].equalsIgnoreCase("force")) {
            if (!service.isAdmin(p)) {
                throw new FamilyException("no-permission");
            }
            service.forceMarriage(player(args[1]), player(args[2]));
            return;
        }
        FamilyManager.Engagement engagement = null;
        if (args.length >= 1) {
            engagement = manager.getEngagement(player(args[0]));
        } else {
            engagement = manager.getEngagement(p.getUniqueId());
            if (engagement == null) {
                // hoofd van de familie van een bruid: de eerste verloving waar hij de trouwerij moet starten
                for (FamilyManager.Engagement e : manager.getEngagements()) {
                    Family bf = manager.getFamily(e.getBride());
                    if (bf != null && bf.isOwner(p.getUniqueId())) {
                        engagement = e;
                        break;
                    }
                }
            }
        }
        if (engagement == null) {
            throw new FamilyException("usage-marriage");
        }
        if (!service.canView(p, engagement)) {
            throw new FamilyException("no-permission");
        }
        service.openWedding(p, engagement);
    }

    /** /family rename &lt;naam&gt; &lt;tag&gt; [familie-tag als admin] */
    private void rename(Player p, String[] args) throws FamilyException {
        if (args.length < 2) {
            throw new FamilyException("usage-rename");
        }
        Family family = editableFamily(p, args, 2);
        service.rename(p, family, args[0], args[1]);
    }

    private UUID player(String arg) throws FamilyException {
        UUID uuid = service.findPlayer(arg);
        if (uuid == null) {
            throw new FamilyException("unknown-player");
        }
        return uuid;
    }

    /** De familie van een speler (naam/UUID): de eigen familie, of als admin elke familie. */
    private Family targetFamily(Player p, String playerArg) throws FamilyException {
        Family own = manager.getFamily(p.getUniqueId());
        if (own != null && service.findMember(own, playerArg) != null) {
            return own;
        }
        UUID target = service.findPlayer(playerArg);
        Family family = target == null ? null : manager.getFamily(target);
        if (family != null && service.isAdmin(p)) {
            return family;
        }
        if (own == null) {
            throw new FamilyException("not-in-family");
        }
        throw new FamilyException("rank-not-member");
    }

    private UUID memberOf(Family family, String arg) throws FamilyException {
        UUID member = service.findMember(family, arg);
        if (member == null) {
            throw new FamilyException("rank-not-member");
        }
        return member;
    }

    /** Eigen familie, of als admin de familie met de tag op plek {@code index}. */
    private Family editableFamily(Player p, String[] args, int index) throws FamilyException {
        if (args.length > index && service.isAdmin(p)) {
            Family family = manager.getFamilyByTag(args[index]);
            if (family == null) {
                throw new FamilyException("family-not-found");
            }
            return family;
        }
        Family family = manager.getFamily(p.getUniqueId());
        if (family == null) {
            throw new FamilyException("not-in-family");
        }
        return family;
    }

    // ------------------------------------------------------------------ helpers

    private Player player(CommandSender sender) throws FamilyException {
        if (!(sender instanceof Player)) {
            throw new FamilyException("players-only");
        }
        return (Player) sender;
    }

    private Family ownFamily(Player p) throws FamilyException {
        Family family = manager.getFamily(p.getUniqueId());
        if (family == null) {
            throw new FamilyException("not-in-family");
        }
        if (!family.isOwner(p.getUniqueId())) {
            throw new FamilyException("not-owner");
        }
        return family;
    }

    private void broadcast(Family family, UUID except, String key, String... replacements) {
        for (UUID member : family.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null && !member.equals(except)) {
                send(online, key, replacements);
            }
        }
    }

    /** Saved on the main thread: families.yml is tiny and this avoids concurrent access to the manager. */
    private void save() {
        storage.save(manager);
    }

    private void refresh(UUID player) {
        if (displayNames != null) {
            displayNames.update(player);
        }
    }

    private void send(CommandSender to, String key, String... replacements) {
        to.sendMessage(color(plugin.getConfig().getString("messages.prefix", "")) + message(key, replacements));
    }

    private String message(String key, String... replacements) {
        String text = plugin.getConfig().getString("messages." + key);
        if (text == null) {
            text = key;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return color(text);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "invite":
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
                case "kick":
                    if (sender instanceof Player) {
                        Family f = manager.getFamily(((Player) sender).getUniqueId());
                        if (f != null) {
                            return filter(new ArrayList<>(f.getMembers().values()), args[1]);
                        }
                    }
                    return new ArrayList<>();
                case "check":
                case "members":
                case "editor":
                    return filter(manager.getFamilies().stream().map(Family::getTag).collect(Collectors.toList()), args[1]);
                case "list":
                    List<String> kingdoms = new ArrayList<>();
                    kingdoms.add("all");
                    kc.getKingdoms().forEach(k -> kingdoms.add(k.getName()));
                    return filter(kingdoms, args[1]);
                case "rank":
                    return filter(Arrays.asList("give", "list"), args[1]);
                case "tree":
                    return filter(Arrays.asList("link", "unlink"), args[1]);
                case "marriage":
                    List<String> options = new ArrayList<>();
                    if (sender.hasPermission("family.admin")) {
                        options.add("force");
                    }
                    options.addAll(memberNames(sender));
                    return filter(options, args[1]);
                default:
                    return new ArrayList<>();
            }
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 3 && sub.equals("rank") && args[1].equalsIgnoreCase("give")) {
            return filter(memberNames(sender), args[2]);
        }
        if (args.length == 4 && sub.equals("rank") && args[1].equalsIgnoreCase("give")) {
            return filter(Arrays.stream(FamilyRank.values()).map(FamilyRank::dutchId).collect(Collectors.toList()), args[3]);
        }
        if (args.length == 3 && sub.equals("tree") && args[1].equalsIgnoreCase("link")) {
            return filter(Arrays.asList("parent", "child", "partner", "sibling"), args[2]);
        }
        if (sub.equals("tree") && ((args[1].equalsIgnoreCase("link") && (args.length == 4 || args.length == 5))
                || (args[1].equalsIgnoreCase("unlink") && (args.length == 3 || args.length == 4)))) {
            return filter(memberNames(sender), args[args.length - 1]);
        }
        if (args.length == 3 && sub.equals("marriage")) {
            List<String> names = new ArrayList<>(memberNames(sender));
            Bukkit.getOnlinePlayers().forEach(pl -> names.add(pl.getName()));
            return filter(names, args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> memberNames(CommandSender sender) {
        if (sender instanceof Player) {
            Family f = manager.getFamily(((Player) sender).getUniqueId());
            if (f != null) {
                return new ArrayList<>(f.getMembers().values());
            }
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).sorted().collect(Collectors.toList());
    }
}
