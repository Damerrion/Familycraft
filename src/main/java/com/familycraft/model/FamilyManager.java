package com.familycraft.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Bukkit independent family logic: validation, membership, invites. */
public class FamilyManager {

    /** Letters only (any language), with underscores acting as spaces in the name. No digits. */
    private static final Pattern NAME = Pattern.compile("[\\p{L}_]+");
    private static final Pattern TAG = Pattern.compile("\\p{L}+");

    private final int maxNameLength;
    private final int maxTagLength;
    private final int maxMembers;
    private final long inviteExpireMillis;

    // read from the async chat thread (placeholders, hover), so concurrent maps
    private final Map<String, Family> byTag = new ConcurrentHashMap<>();
    private final Map<UUID, Family> byMember = new ConcurrentHashMap<>();
    /** invited player -> invite */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    /** Test helper. */
    public interface ThrowingRunnable {
        void run() throws FamilyException;
    }

    public static class Invite {
        private final Family family;
        private final UUID inviter;
        private final long expiresAt;

        Invite(Family family, UUID inviter, long expiresAt) {
            this.family = family;
            this.inviter = inviter;
            this.expiresAt = expiresAt;
        }

        public Family getFamily() {
            return family;
        }

        public UUID getInviter() {
            return inviter;
        }
    }

    public FamilyManager(int maxNameLength, int maxTagLength, int maxMembers, long inviteExpireSeconds) {
        this.maxNameLength = maxNameLength;
        this.maxTagLength = maxTagLength;
        this.maxMembers = maxMembers;
        this.inviteExpireMillis = inviteExpireSeconds * 1000L;
    }

    // ---- lookup ----

    public Family getFamily(UUID player) {
        return byMember.get(player);
    }

    public Family getFamilyByTag(String tag) {
        return tag == null ? null : byTag.get(tag.toLowerCase(Locale.ROOT));
    }

    public Collection<Family> getFamilies() {
        return byTag.values();
    }

    // ---- validation ----

    public static String normalizeName(String raw) {
        return raw.replace('_', ' ').trim();
    }

    public void validateName(String raw) throws FamilyException {
        if (raw == null || raw.isEmpty() || raw.length() > maxNameLength || !NAME.matcher(raw).matches()
                || normalizeName(raw).isEmpty()) {
            throw new FamilyException("name-invalid");
        }
    }

    public void validateTag(String raw) throws FamilyException {
        if (raw == null || raw.isEmpty() || raw.length() > maxTagLength || !TAG.matcher(raw).matches()) {
            throw new FamilyException("tag-invalid");
        }
    }

    // ---- actions ----

    public Family create(UUID owner, String ownerName, String rawName, String rawTag) throws FamilyException {
        if (byMember.containsKey(owner)) {
            throw new FamilyException("already-in-family");
        }
        validateName(rawName);
        validateTag(rawTag);

        String name = normalizeName(rawName);
        for (Family f : byTag.values()) {
            if (f.getName().equalsIgnoreCase(name)) {
                throw new FamilyException("name-taken");
            }
        }
        if (byTag.containsKey(rawTag.toLowerCase(Locale.ROOT))) {
            throw new FamilyException("tag-taken");
        }

        Family family = new Family(name, rawTag, owner, ownerName, Instant.now());
        register(family);
        return family;
    }

    /** Adds an already built family (used when loading from disk). */
    public void register(Family family) {
        byTag.put(family.getTag().toLowerCase(Locale.ROOT), family);
        for (UUID member : family.getMembers().keySet()) {
            byMember.put(member, family);
        }
    }

    public Invite invite(Family family, UUID inviter, UUID target) throws FamilyException {
        if (!family.isOwner(inviter)) {
            throw new FamilyException("not-owner");
        }
        if (inviter.equals(target)) {
            throw new FamilyException("invite-self");
        }
        if (byMember.containsKey(target)) {
            throw new FamilyException("invite-target-in-family");
        }
        if (isFull(family)) {
            throw new FamilyException("family-full");
        }
        Invite existing = getInvite(target);
        if (existing != null && existing.family == family) {
            throw new FamilyException("invite-already-sent");
        }
        Invite invite = new Invite(family, inviter, System.currentTimeMillis() + inviteExpireMillis);
        invites.put(target, invite);
        return invite;
    }

    /** @return the pending, non expired invite or null */
    public Invite getInvite(UUID target) {
        Invite invite = invites.get(target);
        if (invite != null && invite.expiresAt < System.currentTimeMillis()) {
            invites.remove(target);
            return null;
        }
        if (invite != null && byTag.get(invite.family.getTag().toLowerCase(Locale.ROOT)) != invite.family) {
            invites.remove(target);
            return null;
        }
        return invite;
    }

    public Family accept(UUID player, String playerName) throws FamilyException {
        if (byMember.containsKey(player)) {
            throw new FamilyException("already-in-family");
        }
        Invite invite = getInvite(player);
        if (invite == null) {
            throw new FamilyException("no-invite");
        }
        if (isFull(invite.family)) {
            throw new FamilyException("family-full");
        }
        invites.remove(player);
        invite.family.getMembers().put(player, playerName);
        byMember.put(player, invite.family);
        return invite.family;
    }

    /** Declines the invite of the given player, returns it (or null if there was none). */
    public Invite decline(UUID player) {
        Invite invite = getInvite(player);
        invites.remove(player);
        return invite;
    }

    /** Removes every pending invite sent by this family. @return number removed */
    public int cancelInvitesOf(Family family) {
        int before = invites.size();
        invites.values().removeIf(i -> i.family == family);
        return before - invites.size();
    }

    public Family leave(UUID player) throws FamilyException {
        Family family = byMember.get(player);
        if (family == null) {
            throw new FamilyException("not-in-family");
        }
        if (family.isOwner(player)) {
            throw new FamilyException("owner-cannot-leave");
        }
        family.getMembers().remove(player);
        byMember.remove(player);
        return family;
    }

    public void kick(Family family, UUID actor, boolean bypass, UUID target) throws FamilyException {
        if (!bypass && !family.isOwner(actor)) {
            throw new FamilyException("not-owner");
        }
        if (!family.isMember(target)) {
            throw new FamilyException("kick-not-member");
        }
        if (family.isOwner(target)) {
            throw new FamilyException("kick-self");
        }
        family.getMembers().remove(target);
        byMember.remove(target);
    }

    public void disband(Family family) {
        byTag.remove(family.getTag().toLowerCase(Locale.ROOT));
        for (UUID member : family.getMembers().keySet()) {
            byMember.remove(member);
        }
        cancelInvitesOf(family);
    }

    public boolean isFull(Family family) {
        return maxMembers > 0 && family.getMembers().size() >= maxMembers;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public int getMaxTagLength() {
        return maxTagLength;
    }
}
