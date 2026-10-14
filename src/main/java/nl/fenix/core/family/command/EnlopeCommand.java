package nl.fenix.core.family.command;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /enlope &lt;speler&gt; | accept | deny | cancel: verloving, de stap voor het trouwmenu. */
public final class EnlopeCommand implements CommandExecutor, TabCompleter {

    private final FamilyService service;

    public EnlopeCommand(FamilyService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("family.use")) {
            service.send(player, "no-permission");
            return true;
        }
        try {
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "" -> {
                    FamilyManager.Engagement e = service.manager().getEngagement(player.getUniqueId());
                    if (e == null) {
                        service.send(player, "usage-enlope");
                    } else {
                        service.openWedding(player, e);
                    }
                }
                case "accept", "ja" -> service.acceptEngagement(player);
                case "deny", "nee" -> service.denyEngagement(player);
                case "cancel", "verbreek" -> service.cancelEngagement(player);
                default -> {
                    UUID target = service.findPlayer(args[0]);
                    if (target == null) {
                        throw new FamilyException("unknown-player");
                    }
                    service.engage(player, target);
                }
            }
        } catch (FamilyException ex) {
            service.send(player, ex.getKey());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("accept");
            options.add("deny");
            options.add("cancel");
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (!p.equals(sender)) {
                    options.add(p.getName());
                }
            });
            String typed = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(typed));
        }
        return options;
    }
}
