package nl.fenix.core.family.storage;

import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Alle families en de stamboom in families.yml. Leest ook het oude formaat (members.&lt;uuid&gt;: naam) van
 * FamilyCraft; dat wordt bij de volgende keer opslaan vanzelf omgezet.
 */
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
        if (families != null) {
            for (String key : families.getKeys(false)) {
                try {
                    manager.register(loadFamily(families.getConfigurationSection(key), manager.getGenealogy()));
                } catch (RuntimeException ex) {
                    logger.warning("Familie '" + key + "' kon niet geladen worden: " + ex);
                }
            }
        }
        ConfigurationSection people = yaml.getConfigurationSection("people");
        if (people != null) {
            Genealogy tree = manager.getGenealogy();
            for (String key : people.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection p = people.getConfigurationSection(key);
                    tree.setName(uuid, p.getString("name"));
                    for (String parent : p.getStringList("parents")) {
                        tree.loadParent(UUID.fromString(parent), uuid);
                    }
                    if (p.isString("partner")) {
                        tree.loadPartner(uuid, UUID.fromString(p.getString("partner")));
                    }
                    for (String sibling : p.getStringList("siblings")) {
                        tree.loadSibling(uuid, UUID.fromString(sibling));
                    }
                } catch (RuntimeException ex) {
                    logger.warning("Stamboom van '" + key + "' kon niet geladen worden: " + ex);
                }
            }
        }
    }

    private static Family loadFamily(ConfigurationSection s, Genealogy tree) {
        UUID owner = UUID.fromString(s.getString("owner"));
        ConfigurationSection members = s.getConfigurationSection("members");
        Family family = new Family(s.getString("name"), s.getString("tag"), owner, memberName(members, owner.toString()),
                Instant.ofEpochMilli(s.getLong("created")));
        family.setKingdomName(s.getString("kingdom"));
        family.setCrest(s.getString("crest"));
        family.getLineage().addAll(s.getStringList("lineage"));
        family.setNeedsRename(s.getBoolean("needs-rename"));
        if (members != null) {
            for (String key : members.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String name = memberName(members, key);
                FamilyRank rank = members.isConfigurationSection(key)
                        ? FamilyRank.parse(members.getString(key + ".rank")) : null;
                if (!uuid.equals(owner)) {
                    family.addMember(uuid, name, rank == null ? FamilyRank.CHILD : rank);
                }
                tree.setName(uuid, name);
            }
        }
        return family;
    }

    /** Nieuw formaat: members.&lt;uuid&gt;.name, oud formaat: members.&lt;uuid&gt;: naam. */
    private static String memberName(ConfigurationSection members, String key) {
        if (members == null) {
            return "?";
        }
        return members.isConfigurationSection(key) ? members.getString(key + ".name", "?") : members.getString(key, "?");
    }

    public synchronized void save(FamilyManager manager) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Family f : manager.getFamilies()) {
            String path = "families." + f.getTag().toLowerCase(Locale.ROOT);
            yaml.set(path + ".name", f.getName());
            yaml.set(path + ".tag", f.getTag());
            yaml.set(path + ".owner", f.getOwner().toString());
            yaml.set(path + ".created", f.getCreatedAt().toEpochMilli());
            yaml.set(path + ".kingdom", f.getKingdomName());
            yaml.set(path + ".crest", f.getCrest());
            if (f.isBranch()) {
                yaml.set(path + ".lineage", new ArrayList<>(f.getLineage()));
            }
            if (f.needsRename()) {
                yaml.set(path + ".needs-rename", true);
            }
            for (Map.Entry<UUID, String> e : f.getMembers().entrySet()) {
                yaml.set(path + ".members." + e.getKey() + ".name", e.getValue());
                yaml.set(path + ".members." + e.getKey() + ".rank", f.getRank(e.getKey()).id());
            }
        }
        Genealogy tree = manager.getGenealogy();
        for (UUID uuid : tree.people()) {
            String path = "people." + uuid;
            yaml.set(path + ".name", tree.name(uuid));
            List<String> parents = new ArrayList<>();
            tree.parentsOf(uuid).forEach(p -> parents.add(p.toString()));
            if (!parents.isEmpty()) {
                yaml.set(path + ".parents", parents);
            }
            UUID partner = tree.partnerOf(uuid);
            if (partner != null) {
                yaml.set(path + ".partner", partner.toString());
            }
            List<String> siblings = new ArrayList<>();
            tree.siblingsOf(uuid).forEach(p -> siblings.add(p.toString()));
            if (!siblings.isEmpty()) {
                yaml.set(path + ".siblings", siblings);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.severe("families.yml kon niet opgeslagen worden: " + ex);
        }
    }
}
