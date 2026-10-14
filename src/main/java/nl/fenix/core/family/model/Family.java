package nl.fenix.core.family.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Family {

    private volatile String name;
    private volatile String tag;
    private final Instant createdAt;
    private volatile UUID owner;
    /**
     * Kingdom van het familiehoofd bij de oprichting ("familie afstamming"). Blijft staan als het hoofd van
     * kingdom wisselt (gebruiker). Null = nog onbekend.
     */
    private volatile String kingdomName;
    /** uuid -> laatst bekende spelersnaam, in volgorde van toetreden (hoofd eerst) */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    /** rang per lid; het hoofd (owner) is altijd {@link FamilyRank#HEAD} */
    private final Map<UUID, FamilyRank> ranks = new ConcurrentHashMap<>();
    /** familiewapen: een banner (Paper-item als base64), null = standaard */
    private volatile String crest;
    /** namen van de families waar deze aftakking uit is ontstaan (leeg voor een gewone familie) */
    private final List<String> lineage = new ArrayList<>();
    /** aftakking met een te lange of bezette naam/tag: het hoofd moet een nieuwe naam en tag kiezen */
    private volatile boolean needsRename;

    public Family(String name, String tag, UUID owner, String ownerName, Instant createdAt) {
        this.name = name;
        this.tag = tag;
        this.owner = owner;
        this.createdAt = createdAt;
        this.members.put(owner, ownerName);
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    /** Alleen via {@link FamilyManager} (die houdt de index op tag bij). */
    void setNameAndTag(String name, String tag) {
        this.name = name;
        this.tag = tag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getOwner() {
        return owner;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    /** uuid -> naam. Leden toevoegen/verwijderen via {@link #addMember}/{@link #removeMember}. */
    public Map<UUID, String> getMembers() {
        return members;
    }

    public void addMember(UUID uuid, String name, FamilyRank rank) {
        members.put(uuid, name);
        if (rank != null && rank != FamilyRank.HEAD) {
            ranks.put(uuid, rank);
        }
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        ranks.remove(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public FamilyRank getRank(UUID uuid) {
        if (owner.equals(uuid)) {
            return FamilyRank.HEAD;
        }
        return ranks.getOrDefault(uuid, FamilyRank.CHILD);
    }

    /** Rang zetten zonder controles (laden / manager). HEAD gaat via {@link FamilyManager#setRank}. */
    public void putRank(UUID uuid, FamilyRank rank) {
        if (rank == null || rank == FamilyRank.HEAD) {
            ranks.remove(uuid);
        } else {
            ranks.put(uuid, rank);
        }
    }

    /** Leden met deze rang (het hoofd telt als HEAD). */
    public List<UUID> membersWithRank(FamilyRank rank) {
        List<UUID> result = new ArrayList<>();
        for (UUID uuid : members.keySet()) {
            if (getRank(uuid) == rank) {
                result.add(uuid);
            }
        }
        return result;
    }

    public String getKingdomName() {
        return kingdomName;
    }

    public void setKingdomName(String kingdomName) {
        this.kingdomName = kingdomName;
    }

    public String getOwnerName() {
        return members.get(owner);
    }

    public String getCrest() {
        return crest;
    }

    public void setCrest(String crest) {
        this.crest = crest;
    }

    public List<String> getLineage() {
        return lineage;
    }

    public boolean isBranch() {
        return !lineage.isEmpty();
    }

    public boolean needsRename() {
        return needsRename;
    }

    public void setNeedsRename(boolean needsRename) {
        this.needsRename = needsRename;
    }
}
