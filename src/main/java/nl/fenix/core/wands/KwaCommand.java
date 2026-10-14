package nl.fenix.core.wands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * /kwa, opgezet zoals KingdomWands (/kw, /bind, /unbind) met de Premium-extra's:
 * /kwa [info], /kwa &lt;pagina&gt;, /kwa get [wand] [speler], /kwa bind all|&lt;slot&gt; &lt;spell...&gt;,
 * /kwa unbind &lt;slot|all&gt;, /kwa spells [spell], /kwa effect &lt;effect&gt;, /kwa create &lt;wand&gt; [naam],
 * /kwa dev, /kwa check, /kwa reload. /bind en /unbind zonder /kwa worden afgevangen in
 * {@link WandListener}.
 */
public final class KwaCommand implements TabExecutor {

    private static final String LINE = "<gold>-----------------------------------";
    private static final int HELP_PER_PAGE = 6;

    private final WandsModule plugin;

    public KwaCommand(WandsModule plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        run(sender, label, args);
        return true;
    }

    /** Ook gebruikt voor /bind en /unbind (args[0] is dan "bind" of "unbind"). */
    public void run(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.matches("\\d{1,6}")) {
            help(sender, Integer.parseInt(sub));
            return;
        }
        switch (sub) {
            case "help" -> help(sender, args.length > 1 && args[1].matches("\\d{1,6}") ? Integer.parseInt(args[1]) : 1);
            case "get", "getwand", "menu" -> get(sender, args);
            case "bind" -> bind(sender, args);
            case "unbind" -> unbind(sender, args);
            case "list", "spell", "spells", "spelllist" -> spells(sender, args);
            case "effect" -> effect(sender, args);
            case "create" -> create(sender, args);
            case "info" -> info(sender);
            case "dev" -> dev(sender, args);
            case "check" -> check(sender, args);
            case "reload" -> reload(sender);
            default -> {
                // oude vorm /kwa <wand> [speler] blijft werken
                WandType type = plugin.wand().type(args[0]);
                if (type != null) {
                    giveCommand(sender, type, args.length > 1 ? args[1] : null);
                } else {
                    help(sender, 1);
                }
            }
        }
    }

    // ---------------------------------------------------------------- info en help

    /** /kwa en /kwa info: informatie-blok zoals KingdomWands, plus je wand als je die vasthoudt. */
    public void info(CommandSender sender) {
        sender.sendRichMessage(LINE);
        sender.sendRichMessage("<gray> " + plugin.getPluginMeta().getName());
        sender.sendRichMessage(LINE);
        sender.sendRichMessage("<gray> Plugin Versie: " + plugin.getPluginMeta().getVersion());
        sender.sendRichMessage("<gray> Wands: " + wandIds());
        sender.sendRichMessage("<gray> Spells: " + Spell.all().size());
        sender.sendRichMessage("<gray> KingdomCraft: " + (plugin.hasKingdomCraft() ? "gekoppeld" : "niet gevonden"));
        sender.sendRichMessage("<gray> Help: /kwa 1");
        sender.sendRichMessage(LINE);
        if (!(sender instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.wand().isWand(item)) {
            return;
        }
        WandType held = plugin.wand().getType(item);
        Spell selected = plugin.wand().getSelectedSpell(item);
        player.sendRichMessage("<gold> In je hand: <gray>" + (held == null ? "onbekende wand (niet meer in config)" : held.id()));
        player.sendRichMessage("<gold> Bindingen: " + describe(plugin.wand().getBinds(item)));
        player.sendRichMessage("<gold> Geselecteerd: <gray>" + (selected == null ? "-" : selected.displayName()));
        player.sendRichMessage("<gold> Effect: <gray>" + plugin.wand().getEffect(item).name().toLowerCase(Locale.ROOT));
        String deny = held == null ? "<red>onbekende wand" : plugin.denyReason(player, held);
        player.sendRichMessage("<gold> Gebruik: " + (deny == null ? "<green>toegestaan" : deny));
        if (plugin.isDev(player)) {
            player.sendRichMessage("<gold> Dev mode: <green>aan");
        }
    }

    /** /kwa &lt;n&gt; en /kwa help [n]: hulppagina's zoals /kw 1..3. */
    private void help(CommandSender sender, int page) {
        List<String[]> entries = new ArrayList<>();
        for (WandType type : plugin.wand().types()) {
            entries.add(new String[]{WandItem.plainName(type.name()), "/kwa get " + type.id()});
        }
        entries.add(new String[]{"Een menu met alle wands", "/kwa get"});
        entries.add(new String[]{"Bind alle spells op je wand", "/bind all"});
        entries.add(new String[]{"Bind een spell op een slot", "/bind <slot> <spell>"});
        entries.add(new String[]{"Maak een slot vrij", "/unbind <slot|all>"});
        entries.add(new String[]{"Alle spells", "/kwa spells [spell]"});
        entries.add(new String[]{"Select-effect van je wand", "/kwa effect <effect>"});
        entries.add(new String[]{"Maak een wand van je item", "/kwa create <wand> [naam]"});
        entries.add(new String[]{"Informatie", "/kwa info"});
        if (sender.hasPermission("kwa.dev") || sender.hasPermission("kwa.admin")) {
            entries.add(new String[]{"Dev mode", "/kwa dev [on|off]"});
            entries.add(new String[]{"Wordt een speler geraakt", "/kwa check <speler>"});
        }
        if (sender.hasPermission("kwa.admin")) {
            entries.add(new String[]{"Config herladen", "/kwa reload"});
        }
        int pages = Math.max(1, (entries.size() + HELP_PER_PAGE - 1) / HELP_PER_PAGE);
        int p = Math.max(1, Math.min(pages, page));
        sender.sendRichMessage(LINE);
        sender.sendRichMessage("<gold> FenixCore Wands <gray>(" + p + "/" + pages + ")");
        sender.sendRichMessage(LINE);
        for (int i = (p - 1) * HELP_PER_PAGE; i < Math.min(entries.size(), p * HELP_PER_PAGE); i++) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<gold><desc>: <gray><cmd>", Placeholder.unparsed("desc", entries.get(i)[0]),
                    Placeholder.unparsed("cmd", entries.get(i)[1])));
        }
        sender.sendRichMessage(LINE);
    }

    // ---------------------------------------------------------------- wands krijgen

    /** /kwa get: menu; /kwa get &lt;wand&gt; [speler]: geef een wand (aan jezelf of iemand anders). */
    private void get(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                plugin.sayPlain(sender, "players-only");
                return;
            }
            if (!player.hasPermission("kwa.getwand")) {
                plugin.sayPlain(player, "no-permission");
                return;
            }
            WandMenu.open(plugin, player);
            return;
        }
        WandType type = plugin.wand().type(args[1]);
        if (type == null) {
            plugin.error(sender, "<red>Onbekende wand. <gray>Wands: " + wandIds());
            return;
        }
        giveCommand(sender, type, args.length > 2 ? args[2] : null);
    }

    private void giveCommand(CommandSender sender, WandType type, String targetName) {
        if (!sender.hasPermission(type.permission())) {
            plugin.sayPlain(sender, "no-wand-permission");
            return;
        }
        Player target;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                plugin.error(sender, "<red>Speler <gray>" + targetName + "</gray> is niet online.");
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.error(sender, "<red>Gebruik: /kwa get " + type.id() + " <speler>");
            return;
        }
        give(sender, target, type);
    }

    /** Geef een wand (ook vanuit het menu). Vol inventory: de wand valt op de grond. */
    public void give(CommandSender sender, Player target, WandType type) {
        ItemStack wand = plugin.wand().create(type);
        target.getInventory().addItem(wand).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.say(target, "give", wandName(type));
        if (target != sender) {
            plugin.say(sender, "give-other", Placeholder.unparsed("player", target.getName()));
        }
    }

    // ---------------------------------------------------------------- bind / unbind

    // /bind all | /bind <slot> <spell met spaties>
    private void bind(CommandSender sender, String[] args) {
        Player player = requireWandHolder(sender, "kwa.bind");
        if (player == null) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WandItem wand = plugin.wand();
        WandType type = wand.getType(item);
        TreeMap<Integer, Spell> binds = wand.getBinds(item);
        if (type.spells().isEmpty()) {
            plugin.error(player, "<red>Deze wand heeft geen spells <gray>(controleer wands." + type.id()
                    + ".spells in wands.yml)");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("all")) {
            binds.clear();
            int slot = 1;
            for (Spell spell : type.spells()) {
                if (player.hasPermission(spell.permission()) && slot <= wand.maxSlots()) {
                    binds.put(slot++, spell);
                }
            }
            wand.setBinds(item, binds);
            if (binds.isEmpty()) {
                plugin.error(player, "<red>Je hebt geen permissie voor een van de spells van deze wand (kwa.spells.<Spell>).");
            } else {
                plugin.say(player, "bind-all");
            }
            return;
        }
        if (args.length < 3) {
            plugin.say(player, "bind-usage");
            return;
        }
        Integer slot = parseSlot(player, args[1]);
        if (slot == null) {
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Spell spell = Spell.fromName(name);
        if (spell == null || !type.spells().contains(spell)) {
            plugin.say(player, "unknown-spell");
            plugin.error(player, "<gray>Spells van deze wand: " + spellNames(type));
            return;
        }
        if (!player.hasPermission(spell.permission())) {
            plugin.sayPlain(player, "no-spell-permission", Placeholder.unparsed("spell", spell.displayName()));
            return;
        }
        binds.put(slot, spell); // zoals KingdomWands mag dezelfde spell op meerdere slots
        wand.setBinds(item, binds);
        TagResolver[] values = {Placeholder.unparsed("spell", spell.displayName()),
                Placeholder.unparsed("slot", String.valueOf(slot))};
        plugin.say(player, "bind", values);
        plugin.sayPlain(player, "bind-hint");
    }

    // /unbind <slot|all>
    private void unbind(CommandSender sender, String[] args) {
        Player player = requireWandHolder(sender, "kwa.unbind");
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.say(player, "unbind-usage");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WandItem wand = plugin.wand();
        TreeMap<Integer, Spell> binds = wand.getBinds(item);
        if (args[1].equalsIgnoreCase("all")) {
            binds.clear();
            wand.setBinds(item, binds);
            plugin.say(player, "unbind-all");
            return;
        }
        Integer slot = parseSlot(player, args[1]);
        if (slot == null) {
            return;
        }
        if (binds.remove(slot) == null) {
            plugin.error(player, "<red>Slot " + slot + " is al leeg.");
            return;
        }
        wand.setBinds(item, binds);
        plugin.say(player, "unbind", Placeholder.unparsed("slot", String.valueOf(slot)));
        plugin.sayPlain(player, "unbind-hint");
    }

    // ---------------------------------------------------------------- spells, effect, create

    /** /kwa spells: alle spells op één regel (zoals /kw spells); /kwa spells &lt;spell&gt;: details. */
    private void spells(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kwa.spells") && !(sender instanceof Player p && canUseAnyWand(p))) {
            plugin.sayPlain(sender, "no-permission");
            return;
        }
        if (args.length > 1) {
            Spell spell = Spell.fromName(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            if (spell == null) {
                plugin.say(sender, "unknown-spell");
                return;
            }
            String wands = plugin.wand().types().stream().filter(t -> t.spells().contains(spell))
                    .map(WandType::id).collect(Collectors.joining(", "));
            plugin.msg(sender, "<gray>" + spell.displayName());
            sender.sendRichMessage("<gold> Wat: <gray>" + spell.description(plugin.getConfig()));
            sender.sendRichMessage("<gold> Cooldown: <gray>" + plugin.cooldownSeconds(spell) + "s");
            sender.sendRichMessage("<gold> Wands: <gray>" + (wands.isEmpty() ? "-" : wands));
            sender.sendRichMessage("<gold> Permissie: <gray>" + spell.permission());
            if (sender instanceof Player player) {
                long remaining = plugin.cooldownRemaining(player, spell);
                sender.sendRichMessage("<gold> Status: " + (!player.hasPermission(spell.permission()) ? "<red>geen permissie"
                        : remaining > 0 ? "<red>cooldown " + WandsModule.formatSeconds(remaining) : "<green>klaar"));
            }
            return;
        }
        String list = Spell.all().stream()
                .map(s -> (sender.hasPermission(s.permission()) ? "<gray>" : "<dark_gray>") + s.displayName())
                .collect(Collectors.joining("<gold>, "));
        sender.sendRichMessage("<gold>Spells: " + list);
    }

    // /kwa effect <effect>: verandert het select-effect van de wand in je hand (zoals /kw effect)
    private void effect(CommandSender sender, String[] args) {
        Player player = requireWandHolder(sender, "kwa.effect", false);
        if (player == null) {
            return;
        }
        String all = Arrays.stream(SelectEffect.values())
                .map(e -> e.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
        if (args.length < 2) {
            plugin.msg(player, "<gold>Usage: /kwa effect <effect> <gray>(" + all + ")");
            return;
        }
        SelectEffect effect = SelectEffect.fromName(args[1]);
        if (effect == null) {
            plugin.error(player, "<red>Onbekend effect. <gray>Kies uit: " + all);
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        plugin.wand().setEffect(item, effect);
        effect.play(player);
        plugin.msg(player, "<gold>Select-effect is nu <gray>" + effect.name().toLowerCase(Locale.ROOT));
    }

    // /kwa create <wand> [naam]: maakt van het item in je hand de gekozen wand (zoals /kw create)
    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sayPlain(sender, "players-only");
            return;
        }
        if (!player.hasPermission("kwa.create")) {
            plugin.sayPlain(player, "no-permission");
            return;
        }
        WandType type = args.length > 1 ? plugin.wand().type(args[1]) : null;
        if (type == null) {
            plugin.msg(player, "<gold>Usage: /kwa create <wand> [naam] <gray>(wands: " + wandIds() + ")");
            return;
        }
        if (!player.hasPermission(type.permission())) {
            plugin.sayPlain(player, "no-wand-permission");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            plugin.error(player, "<red>Houd het item vast waar je een wand van wilt maken.");
            return;
        }
        if (item.getAmount() != 1) {
            plugin.error(player, "<red>Houd precies 1 item vast.");
            return;
        }
        String name = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        plugin.wand().convert(item, type, name);
        plugin.say(player, "create", wandName(type));
    }

    // ---------------------------------------------------------------- dev en check

    // /kwa dev [on|off]: dev mode voor jezelf (geen cooldowns, spells raken ook jezelf/mobs, debug)
    private void dev(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sayPlain(sender, "players-only");
            return;
        }
        boolean on;
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            on = true;
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            on = false;
        } else {
            on = !plugin.isDev(player);
        }
        // uitzetten mag altijd; aanzetten alleen met kwa.dev
        if (on && !player.hasPermission("kwa.dev")) {
            plugin.sayPlain(player, "no-permission");
            return;
        }
        plugin.setDev(player, on);
        if (!on) {
            plugin.msg(player, "<gold>Dev mode <red>uit<gold>.");
            return;
        }
        List<String> active = new ArrayList<>();
        if (plugin.dev(player, "no-cooldown")) {
            active.add("geen cooldowns");
        }
        if (plugin.dev(player, "hit-self")) {
            active.add("raakt jezelf");
        }
        if (plugin.dev(player, "hit-mobs")) {
            active.add("raakt mobs");
        }
        if (plugin.dev(player, "ignore-kingdom")) {
            active.add("raakt ook eigen kingdom, bondgenoten en creative");
        }
        if (plugin.dev(player, "debug")) {
            active.add("debug in chat");
        }
        plugin.msg(player, "<gold>Dev mode <green>aan<gold>: <gray>" + (active.isEmpty() ? "geen opties (zie dev: in wands.yml)"
                : String.join(", ", active)) + ". <dark_gray>Alleen jouw spells; /kwa dev off om te stoppen.");
        if (plugin.dev(player, "ignore-kingdom")) {
            plugin.msg(player, "<gold>Let op: <gray>je spells raken nu ook spelers in de buurt van je eigen kingdom,"
                    + " bondgenoten en spelers in creative.");
        }
    }

    // /kwa check <speler>: zou deze speler door jouw spells geraakt worden, en waarom (kingdom, relatie, dev mode)
    private void check(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sayPlain(sender, "players-only");
            return;
        }
        if (!player.hasPermission("kwa.dev") && !player.hasPermission("kwa.admin")) {
            plugin.sayPlain(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.msg(player, "<gold>Usage: /kwa check <speler>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.error(player, "<red>Speler <gray>" + args[1] + "</gray> is niet online.");
            return;
        }
        // de wand in je hand, anders de eerste wand uit de config
        WandType type = plugin.wand().getType(player.getInventory().getItemInMainHand());
        if (type == null) {
            type = plugin.wand().types().stream().findFirst().orElse(null);
        }
        if (type == null) {
            plugin.error(player, "<red>Er zijn geen wands in de config.");
            return;
        }
        boolean hit = plugin.affects(player, target, type);
        plugin.msg(player, "<gold>Check <gray>" + target.getName() + "</gray> voor wand <gray>" + type.id()
                + "</gray> (kingdom: <gray>" + (type.kingdom().isEmpty() ? "-" : type.kingdom()) + "</gray>)");
        player.sendRichMessage("<gold> KingdomCraft: " + (plugin.hasKingdomCraft() ? "<green>gekoppeld" : "<red>niet gekoppeld"));
        player.sendRichMessage("<gold> Kingdom: " + plugin.describeKingdom(target, type));
        player.sendRichMessage("<gold> Gamemode: <gray>" + target.getGameMode().name().toLowerCase(Locale.ROOT));
        player.sendRichMessage("<gold> Resultaat: " + (hit ? "<red>wordt GERAAKT" : "<green>wordt gespaard"));
        if (plugin.dev(player, "ignore-kingdom")) {
            player.sendRichMessage("<gold> Let op: <gray>jouw dev mode staat aan met ignore-kingdom, daardoor worden"
                    + " je eigen kingdom en bondgenoten nu WEL geraakt. <white>/kwa dev off</white> om normaal te testen.");
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("kwa.admin")) {
            plugin.sayPlain(sender, "no-permission");
            return;
        }
        plugin.reload();
        plugin.msg(sender, "<green>Config herladen. <gold>KingdomCraft: "
                + (plugin.hasKingdomCraft() ? "<green>gekoppeld" : "<red>niet gevonden"));
    }

    // ---------------------------------------------------------------- hulp

    /**
     * Speler met een wand van de addon in de hand. Binden mag met de commando-permissie, of als je de wand in je
     * hand mag gebruiken (zoals bij KingdomWands, waar /bind geen aparte permissie vraagt).
     */
    private Player requireWandHolder(CommandSender sender, String permission) {
        return requireWandHolder(sender, permission, true);
    }

    /** @param wandUsersAllowed false = alleen met de commando-permissie (bv. /kwa effect vraagt echt kwa.effect) */
    private Player requireWandHolder(CommandSender sender, String permission, boolean wandUsersAllowed) {
        if (!(sender instanceof Player player)) {
            plugin.sayPlain(sender, "players-only");
            return null;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        WandType type = plugin.wand().getType(held);
        if (type == null) {
            if (plugin.wand().isWand(held)) {
                plugin.error(player, "<red>Deze wand staat niet (meer) in de config.");
            } else {
                plugin.sayPlain(player, "no-wand");
            }
            return null;
        }
        if (!player.hasPermission(permission) && !(wandUsersAllowed && plugin.canUse(player, type))) {
            plugin.sayPlain(player, "no-permission");
            return null;
        }
        return player;
    }

    private boolean canUseAnyWand(Player player) {
        return plugin.wand().types().stream().anyMatch(t -> plugin.canUse(player, t));
    }

    private Integer parseSlot(Player player, String raw) {
        int max = plugin.wand().maxSlots();
        try {
            int slot = Integer.parseInt(raw);
            if (slot >= 1 && slot <= max) {
                return slot;
            }
        } catch (NumberFormatException ignored) {
            // valt door naar foutmelding
        }
        plugin.error(player, "<red>Slot moet een getal van 1 t/m " + max + " zijn.");
        return null;
    }

    private String wandIds() {
        String ids = plugin.wand().types().stream().map(WandType::id).collect(Collectors.joining(", "));
        return ids.isEmpty() ? "geen" : ids;
    }

    private static String spellNames(WandType type) {
        return type.spells().isEmpty() ? "geen"
                : type.spells().stream().map(Spell::displayName).collect(Collectors.joining(", "));
    }

    private static TagResolver wandName(WandType type) {
        return Placeholder.component("wand", WandItem.parseName(type.name()));
    }

    private static String describe(Map<Integer, Spell> binds) {
        if (binds.isEmpty()) {
            return "<gray>geen";
        }
        List<String> parts = new ArrayList<>();
        binds.forEach((slot, spell) -> parts.add("<gray>" + slot + "=" + spell.displayName()));
        return String.join("<gold>, ", parts);
    }

    // ---------------------------------------------------------------- tab-completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            options.addAll(List.of("get", "info", "spells", "help"));
            if (sender.hasPermission("kwa.bind")) {
                options.add("bind");
            }
            if (sender.hasPermission("kwa.unbind")) {
                options.add("unbind");
            }
            if (sender.hasPermission("kwa.effect")) {
                options.add("effect");
            }
            if (sender.hasPermission("kwa.create")) {
                options.add("create");
            }
            if (sender.hasPermission("kwa.dev")) {
                options.add("dev");
            }
            if (sender.hasPermission("kwa.dev") || sender.hasPermission("kwa.admin")) {
                options.add("check");
            }
            if (sender.hasPermission("kwa.admin")) {
                options.add("reload");
            }
        } else if (args.length == 2) {
            switch (sub) {
                case "get", "getwand", "create" -> {
                    for (WandType type : plugin.wand().types()) {
                        if (sender.hasPermission(type.permission())) {
                            options.add(type.id());
                        }
                    }
                }
                case "dev" -> options.addAll(List.of("on", "off"));
                case "check" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                case "effect" -> {
                    for (SelectEffect effect : SelectEffect.values()) {
                        options.add(effect.name().toLowerCase(Locale.ROOT));
                    }
                }
                case "spells", "spell", "list", "spelllist" ->
                        Spell.all().forEach(s -> options.add(s.displayName().replace(" ", "")));
                case "bind", "unbind" -> {
                    options.add("all");
                    int max = slotSuggestions(sender);
                    for (int i = 1; i <= max; i++) {
                        options.add(String.valueOf(i));
                    }
                }
                default -> { }
            }
        } else if (args.length == 3 && (sub.equals("get") || sub.equals("getwand"))) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 3 && sub.equals("bind") && !args[1].equalsIgnoreCase("all")) {
            WandType held = sender instanceof Player player
                    ? plugin.wand().getType(player.getInventory().getItemInMainHand()) : null;
            for (Spell spell : held == null ? List.copyOf(Spell.all()) : held.spells()) {
                if (sender.hasPermission(spell.permission())) {
                    // zonder spaties, zodat het na het eerste woord nog te matchen is (/bind 1 EmpireSpark)
                    options.add(spell.displayName().replace(" ", ""));
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }

    /** Slotnummers voorstellen tot het aantal spells van de wand in je hand (of het hoogste gebonden slot). */
    private int slotSuggestions(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return 9;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        WandType type = plugin.wand().getType(item);
        if (type == null) {
            return 9;
        }
        TreeMap<Integer, Spell> binds = plugin.wand().getBinds(item);
        int highest = binds.isEmpty() ? 0 : binds.lastKey();
        return Math.min(plugin.wand().maxSlots(), Math.max(type.spells().size(), highest));
    }
}
