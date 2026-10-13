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

    private FamilyManager manager;
    private FamilyStorage storage;
    private FamilyPlaceholders placeholders;

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
        // ontbrekende instellingen (bv. uit een oudere versie) komen uit de family.yml in de jar
        InputStream defaults = core.getResource(CONFIG_FILE);
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
    }

    public void enable() {
        loadConfig();
        FileConfiguration cfg = config;

        KingdomCraft kc = KingdomCraftProvider.get();
        if (kc == null) {
            throw new IllegalStateException("KingdomCraft API niet beschikbaar");
        }

        manager = new FamilyManager(
                cfg.getInt("settings.max-name-length", 30),
                cfg.getInt("settings.max-tag-length", 3),
                cfg.getInt("settings.max-members", 0),
                cfg.getLong("settings.invite-expire-seconds", 60));
        for (FamilyRank rank : FamilyRank.values()) {
            if (rank != FamilyRank.HEAD && cfg.isSet("ranks." + rank.id() + ".max")) {
                manager.setRankLimit(rank, cfg.getInt("ranks." + rank.id() + ".max"));
            }
        }
        manager.setProposalExpireSeconds(cfg.getLong("marriage.proposal-expire-minutes", 30) * 60);
        manager.setRenameAtParts(cfg.getInt("marriage.rename-at-families", 4));

        storage = new FamilyStorage(new File(core.getDataFolder(), DATA_FILE), getLogger());
        storage.load(manager);

        VaultEconomy economy = null;
        if (cfg.getDouble("economy.create-cost", 0) > 0) {
            economy = VaultEconomy.hook();
            if (economy == null) {
                getLogger().warning("economy.create-cost staat aan maar Vault/economie niet gevonden; familie maken wordt geweigerd.");
            }
        }

        placeholders = new FamilyPlaceholders(kc, manager, cfg.getString("placeholders.prefix-format", "&7[&f%tag%&7] "));
        placeholders.register();

        DisplayNames displayNames = null;
        if (cfg.getBoolean("display-name.enabled", true)) {
            displayNames = new DisplayNames(core, kc,
                    cfg.getString("display-name.format", "{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}"));
            displayNames.register();
        }

        FamilyInfo info = new FamilyInfo(this, kc, manager, storage);
        info.register();

        if (cfg.getBoolean("chat-hover.enabled", true)) {
            new FamilyHover(core, kc, manager, placeholders, info,
                    cfg.getStringList("chat-hover.lines"), cfg.getString("chat-hover.click-command", "")).register();
        }

        FamilyCommand command = new FamilyCommand(this, kc, manager, storage, economy, displayNames, info);
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
        PluginCommand family = core.getCommand("family");
        if (family != null) {
            family.setExecutor(command);
            family.setTabCompleter(command);
        }

        getLogger().info("Family-module aan: " + manager.getFamilies().size() + " families geladen.");
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
