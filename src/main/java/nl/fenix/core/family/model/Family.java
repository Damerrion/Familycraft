package nl.fenix.core.family.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Family {

    private final String name;
    private final String tag;
    private final Instant createdAt;
    private UUID owner;
    /** Name of the owner's kingdom when last seen ("familie afstamming"), null if none/unknown. */
    private volatile String kingdomName;
    /** uuid -> last known player name, insertion ordered (owner first) */
    private final Map<UUID, String> members = new LinkedHashMap<>();

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
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
}
