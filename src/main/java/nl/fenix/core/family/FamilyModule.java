package nl.fenix.core.family;

import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.KingdomCraftProvider;
import nl.fenix.core.FenixCore;
import nl.fenix.core.family.chat.FamilyHover;
import nl.fenix.core.family.command.FamilyCommand;
import nl.fenix.core.family.economy.VaultEconomy;
import nl.fenix.core.family.gui.GuiListener;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.placeholder.DisplayNames;
import nl.fenix.core.family.placeholder.FamilyInfo;
import nl.fenix.core.family.placeholder.FamilyPlaceholders;
import nl.fenix.core.family.storage.FamilyStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Family-module van FenixCore (voorheen de losse plugin FamilyCraft). Config in {@code family.yml},
 * families in {@code families.yml}. Vereist KingdomCraft.
 */
public final class FamilyModule {

    public static final String CONFIG_FILE = "family.yml";
    public static final String DATA_FILE = "families.yml";

    private final FenixCore core;
    private final File configFile;
    private YamlConfiguration config;
    /** family.yml had een YAML-fout: nooit opslaan, anders is hij leeg */
    private boolean configBroken;

    private FamilyManager manager;
    private FamilyStorage storage;
    private FamilyPlaceholders placeholders;
    private DisplayNames displayNames;
    private FamilyHover hover;
    private FamilyCommand command;

    public FamilyModule(FenixCore core) {
        this.core = core;
        this.configFile = new File(core.getDataFolder(), CONFIG_FILE);
    }

    /** De FenixCore-plugin (voor scheduler, events, ...). */
    public Plugin plugin() {
        return core;
    }

    public Logger getLogger() {
        return core.getLogger();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            core.saveResource(CONFIG_FILE, false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaults = core.getResource(CONFIG_FILE);
        if (defaults == null) {
            return;
        }
        YamlConfiguration jar = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));
        config.setDefaults(jar);
        configBroken = config.getKeys(false).isEmpty();
        if (configBroken) {
            // YAML-fout: Bukkit geeft dan een lege config. Nooit opslaan, anders is family.yml weg.
            getLogger().severe(CONFIG_FILE + " kon niet geladen worden (YAML-fout?); de standaardwaarden worden gebruikt.");
            return;
        }
        boolean changed = replaceOldDefaults(jar);
        // Nieuwe instellingen en teksten (uit een nieuwere versie) in de bestaande family.yml zetten. Nodig omdat
        // getString(pad, standaard) de defaults uit de jar overslaat; zo kan de admin ze ook meteen aanpassen.
        int before = config.getKeys(true).size();
        config.options().copyDefaults(true);
        if (changed || config.getKeys(true).size() != before) {
            try {
                config.save(configFile);
                getLogger().info(CONFIG_FILE + " aangevuld met nieuwe instellingen en teksten.");
            } catch (java.io.IOException e) {
                getLogger().warning(CONFIG_FILE + " kon niet bijgewerkt worden: " + e);
            }
        }
    }

    /** Instelling aanpassen en family.yml opslaan (bv. verboden wapens); niet als family.yml een YAML-fout had. */
    public void setAndSave(String path, Object value) {
        config.set(path, value);
        if (configBroken) {
            getLogger().warning(CONFIG_FILE + " heeft een YAML-fout; '" + path + "' geldt tot de volgende herstart.");
            return;
        }
        try {
            config.save(configFile);
        } catch (java.io.IOException e) {
            getLogger().warning(CONFIG_FILE + " kon niet opgeslagen worden: " + e);
        }
    }

    /** Waarden die nog precies de oude standaard zijn (family-old-defaults.yml) vervangen door de nieuwe. */
    private boolean replaceOldDefaults(YamlConfiguration jar) {
        InputStream in = core.getResource("family-old-defaults.yml");
        if (in == null) {
            return false;
        }
        YamlConfiguration versions = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        boolean changed = false;
        // elke sectie (v1, v2, ...) bevat de standaardwaarden van een oudere versie
        for (String version : versions.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection old = versions.getConfigurationSection(version);
            if (old == null) {
                continue;
            }
            for (String path : old.getKeys(true)) {
                if (old.isConfigurationSection(path) || !config.isSet(path)) {
                    continue;
                }
                if (java.util.Objects.equals(config.get(path), old.get(path)) && jar.isSet(path)) {
                    config.set(path, jar.get(path));
                    changed = true;
                }
            }
        }
        if (changed) {
            getLogger().info(CONFIG_FILE + ": oude standaardteksten (o.a. Engelse rangnamen) vervangen door de nieuwe.");
        }
        return changed;
    }

    public void enable() {
        loadConfig();
        FileConfiguration cfg = config;

        KingdomCraft kc = KingdomCraftProvider.get();
        if (kc == null) {
            throw new IllegalStateException("KingdomCraft API niet beschikbaar");
        }

        manager = new FamilyManager(30, 3, 0, 60);
        applySettings();

        storage = new FamilyStorage(new File(core.getDataFolder(), DATA_FILE), getLogger());
        storage.load(manager);

        VaultEconomy economy = hookEconomy();

        placeholders = new FamilyPlaceholders(kc, manager, cfg.getString("placeholders.prefix-format", "&7[&f%tag%&7] "));
        placeholders.register();

        if (cfg.getBoolean("display-name.enabled", true)) {
            displayNames = new DisplayNames(core, kc,
                    cfg.getString("display-name.format", "{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}"));
            displayNames.register();
        }

        FamilyInfo info = new FamilyInfo(this, kc, manager, storage);
        info.register();

        if (cfg.getBoolean("chat-hover.enabled", true)) {
            hover = new FamilyHover(core, kc, manager, placeholders, info,
                    cfg.getStringList("chat-hover.lines"), cfg.getString("chat-hover.click-command", ""));
            hover.register();
        }

        command = new FamilyCommand(this, kc, manager, storage, economy, displayNames, info);
        FamilyService service = new FamilyService(this, kc, manager, storage, displayNames, info);
        command.setService(service);
        core.getServer().getPluginManager().registerEvents(new GuiListener(), core);
        // "popup" voor het hoofd van een aftakking die nog een nieuwe naam moet kiezen
        core.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                Family family = manager.getFamily(event.getPlayer().getUniqueId());
                if (family != null && family.isOwner(event.getPlayer().getUniqueId()) && family.needsRename()) {
                    core.getServer().getScheduler().runTaskLater(core, () -> {
                        if (event.getPlayer().isOnline()) {
                            service.promptRename(event.getPlayer(), family);
                        }
                    }, 60L);
                }
            }
        }, core);
        PluginCommand enlope = core.getCommand("enlope");
        if (enlope != null) {
            nl.fenix.core.family.command.EnlopeCommand executor = new nl.fenix.core.family.command.EnlopeCommand(service);
            enlope.setExecutor(executor);
            enlope.setTabCompleter(executor);
        }
        PluginCommand divorce = core.getCommand("divorce");
        if (divorce != null) {
            nl.fenix.core.family.command.DivorceCommand executor = new nl.fenix.core.family.command.DivorceCommand(service);
            divorce.setExecutor(executor);
            divorce.setTabCompleter(executor);
        }
        PluginCommand family = core.getCommand("family");
        if (family != null) {
            family.setExecutor(command);
            family.setTabCompleter(command);
        }

        getLogger().info("Family-module aan: " + manager.getFamilies().size() + " families geladen.");
    }

    /** Instellingen uit family.yml die bij het starten gelezen worden. */
    private void applySettings() {
        FileConfiguration cfg = config;
        manager.applySettings(
                cfg.getInt("settings.max-name-length", 30),
                cfg.getInt("settings.max-tag-length", 3),
                cfg.getInt("settings.max-members", 0),
                cfg.getLong("settings.invite-expire-seconds", 60));
        for (FamilyRank rank : FamilyRank.values()) {
            if (rank != FamilyRank.HEAD) {
                manager.setRankLimit(rank, cfg.getInt("ranks." + rank.id() + ".max",
                        rank == FamilyRank.SUCCESSOR || rank == FamilyRank.SPARE_HEIR ? 1 : 0));
            }
        }
        manager.setProposalExpireSeconds(cfg.getLong("marriage.proposal-expire-minutes", 30) * 60);
        manager.setRenameAtParts(cfg.getInt("marriage.rename-at-families", 4));
        manager.setArchiveMax(cfg.getInt("archive.max", 200));
        manager.setBannedWords(cfg.getStringList("banned.names"));
    }

    private VaultEconomy hookEconomy() {
        if (config.getDouble("economy.create-cost", 0) <= 0) {
            return null;
        }
        VaultEconomy economy = VaultEconomy.hook();
        if (economy == null) {
            getLogger().warning("economy.create-cost staat aan maar Vault/economie niet gevonden; familie maken wordt geweigerd.");
        }
        return economy;
    }

    /**
     * /fenixcore reload family: family.yml opnieuw lezen en toepassen. De families zelf (families.yml) worden niet
     * opnieuw ingelezen: die staan in het geheugen en worden bij elke wijziging opgeslagen.
     * display-name.enabled en chat-hover.enabled aan/uit zetten vraagt een herstart.
     */
    public void reload() {
        loadConfig();
        applySettings();
        placeholders.setPrefixFormat(config.getString("placeholders.prefix-format", "&7[&f%tag%&7] "));
        if (displayNames != null) {
            displayNames.setFormat(config.getString("display-name.format", "{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}"));
            core.getServer().getOnlinePlayers().forEach(p -> displayNames.update(p.getUniqueId()));
        }
        if (hover != null) {
            hover.setLines(config.getStringList("chat-hover.lines"), config.getString("chat-hover.click-command", ""));
        }
        command.setEconomy(hookEconomy());
    }

    public void disable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (storage != null && manager != null) {
            storage.save(manager);
        }
    }
}
