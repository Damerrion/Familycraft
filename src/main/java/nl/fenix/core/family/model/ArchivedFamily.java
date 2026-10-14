package nl.fenix.core.family.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Momentopname van een familie vlak voordat hij verdween of veranderde (opheffen, opgaan in een andere lijn,
 * fuseren). Een admin kan hem terugzetten met /family archive.
 */
public final class ArchivedFamily {

    private final String id;
    private final String reason;
    private final Instant archivedAt;
    private final String name;
    private final String tag;
    private final Instant createdAt;
    private final UUID owner;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Map<UUID, FamilyRank> ranks = new LinkedHashMap<>();
    private final String crest;
    private final String kingdomName;
    private final List<String> lineage = new ArrayList<>();

    public ArchivedFamily(String id, String reason, Instant archivedAt, String name, String tag, Instant createdAt,
                          UUID owner, String crest, String kingdomName) {
        this.id = id;
        this.reason = reason;
        this.archivedAt = archivedAt;
        this.name = name;
        this.tag = tag;
        this.createdAt = createdAt;
        this.owner = owner;
        this.crest = crest;
        this.kingdomName = kingdomName;
    }

    static ArchivedFamily of(Family family, String reason) {
        Instant now = Instant.now();
        ArchivedFamily a = new ArchivedFamily(family.getTag().toLowerCase(java.util.Locale.ROOT) + "-" + now.toEpochMilli(),
                reason, now, family.getName(), family.getTag(), family.getCreatedAt(), family.getOwner(),
                family.getCrest(), family.getKingdomName());
        for (Map.Entry<UUID, String> e : family.getMembers().entrySet()) {
            a.members.put(e.getKey(), e.getValue());
            a.ranks.put(e.getKey(), family.getRank(e.getKey()));
        }
        a.lineage.addAll(family.getLineage());
        return a;
    }

    public String getId() {
        return id;
    }

    /** disband, absorbed, fused */
    public String getReason() {
        return reason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public Map<UUID, FamilyRank> getRanks() {
        return ranks;
    }

    public String getCrest() {
        return crest;
    }

    public String getKingdomName() {
        return kingdomName;
    }

    public List<String> getLineage() {
        return lineage;
    }
}
