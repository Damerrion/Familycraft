package nl.fenix.core.family.command;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.FamilyException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /divorce: vraag je partner om te scheiden | accept | deny | force &lt;speler&gt; (admin mode). */
public final class DivorceCommand implements CommandExecutor, TabCompleter {

    private final FamilyService service;

    public DivorceCommand(FamilyService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            service.send(sender, "players-only");
            return true;
        }
        try {
            service.require(player, "family.divorce");
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "" -> service.requestDivorce(player);
                case "accept", "ja" -> service.acceptDivorce(player);
                case "deny", "nee" -> service.denyDivorce(player);
                case "force" -> {
                    if (!service.isAdmin(player) || args.length < 2) {
                        throw new FamilyException(args.length < 2 ? "usage-divorce" : "no-permission");
                    }
                    UUID target = service.findPlayer(args[1]);
                    if (target == null) {
                        throw new FamilyException("unknown-player");
                    }
                    service.forceDivorce(target);
                }
                default -> throw new FamilyException("usage-divorce");
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
            if (service.isAdmin(sender)) {
                options.add("force");
            }
            String typed = args[0].toLowerCase(Locale.ROOT);
            options.removeIf(o -> !o.startsWith(typed));
        }
        return options;
    }
}
