package nl.fenix.core.family.command;

import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.family.economy.VaultEconomy;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
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
            "accept", "cancel", "create", "disband", "check", "invite", "kick", "leave", "members", "info", "help");

    private final FamilyModule plugin;
    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final FamilyStorage storage;
    private final VaultEconomy economy;
    private final DisplayNames displayNames;
    private final FamilyInfo info;

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
                case "help":
                    for (String line : plugin.getConfig().getStringList("messages.help")) {
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
            String role = message(family.isOwner(e.getKey()) ? "role-owner" : "role-member");
            sender.sendMessage(message("members-entry", "%player%", e.getValue(), "%role%", role).trim());
        }
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
        String text = plugin.getConfig().getString("messages." + key, key);
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
                    return filter(manager.getFamilies().stream().map(Family::getTag).collect(Collectors.toList()), args[1]);
                default:
                    return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).sorted().collect(Collectors.toList());
    }
}
