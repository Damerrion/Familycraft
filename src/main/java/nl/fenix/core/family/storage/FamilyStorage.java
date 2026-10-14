package nl.fenix.core.family.storage;

import nl.fenix.core.family.model.ArchivedFamily;
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
        loadEngagements(yaml, manager);
        loadArchive(yaml, manager);
        ConfigurationSection dead = yaml.getConfigurationSection("deceased");
        if (dead != null) {
            for (String key : dead.getKeys(false)) {
                try {
                    ConfigurationSection d = dead.getConfigurationSection(key);
                    FamilyRank rank = FamilyRank.parse(d.getString("rank"));
                    manager.loadDeceased(UUID.fromString(key), new FamilyManager.Deceased(UUID.fromString(d.getString("player")),
                            d.getString("name", "?"), rank == null ? FamilyRank.CHILD : rank,
                            Instant.ofEpochMilli(d.getLong("died"))));
                } catch (RuntimeException ex) {
                    logger.warning("Overleden karakter '" + key + "' kon niet geladen worden: " + ex);
                }
            }
        }
        for (String fake : yaml.getStringList("fakes")) {
            try {
                manager.getFakes().add(UUID.fromString(fake));
            } catch (IllegalArgumentException ignored) {
                // kapotte regel overslaan
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
                    for (String origin : p.getStringList("origins")) {
                        tree.addOrigin(uuid, origin);
                    }
                } catch (RuntimeException ex) {
                    logger.warning("Stamboom van '" + key + "' kon niet geladen worden: " + ex);
                }
            }
        }
    }

    private void loadEngagements(YamlConfiguration yaml, FamilyManager manager) {
        for (java.util.Map<?, ?> map : yaml.getMapList("engagements")) {
            try {
                FamilyManager.Engagement e = new FamilyManager.Engagement(UUID.fromString(String.valueOf(map.get("groom"))),
                        UUID.fromString(String.valueOf(map.get("bride"))));
                e.setGroomChooses(!"bride".equals(String.valueOf(map.get("chooser"))));
                Object option = map.get("option");
                if (option != null) {
                    e.setOption(FamilyManager.MarriageOption.valueOf(String.valueOf(option)));
                }
                manager.loadEngagement(e);
            } catch (RuntimeException ex) {
                logger.warning("Verloving kon niet geladen worden: " + ex);
            }
        }
    }

    /** archive: lijst van momentopnames, nieuwste eerst */
    private void loadArchive(YamlConfiguration yaml, FamilyManager manager) {
        for (Map<?, ?> map : yaml.getMapList("archive")) {
            try {
                ArchivedFamily a = new ArchivedFamily(String.valueOf(map.get("id")), String.valueOf(map.get("reason")),
                        Instant.ofEpochMilli(((Number) map.get("archived")).longValue()), String.valueOf(map.get("name")),
                        String.valueOf(map.get("tag")), Instant.ofEpochMilli(((Number) map.get("created")).longValue()),
                        UUID.fromString(String.valueOf(map.get("owner"))), stringOrNull(map.get("crest")),
                        stringOrNull(map.get("kingdom")));
                Object members = map.get("members");
                if (members instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        UUID uuid = UUID.fromString(String.valueOf(e.getKey()));
                        Map<?, ?> member = e.getValue() instanceof Map<?, ?> mm ? mm : Map.of();
                        a.getMembers().put(uuid, String.valueOf(member.containsKey("name") ? member.get("name") : "?"));
                        FamilyRank rank = FamilyRank.parse(stringOrNull(member.get("rank")));
                        a.getRanks().put(uuid, rank == null ? FamilyRank.CHILD : rank);
                    }
                }
                if (map.get("lineage") instanceof List<?> lineage) {
                    lineage.forEach(l -> a.getLineage().add(String.valueOf(l)));
                }
                manager.loadArchived(a);
            } catch (RuntimeException ex) {
                logger.warning("Archief-regel kon niet geladen worden: " + ex);
            }
        }
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
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
            if (!tree.originsOf(uuid).isEmpty()) {
                yaml.set(path + ".origins", new ArrayList<>(tree.originsOf(uuid)));
            }
        }
        List<java.util.Map<String, Object>> engagements = new ArrayList<>();
        for (FamilyManager.Engagement e : manager.getEngagements()) {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("groom", e.getGroom().toString());
            map.put("bride", e.getBride().toString());
            map.put("chooser", e.isGroomChooses() ? "groom" : "bride");
            map.put("option", e.getOption().name());
            engagements.add(map);
        }
        if (!engagements.isEmpty()) {
            yaml.set("engagements", engagements);
        }
        List<Map<String, Object>> archive = new ArrayList<>();
        for (ArchivedFamily a : manager.getArchive()) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("reason", a.getReason());
            map.put("archived", a.getArchivedAt().toEpochMilli());
            map.put("name", a.getName());
            map.put("tag", a.getTag());
            map.put("created", a.getCreatedAt().toEpochMilli());
            map.put("owner", a.getOwner().toString());
            if (a.getKingdomName() != null) {
                map.put("kingdom", a.getKingdomName());
            }
            if (a.getCrest() != null) {
                map.put("crest", a.getCrest());
            }
            if (!a.getLineage().isEmpty()) {
                map.put("lineage", new ArrayList<>(a.getLineage()));
            }
            Map<String, Object> members = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, String> e : a.getMembers().entrySet()) {
                Map<String, Object> member = new java.util.LinkedHashMap<>();
                member.put("name", e.getValue());
                FamilyRank rank = a.getRanks().get(e.getKey());
                member.put("rank", (rank == null ? FamilyRank.CHILD : rank).id());
                members.put(e.getKey().toString(), member);
            }
            map.put("members", members);
            archive.add(map);
        }
        if (!archive.isEmpty()) {
            yaml.set("archive", archive);
        }
        for (Map.Entry<UUID, FamilyManager.Deceased> e : manager.getAllDeceased().entrySet()) {
            String path = "deceased." + e.getKey();
            FamilyManager.Deceased d = e.getValue();
            yaml.set(path + ".player", d.getPlayer().toString());
            yaml.set(path + ".name", d.getName());
            yaml.set(path + ".rank", d.getFormerRank().id());
            yaml.set(path + ".died", d.getDiedAt().toEpochMilli());
        }
        if (!manager.getFakes().isEmpty()) {
            List<String> fakes = new ArrayList<>();
            manager.getFakes().forEach(f -> fakes.add(f.toString()));
            yaml.set("fakes", fakes);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.severe("families.yml kon niet opgeslagen worden: " + ex);
        }
    }
}
