package nl.fenix.core.family.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Familielogica zonder Bukkit: validatie, leden, uitnodigingen, rangen, stamboom, huwelijken. */
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
    /** bruid -> huwelijksaanzoek */
    private final Map<UUID, Proposal> proposals = new ConcurrentHashMap<>();
    private final Genealogy genealogy = new Genealogy();
    /** max aantal leden per rang (0 = onbeperkt) */
    private final Map<FamilyRank, Integer> rankLimits = new EnumMap<>(FamilyRank.class);
    private long proposalExpireMillis = 5 * 60 * 1000L;
    /** vanaf zoveel families in de naam (A-B-C-D) moet een aftakking een nieuwe naam kiezen */
    private int renameAtParts = 4;

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
        rankLimits.put(FamilyRank.SUCCESSOR, 1);
        rankLimits.put(FamilyRank.SPARE_HEIR, 1);
    }

    public void setRankLimit(FamilyRank rank, int max) {
        rankLimits.put(rank, Math.max(0, max));
    }

    public void setProposalExpireSeconds(long seconds) {
        this.proposalExpireMillis = seconds * 1000L;
    }

    public void setRenameAtParts(int parts) {
        this.renameAtParts = Math.max(2, parts);
    }

    public Genealogy getGenealogy() {
        return genealogy;
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
        genealogy.setName(owner, ownerName);
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
        invite.family.addMember(player, playerName, FamilyRank.CHILD);
        byMember.put(player, invite.family);
        genealogy.setName(player, playerName);
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
        family.removeMember(player);
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
        family.removeMember(target);
        byMember.remove(target);
    }

    public void disband(Family family) {
        byTag.remove(family.getTag().toLowerCase(Locale.ROOT));
        for (UUID member : family.getMembers().keySet()) {
            byMember.remove(member);
        }
        cancelInvitesOf(family);
        proposals.values().removeIf(p -> p.groomFamily == family || p.brideFamily == family);
    }

    private boolean isRegistered(Family family) {
        return byTag.get(family.getTag().toLowerCase(Locale.ROOT)) == family;
    }

    private void requireOwner(Family family, UUID actor, boolean bypass) throws FamilyException {
        if (!bypass && !family.isOwner(actor)) {
            throw new FamilyException("not-owner");
        }
    }

    // ---- rangen ----

    /**
     * Geeft een lid een rang. Family-head geven = het hoofd overdragen: het oude hoofd krijgt de oude rang van
     * het nieuwe hoofd.
     */
    public void setRank(Family family, UUID actor, boolean bypass, UUID target, FamilyRank rank) throws FamilyException {
        requireOwner(family, actor, bypass);
        if (!family.isMember(target)) {
            throw new FamilyException("rank-not-member");
        }
        FamilyRank current = family.getRank(target);
        if (current == rank) {
            throw new FamilyException("rank-already");
        }
        if (rank == FamilyRank.HEAD) {
            UUID oldHead = family.getOwner();
            family.putRank(target, null);
            family.setOwner(target);
            family.putRank(oldHead, current);
            return;
        }
        if (family.isOwner(target)) {
            throw new FamilyException("rank-head-change");
        }
        int max = rankLimits.getOrDefault(rank, 0);
        if (max > 0 && family.membersWithRank(rank).size() >= max) {
            throw new FamilyException("rank-full");
        }
        family.putRank(target, rank);
    }

    /** Opvolger van een vertrekkend hoofd: eerst de Successor, dan de Spare-Heir. Null als er geen is. */
    public UUID findHeir(Family family, UUID leaving) {
        for (FamilyRank rank : new FamilyRank[]{FamilyRank.SUCCESSOR, FamilyRank.SPARE_HEIR}) {
            for (UUID uuid : family.membersWithRank(rank)) {
                if (!uuid.equals(leaving)) {
                    return uuid;
                }
            }
        }
        return null;
    }

    // ---- stamboom ----

    /** Koppel twee leden van dezelfde familie (bij PARENT is {@code a} de ouder van {@code b}). */
    public void link(Family family, UUID actor, boolean bypass, Genealogy.Relation relation, UUID a, UUID b)
            throws FamilyException {
        requireOwner(family, actor, bypass);
        if (!family.isMember(a) || !family.isMember(b)) {
            throw new FamilyException("tree-not-member");
        }
        genealogy.setName(a, family.getMembers().get(a));
        genealogy.setName(b, family.getMembers().get(b));
        genealogy.link(relation, a, b);
    }

    public void unlink(Family family, UUID actor, boolean bypass, UUID a, UUID b) throws FamilyException {
        requireOwner(family, actor, bypass);
        if (!family.isMember(a) && !family.isMember(b)) {
            throw new FamilyException("tree-not-member");
        }
        if (!genealogy.unlink(a, b)) {
            throw new FamilyException("tree-no-relation");
        }
    }

    // ---- familiewapen en naam ----

    public void setCrest(Family family, UUID actor, boolean bypass, String crest) throws FamilyException {
        requireOwner(family, actor, bypass);
        family.setCrest(crest);
    }

    /** Nieuwe naam en tag, alleen voor een aftakking die dat moet (of met bypass). */
    public void rename(Family family, UUID actor, boolean bypass, String rawName, String rawTag) throws FamilyException {
        requireOwner(family, actor, bypass);
        if (!bypass && !family.needsRename()) {
            throw new FamilyException("rename-not-needed");
        }
        validateName(rawName);
        validateTag(rawTag);
        String name = normalizeName(rawName);
        for (Family f : byTag.values()) {
            if (f != family && f.getName().equalsIgnoreCase(name)) {
                throw new FamilyException("name-taken");
            }
        }
        Family other = getFamilyByTag(rawTag);
        if (other != null && other != family) {
            throw new FamilyException("tag-taken");
        }
        byTag.remove(family.getTag().toLowerCase(Locale.ROOT));
        family.setNameAndTag(name, rawTag);
        family.setNeedsRename(false);
        byTag.put(rawTag.toLowerCase(Locale.ROOT), family);
    }

    // ---- huwelijken ----

    public static class Proposal {
        private final Family groomFamily;
        private final Family brideFamily;
        private final UUID groom;
        private final UUID bride;
        private final long expiresAt;

        Proposal(Family groomFamily, Family brideFamily, UUID groom, UUID bride, long expiresAt) {
            this.groomFamily = groomFamily;
            this.brideFamily = brideFamily;
            this.groom = groom;
            this.bride = bride;
            this.expiresAt = expiresAt;
        }

        public Family getGroomFamily() {
            return groomFamily;
        }

        public Family getBrideFamily() {
            return brideFamily;
        }

        public UUID getGroom() {
            return groom;
        }

        public UUID getBride() {
            return bride;
        }
    }

    public static class Marriage {
        private final Family branch;
        private final Family groomFamily;
        private final Family brideFamily;
        private final UUID newGroomFamilyHead;
        private final UUID newBrideFamilyHead;

        Marriage(Family branch, Family groomFamily, Family brideFamily, UUID newGroomFamilyHead, UUID newBrideFamilyHead) {
            this.branch = branch;
            this.groomFamily = groomFamily;
            this.brideFamily = brideFamily;
            this.newGroomFamilyHead = newGroomFamilyHead;
            this.newBrideFamilyHead = newBrideFamilyHead;
        }

        public Family getBranch() {
            return branch;
        }

        public Family getGroomFamily() {
            return groomFamily;
        }

        public Family getBrideFamily() {
            return brideFamily;
        }

        /** nieuw hoofd van de familie van de bruidegom als die het hoofd was, anders null */
        public UUID getNewGroomFamilyHead() {
            return newGroomFamilyHead;
        }

        public UUID getNewBrideFamilyHead() {
            return newBrideFamilyHead;
        }
    }

    /** Het hoofd van de familie van de bruidegom vraagt het hoofd van de familie van de bruid. */
    public Proposal propose(UUID actor, boolean bypass, UUID groom, UUID bride) throws FamilyException {
        Family groomFamily = byMember.get(groom);
        Family brideFamily = byMember.get(bride);
        checkMarriage(groomFamily, brideFamily, groom, bride);
        requireOwner(groomFamily, actor, bypass);
        Proposal existing = getProposal(bride);
        if (existing != null && existing.groom.equals(groom)) {
            throw new FamilyException("marriage-already-proposed");
        }
        Proposal proposal = new Proposal(groomFamily, brideFamily, groom, bride,
                System.currentTimeMillis() + proposalExpireMillis);
        proposals.put(bride, proposal);
        return proposal;
    }

    private void checkMarriage(Family groomFamily, Family brideFamily, UUID groom, UUID bride) throws FamilyException {
        if (groom.equals(bride)) {
            throw new FamilyException("marriage-self");
        }
        if (groomFamily == null) {
            throw new FamilyException("marriage-groom-no-family");
        }
        if (brideFamily == null) {
            throw new FamilyException("marriage-bride-no-family");
        }
        if (groomFamily == brideFamily) {
            throw new FamilyException("marriage-same-family");
        }
        if (genealogy.partnerOf(groom) != null || genealogy.partnerOf(bride) != null) {
            throw new FamilyException("marriage-already-married");
        }
        if (groomFamily.isOwner(groom) && findHeir(groomFamily, groom) == null) {
            throw new FamilyException("marriage-no-successor");
        }
        if (brideFamily.isOwner(bride) && findHeir(brideFamily, bride) == null) {
            throw new FamilyException("marriage-no-successor");
        }
    }

    /** Openstaand (niet verlopen) aanzoek voor deze bruid, of null. */
    public Proposal getProposal(UUID bride) {
        Proposal p = proposals.get(bride);
        if (p != null && (p.expiresAt < System.currentTimeMillis() || !isRegistered(p.groomFamily)
                || !isRegistered(p.brideFamily))) {
            proposals.remove(bride);
            return null;
        }
        return p;
    }

    /** Aanzoek waar deze speler (als hoofd van de familie van de bruid) op moet antwoorden, of null. */
    public Proposal getProposalFor(UUID brideFamilyHead) {
        for (UUID bride : new ArrayList<>(proposals.keySet())) {
            Proposal p = getProposal(bride);
            if (p != null && p.brideFamily.isOwner(brideFamilyHead)) {
                return p;
            }
        }
        return null;
    }

    public Proposal deny(UUID brideFamilyHead) throws FamilyException {
        Proposal p = getProposalFor(brideFamilyHead);
        if (p == null) {
            throw new FamilyException("marriage-no-proposal");
        }
        proposals.remove(p.bride);
        return p;
    }

    /** Het hoofd van de familie van de bruid accepteert: er ontstaat een aftakking met het bruidspaar. */
    public Marriage acceptMarriage(UUID brideFamilyHead) throws FamilyException {
        Proposal p = getProposalFor(brideFamilyHead);
        if (p == null) {
            throw new FamilyException("marriage-no-proposal");
        }
        Marriage marriage = marry(p.groom, p.bride);
        proposals.remove(p.bride);
        return marriage;
    }

    /**
     * Bruidegom en bruid verlaten hun familie en vormen samen een aftakking: naam "A-B", tag uit de beginletters,
     * bruidegom wordt hoofd, bruid Consort. Was een van beiden hoofd, dan neemt de Successor (of Spare-Heir) het over.
     */
    public Marriage marry(UUID groom, UUID bride) throws FamilyException {
        Family groomFamily = byMember.get(groom);
        Family brideFamily = byMember.get(bride);
        checkMarriage(groomFamily, brideFamily, groom, bride);

        String groomName = groomFamily.getMembers().get(groom);
        String brideName = brideFamily.getMembers().get(bride);
        UUID newGroomHead = leaveForMarriage(groomFamily, groom);
        UUID newBrideHead = leaveForMarriage(brideFamily, bride);

        MergedName merged = merge(groomFamily.getName(), brideFamily.getName());
        String name = merged.name();
        String tag = merged.tag();
        // een aftakking-tag mag langer zijn dan max-tag-length (DAL, DABL...); pas bij A-B-C-D een nieuwe naam
        boolean rename = merged.parts() >= renameAtParts || isNameTaken(name);
        if (getFamilyByTag(tag) != null) {
            rename = true;
            tag = uniqueTag(tag);
        }

        Family branch = new Family(name, tag, groom, groomName, Instant.now());
        branch.addMember(bride, brideName, FamilyRank.CONSORT);
        branch.getLineage().add(groomFamily.getName());
        branch.getLineage().add(brideFamily.getName());
        branch.setKingdomName(groomFamily.getKingdomName());
        branch.setCrest(groomFamily.getCrest());
        branch.setNeedsRename(rename);
        register(branch);

        genealogy.setName(groom, groomName);
        genealogy.setName(bride, brideName);
        genealogy.linkPartner(groom, bride);
        invites.remove(groom);
        invites.remove(bride);
        return new Marriage(branch, groomFamily, brideFamily, newGroomHead, newBrideHead);
    }

    /** @return het nieuwe hoofd als {@code player} het hoofd was, anders null */
    private UUID leaveForMarriage(Family family, UUID player) {
        UUID newHead = null;
        if (family.isOwner(player)) {
            newHead = findHeir(family, player);
            family.putRank(newHead, null);
            family.setOwner(newHead);
        }
        family.removeMember(player);
        byMember.remove(player);
        return newHead;
    }

    private boolean isNameTaken(String name) {
        for (Family f : byTag.values()) {
            if (f.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String uniqueTag(String tag) {
        for (char c = 'A'; c <= 'Z'; c++) {
            if (getFamilyByTag(tag + c) == null) {
                return tag + c;
            }
        }
        String candidate = tag;
        while (getFamilyByTag(candidate) != null) {
            candidate += "X";
        }
        return candidate;
    }

    // ---- namen samenvoegen ----

    /** Resultaat van het samenvoegen van twee familienamen. */
    public static final class MergedName {
        private final String name;
        private final String tag;
        private final int parts;

        MergedName(String name, String tag, int parts) {
            this.name = name;
            this.tag = tag;
            this.parts = parts;
        }

        public String name() {
            return name;
        }

        public String tag() {
            return tag;
        }

        /** aantal families in de naam (A-B-C-D = 4) */
        public int parts() {
            return parts;
        }
    }

    /**
     * Gedeeld achtervoegsel: "Dokker Lijn" + "Adamantie Lijn" -> "Dokker-Adamantie Lijn", tag DAL.
     * Anders: "Rheanos Decendants" + "Triton Wachters" -> "Rheanos Decendants-Triton Wachters", tag RT.
     */
    public static MergedName merge(String groomFamily, String brideFamily) {
        String a = lastWord(groomFamily);
        String b = lastWord(brideFamily);
        List<String> parts = new ArrayList<>();
        String name;
        String suffix = null;
        if (a != null && a.equalsIgnoreCase(b)) {
            suffix = a;
            parts.addAll(withoutSuffix(groomFamily));
            parts.addAll(withoutSuffix(brideFamily));
            name = String.join("-", parts) + " " + suffix;
        } else {
            parts.addAll(split(groomFamily));
            parts.addAll(split(brideFamily));
            name = String.join("-", parts);
        }
        StringBuilder tag = new StringBuilder();
        for (String part : parts) {
            tag.appendCodePoint(part.codePointAt(0));
        }
        if (suffix != null) {
            tag.appendCodePoint(suffix.codePointAt(0));
        }
        return new MergedName(name, tag.toString().toUpperCase(Locale.ROOT), parts.size());
    }

    private static List<String> split(String name) {
        List<String> parts = new ArrayList<>();
        for (String part : name.split("-")) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    /** Laatste woord van het laatste deel, alleen als dat deel uit meerdere woorden bestaat ("Adamantie Lijn"). */
    private static String lastWord(String name) {
        List<String> parts = split(name);
        if (parts.isEmpty()) {
            return null;
        }
        String[] words = parts.get(parts.size() - 1).split("\\s+");
        return words.length >= 2 ? words[words.length - 1] : null;
    }

    private static List<String> withoutSuffix(String name) {
        List<String> parts = split(name);
        String last = parts.remove(parts.size() - 1);
        parts.add(last.substring(0, last.lastIndexOf(' ')).trim());
        return parts;
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
