package nl.fenix.core.wands;

import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import nl.fenix.core.FenixCore;
import nl.fenix.core.wands.spell.Spells;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Wands-module van FenixCore (voorheen de losse plugin KingdomWandsAddon). Heeft dezelfde methodes als een
 * JavaPlugin (getConfig, saveConfig, getLogger, ...), maar met een eigen config: {@code wands.yml}.
 */
public final class WandsModule {

    /** Config-bestand in plugins/FenixCore/ en standaard-config in de jar. */
    public static final String CONFIG_FILE = "wands.yml";

    private final FenixCore core;
    private final File configFile;
    private YamlConfiguration config;

    /** null = KingdomCraft niet gevonden: dan wordt niemand als bondgenoot beschermd. */
    private KingdomHook kingdoms;
    private WandItem wand;
    /** speler -> spell-id -> tijdstip waarop de cooldown voorbij is */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastCooldownMessage = new HashMap<>();
    /** Spelers met dev mode aan (/kwa dev). Alleen in het geheugen: na een herstart staat het weer uit. */
    private final Set<UUID> devMode = new HashSet<>();

    public WandsModule(FenixCore core) {
        this.core = core;
        this.configFile = new File(core.getDataFolder(), CONFIG_FILE);
    }

    public void enable() {
        saveDefaultConfig();
        reloadConfig();
        migrateConfig();

        // eerst de spells: de wands in de config verwijzen ernaar
        Spells.registerAll(this);
        wand = new WandItem(this);
        registerWandPermissions();

        KwaCommand executor = new KwaCommand(this);
        PluginCommand command = core.getCommand("kwa");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new WandListener(this, executor), core);
        getServer().getPluginManager().registerEvents(new WandMenu.Clicks(this, executor), core);

        // Pas koppelen als alle plugins geladen zijn, zodat de laadvolgorde (en de pluginnaam) niet uitmaakt.
        getServer().getScheduler().runTask(core, this::hookKingdoms);
    }

    // ---------------------------------------------------------------- plugin-methodes (eigen wands.yml)

    /** De FenixCore-plugin (voor scheduler, events, ...). */
    public Plugin plugin() {
        return core;
    }

    public Server getServer() {
        return core.getServer();
    }

    public Logger getLogger() {
        return core.getLogger();
    }

    public PluginMeta getPluginMeta() {
        return core.getPluginMeta();
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    /** Zoals JavaPlugin#reloadConfig: bij een YAML-fout is de config leeg (zie migrateConfig), defaults uit de jar. */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        try (Reader reader = textResource()) {
            if (reader != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
        } catch (IOException e) {
            getLogger().warning("Standaard-" + CONFIG_FILE + " uit de jar niet te lezen: " + e);
        }
    }

    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            getLogger().severe("Kon " + CONFIG_FILE + " niet opslaan: " + e);
        }
    }

    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            core.saveResource(CONFIG_FILE, false);
        }
    }

    /** {@code wands.yml} uit de jar, of null. */
    private Reader textResource() {
        InputStream in = core.getResource(CONFIG_FILE);
        return in == null ? null : new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    /** Zoek de KingdomCraft-API. Optioneel: zonder KingdomCraft werkt de wand, maar zonder bondgenoot-bescherming. */
    public void hookKingdoms() {
        kingdoms = null;
        try {
            Class.forName("com.gufli.kingdomcraft.api.KingdomCraftProvider", false, getClass().getClassLoader());
            kingdoms = KingdomHook.create();
        } catch (ClassNotFoundException | LinkageError e) {
            kingdoms = null;
        }
        if (kingdoms != null) {
            getLogger().info("Gekoppeld aan KingdomCraft: het kingdom van een wand en zijn bondgenoten worden niet geraakt.");
        } else {
            getLogger().warning("KingdomCraft niet gevonden: spells raken iedereen (geen bescherming voor kingdoms en bondgenoten).");
        }
    }

    /** Oude standaardnamen van de Tygron-wand uit eerdere versies. */
    private static final Set<String> OLD_DEFAULT_NAMES = Set.of(
            "<dark_red>Aztec Deathwhistle", "<dark_red>Tygron Whistle");

    /** Huidige versie van wands.yml (zie config-version in de config uit de jar). */
    private static final int CONFIG_VERSION = 6;

    /**
     * Wands die versie 5 even als standaard toevoegde (de KingdomWands-wands) met hun standaardnaam. Versie 6 haalt ze
     * weer weg (KWA is alleen een addon met de Tygron-wand); een wand met een eigen naam blijft staan.
     */
    private static final Map<String, String> REMOVED_DEFAULT_WANDS = Map.ofEntries(
            Map.entry("empire", "&cEmpire Wand"), Map.entry("god", "&dGod Wand"), Map.entry("blood", "&cBlood Magic"),
            Map.entry("axe", "&9Axe Wand"), Map.entry("dragon", "&cDragon Killer"),
            Map.entry("mephi", "&7Zeis der Mephidantes"), Map.entry("ragf", "&8Ragftagar Wand"),
            Map.entry("dark", "&5Dark Wand"), Map.entry("celcrius", "&6Celcrius Wand"),
            Map.entry("icezeck", "&fIcezeck Star"), Map.entry("wortel", "&6Wortel Wand"),
            Map.entry("theros", "&8Theros Dagger"));

    /**
     * Werk een wands.yml (of de config.yml van KingdomWandsAddon) uit een eerdere versie bij, zodat bestaande
     * servers niets hoeven te verwijderen.
     * Waarden die de admin zelf heeft aangepast blijven staan; alleen oude standaardwaarden worden vervangen.
     */
    public void migrateConfig() {
        FileConfiguration config = getConfig();
        if (config.getKeys(false).isEmpty()) {
            // YAML-fout: Bukkit geeft dan een lege config terug. Nooit opslaan, anders is de hele wands.yml weg.
            getLogger().severe(CONFIG_FILE + " kon niet geladen worden (YAML-fout?). Er wordt niets omgezet of opgeslagen;"
                    + " de standaardwaarden worden gebruikt tot de fout is opgelost.");
            return;
        }
        boolean changed = migrateToWands(config);
        // zonder config-version = een config van voor versie 2
        int version = config.getInt("config-version", 1);
        if (version < 2) {
            // Scream: geen schud-effect meer, alleen Nausea van 10s (alleen als nog de oude standaard 4s)
            if (config.getInt("scream.nausea-seconds", 4) == 4) {
                config.set("scream.nausea-seconds", 10);
            }
            config.setInlineComments("scream.nausea-seconds", List.of("heftige nausea (draaiend scherm); 0 = geen"));
            config.set("scream.shake", null);
            // Mark: duration-seconds vervangen door glowing-seconds (30) en blindness-seconds (1)
            config.set("mark.duration-seconds", null);
        }
        if (version < 3) {
            // Scream: Slowness + Weakness 10s (was 8s), Nausea 5s (was 4s of 10s) - alleen oude standaardwaarden
            if (config.getInt("scream.fear-duration-seconds", 8) == 8) {
                config.set("scream.fear-duration-seconds", 10);
            }
            int nausea = config.getInt("scream.nausea-seconds", 10);
            if (nausea == 4 || nausea == 10) {
                config.set("scream.nausea-seconds", 5);
            }
        }
        if (version < 4 && config.isSet("dev.ignore-kingdom") && config.getBoolean("dev.ignore-kingdom")) {
            // dev mode: bondgenoten en eigen kingdom standaard NIET meer raken (oude standaard was true)
            config.set("dev.ignore-kingdom", false);
        }
        if (version < 5 && "<dark_gray>[<dark_red>KWA</dark_red>]</dark_gray> ".equals(config.getString("messages.prefix"))) {
            // KingdomWands-stijl: goud/grijs prefix (alleen als nog de oude standaard)
            config.set("messages.prefix", "<gold>[<gray>KWA</gray>]</gold> ");
        }
        if (version < 6) {
            removeKingdomWandsDefaults(config);
        }
        if (version < CONFIG_VERSION) {
            config.set("config-version", CONFIG_VERSION);
            changed = true;
        }
        if (changed) {
            saveConfig();
            getLogger().info(CONFIG_FILE + " bijgewerkt naar versie " + CONFIG_VERSION + ".");
        }
    }

    /** Oud formaat (kingdom-name + wand.*) omzetten naar wands.tygron.*. @return of er iets veranderd is */
    private boolean migrateToWands(FileConfiguration config) {
        if (config.isSet("wands") || (!config.isConfigurationSection("wand") && !config.isSet("kingdom-name"))) {
            return false; // al nieuw formaat, of geen oud formaat (bv. 'wands:' bewust leeg)
        }
        Configuration defaults = config.getDefaults();
        ConfigurationSection defaultWand = defaults == null ? null : defaults.getConfigurationSection("wands.tygron");
        if (defaultWand != null) {
            for (String key : defaultWand.getKeys(false)) {
                config.set("wands.tygron." + key, defaultWand.get(key));
            }
        }
        String oldName = config.getString("wand.name");
        if (config.isSet("wand.name") && oldName != null && !OLD_DEFAULT_NAMES.contains(oldName)) {
            config.set("wands.tygron.name", oldName);
        }
        copyIfSet(config, "wand.lore", "wands.tygron.lore");
        copyIfSet(config, "wand.material", "wands.tygron.material");
        copyIfSet(config, "wand.custom-model-data", "wands.tygron.custom-model-data");
        copyIfSet(config, "wand.default-effect", "wands.tygron.effect");
        copyIfSet(config, "kingdom-name", "wands.tygron.kingdom");
        copyIfSet(config, "wand.slots", "slots");
        config.set("wand", null);
        config.set("kingdom-name", null);
        getLogger().info(CONFIG_FILE + " omgezet naar het nieuwe formaat (wands.tygron).");
        return true;
    }

    /** Versie 5 voegde de KingdomWands-wands toe; die (met hun standaardnaam) en kw-spells weer weghalen. */
    private void removeKingdomWandsDefaults(FileConfiguration config) {
        List<String> removed = new ArrayList<>();
        REMOVED_DEFAULT_WANDS.forEach((id, defaultName) -> {
            if (config.isConfigurationSection("wands." + id) && defaultName.equals(config.getString("wands." + id + ".name"))) {
                config.set("wands." + id, null);
                removed.add(id);
            }
        });
        if (!removed.isEmpty()) {
            getLogger().info("KingdomWands-wands uit " + CONFIG_FILE + " gehaald: " + String.join(", ", removed));
        }
        config.set("kw-spells", null);
        config.set("messages.stopped", null);
        if (config.isSet("slots") && config.getInt("slots") == 100) {
            config.set("slots", 9); // standaard van versie 5 terug naar 9
        }
        // uitleg van versie 5 (God Wand, spells: [all], KingdomWands-effecten) vervangen door die uit de jar
        try (Reader reader = textResource()) {
            if (reader != null) {
                org.bukkit.configuration.file.YamlConfiguration jar =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                config.setInlineComments("slots", jar.getInlineComments("slots"));
                config.setComments("wands", jar.getComments("wands"));
            }
        } catch (java.io.IOException ignored) {
            // alleen commentaar; niet erg als dat niet lukt
        }
    }

    private static void copyIfSet(FileConfiguration config, String from, String to) {
        if (config.isSet(from)) {
            config.set(to, config.get(from));
        }
    }

    /**
     * Per wand: kwa.wand.&lt;id&gt; (krijgen) en kwa.use.&lt;id&gt; (gebruiken), standaard op, als kind van kwa.*
     * (en kwa.use), zodat nieuwe wands uit de config zonder aanpassing van plugin.yml werken.
     */
    public void registerWandPermissions() {
        PluginManager pm = getServer().getPluginManager();
        for (Spell spell : Spell.all()) {
            registerPermission(pm, spell.permission(), "Mag de spell " + spell.displayName() + " binden en casten",
                    "kwa.*", "kwa.spells.*");
        }
        for (WandType type : wand.types()) {
            registerPermission(pm, type.permission(), "Toestemming om de wand " + type.id() + " te krijgen", "kwa.*");
            registerPermission(pm, type.usePermission(), "Mag de wand " + type.id() + " gebruiken", "kwa.*", "kwa.use");
        }
    }

    private static void registerPermission(PluginManager pm, String node, String description, String... parents) {
        if (pm.getPermission(node) == null) {
            pm.addPermission(new Permission(node, description, PermissionDefault.OP));
        }
        for (String parentNode : parents) {
            Permission parent = pm.getPermission(parentNode);
            if (parent != null && !parent.getChildren().containsKey(node)) {
                parent.getChildren().put(node, true);
                parent.recalculatePermissibles();
            }
        }
    }

    public void disable() {
        cooldowns.clear();
        devMode.clear();
    }

    public boolean hasKingdomCraft() {
        return kingdoms != null;
    }

    /** Uitleg over het kingdom van een speler t.o.v. het kingdom van de wand (voor /kwa check). */
    public String describeKingdom(Player target, WandType type) {
        if (kingdoms == null) {
            return "<red>KingdomCraft niet gekoppeld";
        }
        if (type.kingdom().isEmpty()) {
            return "<gray>wand heeft geen kingdom (iedereen wordt geraakt)";
        }
        try {
            return kingdoms.describe(target, type.kingdom());
        } catch (LinkageError e) {
            return "<red>KingdomCraft-API wijkt af: " + e;
        }
    }

    public WandItem wand() {
        return wand;
    }

    // ---------------------------------------------------------------- toegang

    /**
     * Gebruik per wand via permissies: {@code kwa.use.<wand>} (bv. kwa.use.tygron voor de Stamleider).
     * {@code kwa.use} geeft alle wands, zoals kingdomwands.use. Wie een wand opraapt zonder die permissie kan er niets mee.
     *
     * @return null als het mag, anders de reden (MiniMessage).
     */
    public String denyReason(Player player, WandType type) {
        if (!player.hasPermission(type.usePermission())) {
            return "<red>geen permissie <gray>(" + type.usePermission() + ")";
        }
        return null;
    }

    public boolean canUse(Player player, WandType type) {
        return denyReason(player, type) == null;
    }

    /**
     * Raakt iedereen behalve de caster, creative/spectator, en leden of bondgenoten van het kingdom van de wand.
     * In dev mode (zie config {@code dev.*}): hit-self = ook jezelf, hit-mobs = ook mobs,
     * ignore-kingdom = ook het eigen kingdom, bondgenoten en spelers in creative.
     */
    public boolean affects(Player caster, LivingEntity target, WandType type) {
        if (target.isDead()) {
            return false;
        }
        if (target.equals(caster)) {
            return dev(caster, "hit-self");
        }
        if (!(target instanceof Player player)) {
            return !(target instanceof ArmorStand) && dev(caster, "hit-mobs");
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || (mode == GameMode.CREATIVE && !dev(caster, "ignore-kingdom"))) {
            return false;
        }
        if (dev(caster, "ignore-kingdom") || kingdoms == null || type.kingdom().isEmpty()) {
            return true;
        }
        try {
            return !kingdoms.isFriendly(player, type.kingdom());
        } catch (LinkageError e) {
            // KingdomCraft-versie heeft een andere API dan verwacht: koppeling uitzetten in plaats van elke tick te crashen
            getLogger().severe("KingdomCraft-API wijkt af (" + e + "), koppeling uitgeschakeld.");
            kingdoms = null;
            return true;
        }
    }

    // ---------------------------------------------------------------- casten

    /** Cast een spell van een wand met cooldown-controle. Geen kosten, alleen cooldowns. */
    public void cast(Player player, Spell spell, WandType type) {
        if (!canUse(player, type)) {
            return; // zonder kwa.use gebeurt er niets, ook geen melding
        }
        if (!type.spells().contains(spell)) {
            error(player, "<red>Deze wand kan <gray>" + spell.displayName() + "</gray> niet (meer) gebruiken.");
            return;
        }
        if (!player.hasPermission(spell.permission())) {
            sayPlain(player, "no-spell-permission", Placeholder.unparsed("spell", spell.displayName()));
            return;
        }
        boolean noCooldown = dev(player, "no-cooldown");
        long remaining = noCooldown ? 0 : cooldownRemaining(player, spell);
        if (remaining > 0) {
            // in de chat, maar max 1x per seconde zodat spam-klikken de chat niet volspamt
            long now = System.currentTimeMillis();
            Long last = lastCooldownMessage.get(player.getUniqueId());
            if (last == null || now - last >= 1000) {
                lastCooldownMessage.put(player.getUniqueId(), now);
                say(player, "cooldown", Placeholder.unparsed("spell", spell.displayName()),
                        Placeholder.unparsed("time", formatSeconds(remaining)));
            }
            return;
        }
        boolean cast = spell.handler().cast(player, type);
        if (cast && noCooldown) {
            devLog(player, spell.displayName() + ": geen cooldown (dev mode)");
        } else if (cast) {
            cooldowns.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>())
                    .put(spell.id(), System.currentTimeMillis() + cooldownSeconds(spell) * 1000L);
        }
    }

    /** @return resterende cooldown in milliseconden (0 als klaar). */
    public long cooldownRemaining(Player player, Spell spell) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            return 0;
        }
        Long until = map.get(spell.id());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    /**
     * Cooldown van een spell: {@code <spell>.cooldown-seconds} uit de config (ook de standaard uit de jar),
     * anders de standaard van de spell.
     */
    public long cooldownSeconds(Spell spell) {
        String path = spell.configKey() + ".cooldown-seconds";
        if (getConfig().contains(path)) {
            return Math.max(0, getConfig().getLong(path));
        }
        return Math.max(0, spell.defaultCooldownSeconds());
    }

    public void resetCooldowns() {
        cooldowns.clear();
        lastCooldownMessage.clear();
    }

    // ---------------------------------------------------------------- select-toon

    /** Sound-namen die al een waarschuwing gaven (niet elke klik opnieuw loggen). */
    private final Set<String> badSounds = new HashSet<>();

    /**
     * Speel de eigen switch-toon van een spell ({@code <spell>.select-sound}), alleen voor de speler zelf.
     * Toegestaan: Bukkit-naam (BLOCK_NOTE_BLOCK_BELL), Minecraft-key (block.note_block.bell) of een geluid uit een
     * eigen resourcepack (mijnpack:fluit.scream).
     *
     * @return false als de spell geen eigen toon heeft (dan speelt het effect van de wand zijn eigen geluid)
     */
    public boolean playSelectSound(Player player, Spell spell) {
        String key = spell.configKey();
        String raw = getConfig().getString(key + ".select-sound", "");
        if (raw == null || raw.isBlank()) {
            return false;
        }
        raw = raw.trim();
        // zonder eigen standaardwaarde: een oude wands.yml gebruikt dan de waarde uit de jar; 0 of leeg = 1.0
        float volume = (float) getConfig().getDouble(key + ".select-volume");
        float pitch = (float) getConfig().getDouble(key + ".select-pitch");
        volume = volume > 0 ? volume : 1.0f;
        pitch = pitch > 0 ? Math.min(2.0f, pitch) : 1.0f;
        try {
            if (raw.matches("[A-Z0-9_]+")) {
                Sound sound = (Sound) Sound.class.getField(raw).get(null);
                player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
            } else {
                player.playSound(player.getLocation(), raw.toLowerCase(Locale.ROOT), SoundCategory.PLAYERS, volume, pitch);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (badSounds.add(raw)) {
                getLogger().warning("Onbekend geluid in " + key + ".select-sound: '" + raw + "'");
            }
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- dev mode

    /** Dev mode aan? Vervalt meteen als de speler kwa.dev kwijt is (deop, verlopen LuckPerms-permissie). */
    public boolean isDev(Player player) {
        if (!devMode.contains(player.getUniqueId())) {
            return false;
        }
        if (!player.hasPermission("kwa.dev")) {
            devMode.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void setDev(Player player, boolean on) {
        if (on) {
            devMode.add(player.getUniqueId());
        } else {
            devMode.remove(player.getUniqueId());
        }
    }

    /** Staat dev-optie {@code dev.<option>} aan voor deze speler? (alleen als hij dev mode aan heeft) */
    public boolean dev(Player player, String option) {
        // zonder eigen standaardwaarde: oude wands.yml zonder 'dev' gebruikt de waarden uit de jar
        return isDev(player) && getConfig().getBoolean("dev." + option);
    }

    /** Debug-melding in de chat, alleen in dev mode met dev.debug aan. */
    public void devLog(Player player, String miniMessage) {
        if (dev(player, "debug")) {
            msg(player, "<dark_aqua>[dev] <gray>" + miniMessage);
        }
    }

    // ---------------------------------------------------------------- berichten

    public static String formatSeconds(long millis) {
        long seconds = (millis + 999) / 1000;
        return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
    }

    public void msg(CommandSender sender, String miniMessage, TagResolver... placeholders) {
        String prefix = getConfig().getString("messages.prefix", "<gold>[<gray>KWA</gray>]</gold> ");
        sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + miniMessage, placeholders));
    }

    /** Foutmelding zoals bij KingdomWands: rood, zonder prefix. */
    public void error(CommandSender sender, String miniMessage, TagResolver... placeholders) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage, placeholders));
    }

    /** Tekst uit {@code messages.<key>} (standaard uit de config in de jar). */
    public String text(String key) {
        String value = getConfig().getString("messages." + key);
        return value == null ? key : value;
    }

    /** Bericht {@code messages.<key>} met prefix. */
    public void say(CommandSender sender, String key, TagResolver... placeholders) {
        msg(sender, text(key), placeholders);
    }

    /** Bericht {@code messages.<key>} zonder prefix (fouten en extra regels, zoals KingdomWands). */
    public void sayPlain(CommandSender sender, String key, TagResolver... placeholders) {
        error(sender, text(key), placeholders);
    }
}
