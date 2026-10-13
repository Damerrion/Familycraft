package com.familycraft.storage;

import com.familycraft.model.Family;
import com.familycraft.model.FamilyManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/** Stores all families in families.yml. */
public class FamilyStorage {

    private final File file;
    private final Logger logger;

    public FamilyStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void load(FamilyManager manager) {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection families = yaml.getConfigurationSection("families");
        if (families == null) {
            return;
        }
        for (String key : families.getKeys(false)) {
            ConfigurationSection s = families.getConfigurationSection(key);
            try {
                UUID owner = UUID.fromString(s.getString("owner"));
                Family family = new Family(s.getString("name"), s.getString("tag"), owner,
                        s.getString("members." + owner, "?"), Instant.ofEpochMilli(s.getLong("created")));
                family.setKingdomName(s.getString("kingdom"));
                ConfigurationSection members = s.getConfigurationSection("members");
                if (members != null) {
                    for (String uuid : members.getKeys(false)) {
                        family.getMembers().put(UUID.fromString(uuid), members.getString(uuid));
                    }
                }
                manager.register(family);
            } catch (RuntimeException ex) {
                logger.warning("Could not load family '" + key + "': " + ex);
            }
        }
    }

    public synchronized void save(FamilyManager manager) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Family f : manager.getFamilies()) {
            String path = "families." + f.getTag().toLowerCase();
            yaml.set(path + ".name", f.getName());
            yaml.set(path + ".tag", f.getTag());
            yaml.set(path + ".owner", f.getOwner().toString());
            yaml.set(path + ".created", f.getCreatedAt().toEpochMilli());
            yaml.set(path + ".kingdom", f.getKingdomName());
            for (java.util.Map.Entry<UUID, String> e : f.getMembers().entrySet()) {
                yaml.set(path + ".members." + e.getKey(), e.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.severe("Could not save families.yml: " + ex);
        }
    }
}
