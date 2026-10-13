package com.familycraft;

import com.familycraft.command.FamilyCommand;
import com.familycraft.economy.VaultEconomy;
import com.familycraft.model.FamilyManager;
import com.familycraft.placeholder.DisplayNames;
import com.familycraft.placeholder.FamilyPlaceholders;
import com.familycraft.storage.FamilyStorage;
import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.KingdomCraftProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class FamilyCraftPlugin extends JavaPlugin {

    private FamilyManager manager;
    private FamilyStorage storage;
    private FamilyPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration cfg = getConfig();

        KingdomCraft kc = KingdomCraftProvider.get();
        if (kc == null) {
            getLogger().severe("KingdomCraft API not available, disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        manager = new FamilyManager(
                cfg.getInt("settings.max-name-length", 30),
                cfg.getInt("settings.max-tag-length", 3),
                cfg.getInt("settings.max-members", 0),
                cfg.getLong("settings.invite-expire-seconds", 60));

        storage = new FamilyStorage(new File(getDataFolder(), "families.yml"), getLogger());
        storage.load(manager);

        VaultEconomy economy = null;
        if (cfg.getDouble("economy.create-cost", 0) > 0) {
            economy = VaultEconomy.hook();
            if (economy == null) {
                getLogger().warning("economy.create-cost is set but Vault/economy was not found; creating will be refused.");
            }
        }

        placeholders = new FamilyPlaceholders(kc, manager, cfg.getString("placeholders.prefix-format", "&7[&f%tag%&7] "));
        placeholders.register();

        DisplayNames displayNames = null;
        if (cfg.getBoolean("display-name.enabled", true)) {
            displayNames = new DisplayNames(this, kc,
                    cfg.getString("display-name.format", "{kingdom_prefix} {family_prefix} %player%{kingdom_suffix}"));
            displayNames.register();
        }

        FamilyCommand command = new FamilyCommand(this, kc, manager, storage, economy, displayNames);
        getCommand("family").setExecutor(command);
        getCommand("family").setTabCompleter(command);

        getLogger().info("Loaded " + manager.getFamilies().size() + " families.");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (storage != null && manager != null) {
            storage.save(manager);
        }
    }
}
