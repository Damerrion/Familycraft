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

    private int maxNameLength;
    private int maxTagLength;
    private int maxMembers;
    private long inviteExpireMillis;

    // read from the async chat thread (placeholders, hover), so concurrent maps
    private final Map<String, Family> byTag = new ConcurrentHashMap<>();
    private final Map<UUID, Family> byMember = new ConcurrentHashMap<>();
    /** invited player -> invite */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
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

    /** Instellingen opnieuw zetten (/fenixcore reload). */
    public void applySettings(int maxNameLength, int maxTagLength, int maxMembers, long inviteExpireSeconds) {
        this.maxNameLength = maxNameLength;
        this.maxTagLength = maxTagLength;
        this.maxMembers = maxMembers;
        this.inviteExpireMillis = inviteExpireSeconds * 1000L;
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

    /**
     * Je eigen relatie met iemand verbreken. Zit die ander in een andere familie (bv. na een nieuwe familie of een
     * huwelijk), dan mag dat altijd; binnen dezelfde familie beslist het hoofd.
     */
    public void unlinkSelf(UUID actor, UUID other) throws FamilyException {
        Family own = byMember.get(actor);
        if (own != null && own == byMember.get(other) && !own.isOwner(actor)) {
            throw new FamilyException("tree-unlink-ask-head");
        }
        if (!genealogy.unlink(actor, other)) {
            throw new FamilyException("tree-no-relation");
        }
    }

    /** Relatie tussen een lid ({@code a} of {@code b}) en iemand anders verbreken; die ander mag in een andere familie zitten. */
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

    // ---- verloving en huwelijk ----

    /** Wat er met de lijnen gebeurt (bij een gewoon huwelijk kiest A, de aangewezen partner). */
    public enum MarriageOption {
        /** lijn A (van de kiezer) gaat op in lijn B (alleen hoofd/Opvolger van A) */
        ABSORB_LINE,
        /** andersom: lijn B (van de partner) gaat op in lijn A (alleen als de partner hoofd/Opvolger van B is) */
        ABSORB_PARTNER_LINE,
        /** alleen A gaat over naar lijn B, beide lijnen gaan verder */
        JOIN_PERSON,
        /** andersom: alleen de partner gaat over naar lijn A, beide lijnen gaan verder */
        JOIN_PARTNER,
        /** het bruidspaar begint samen een nieuwe lijn C (nieuwe naam kiezen); A en B gaan verder */
        NEW_LINE,
        /** het bruidspaar vormt een aftakking "A-B"; A en B gaan verder */
        BRANCH
    }

    /** Soort huwelijk, bepaald door de rangen van het bruidspaar. */
    public enum MarriageType {
        /** zelfde familie: alleen partners koppelen */
        SAME_FAMILY,
        /** hoofd + hoofd: de families fuseren automatisch tot "A-B" */
        HEADS,
        /** hoofd + Opvolger: de Opvolger gaat over naar de familie van het hoofd */
        HEAD_SUCCESSOR,
        /** de rest: A kiest een {@link MarriageOption} */
        CHOICE
    }

    /** Een verloofd paar (/enlope). Rollen, wie kiest en de keuze stellen ze in het trouwmenu in. */
    public static class Engagement {
        private UUID groom;
        private UUID bride;
        private boolean groomChooses = true;
        private MarriageOption option = MarriageOption.BRANCH;

        public Engagement(UUID groom, UUID bride) {
            this.groom = groom;
            this.bride = bride;
        }

        public UUID getGroom() {
            return groom;
        }

        public UUID getBride() {
            return bride;
        }

        /** De partner die de keuze maakt (A). */
        public UUID getChooser() {
            return groomChooses ? groom : bride;
        }

        public boolean isGroomChooses() {
            return groomChooses;
        }

        public void setGroomChooses(boolean groomChooses) {
            this.groomChooses = groomChooses;
        }

        public MarriageOption getOption() {
            return option;
        }

        public void setOption(MarriageOption option) {
            this.option = option;
        }

        public boolean involves(UUID player) {
            return groom.equals(player) || bride.equals(player);
        }

        public UUID partnerOf(UUID player) {
            return groom.equals(player) ? bride : groom;
        }

        public void swapRoles() {
            UUID g = groom;
            groom = bride;
            bride = g;
            groomChooses = !groomChooses; // dezelfde persoon blijft kiezen
        }
    }

    /** Resultaat van een trouwerij. */
    public static class Wedding {
        private final MarriageType type;
        private final MarriageOption option;
        private final Family family;
        private final List<Family> removed;
        private final Map<Family, UUID> newHeads;

        Wedding(MarriageType type, MarriageOption option, Family family, List<Family> removed, Map<Family, UUID> newHeads) {
            this.type = type;
            this.option = option;
            this.family = family;
            this.removed = removed;
            this.newHeads = newHeads;
        }

        public MarriageType getType() {
            return type;
        }

        /** null als er niets te kiezen viel */
        public MarriageOption getOption() {
            return option;
        }

        /** de familie waar het bruidspaar nu in zit */
        public Family getFamily() {
            return family;
        }

        /** families die zijn opgegaan in een andere (bestaan niet meer) */
        public List<Family> getRemoved() {
            return removed;
        }

        /** families die een nieuw hoofd kregen omdat het oude vertrok */
        public Map<Family, UUID> getNewHeads() {
            return newHeads;
        }
    }

    /** speler -> verloving (beide partners wijzen naar hetzelfde object) */
    private final Map<UUID, Engagement> engagements = new ConcurrentHashMap<>();
    /** aanzoek: ontvanger -> aanvrager */
    private final Map<UUID, UUID> engagementRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> engagementRequestExpiry = new ConcurrentHashMap<>();

    public Engagement getEngagement(UUID player) {
        return engagements.get(player);
    }

    public Collection<Engagement> getEngagements() {
        return new java.util.LinkedHashSet<>(engagements.values());
    }

    /** Laden uit families.yml. */
    public void loadEngagement(Engagement engagement) {
        engagements.put(engagement.groom, engagement);
        engagements.put(engagement.bride, engagement);
    }

    /** /enlope &lt;speler&gt;: vraag iemand ten huwelijk (verloving). */
    public void requestEngagement(UUID from, UUID to) throws FamilyException {
        if (from.equals(to)) {
            throw new FamilyException("marriage-self");
        }
        if (genealogy.isBloodRelated(from, to)) {
            throw new FamilyException("marriage-related");
        }
        if (byMember.get(from) == null || byMember.get(to) == null) {
            throw new FamilyException("engage-no-family");
        }
        if (genealogy.partnerOf(from) != null || genealogy.partnerOf(to) != null) {
            throw new FamilyException("marriage-already-married");
        }
        if (engagements.containsKey(from) || engagements.containsKey(to)) {
            throw new FamilyException("engage-already-engaged");
        }
        engagementRequests.put(to, from);
        engagementRequestExpiry.put(to, System.currentTimeMillis() + proposalExpireMillis);
    }

    /** Wie heeft deze speler gevraagd (niet verlopen), of null. */
    public UUID getEngagementRequest(UUID to) {
        Long until = engagementRequestExpiry.get(to);
        if (until == null || until < System.currentTimeMillis()) {
            engagementRequests.remove(to);
            engagementRequestExpiry.remove(to);
            return null;
        }
        return engagementRequests.get(to);
    }

    /** Ja zeggen: de aanvrager wordt (voorlopig) bruidegom; dat kan in het trouwmenu gewisseld worden. */
    public Engagement acceptEngagement(UUID to) throws FamilyException {
        UUID from = getEngagementRequest(to);
        if (from == null) {
            throw new FamilyException("engage-no-request");
        }
        engagementRequests.remove(to);
        engagementRequestExpiry.remove(to);
        if (engagements.containsKey(from) || engagements.containsKey(to)) {
            throw new FamilyException("engage-already-engaged");
        }
        Engagement engagement = new Engagement(from, to);
        loadEngagement(engagement);
        return engagement;
    }

    public UUID denyEngagement(UUID to) throws FamilyException {
        UUID from = getEngagementRequest(to);
        if (from == null) {
            throw new FamilyException("engage-no-request");
        }
        engagementRequests.remove(to);
        engagementRequestExpiry.remove(to);
        return from;
    }

    public Engagement cancelEngagement(UUID player) throws FamilyException {
        Engagement engagement = engagements.get(player);
        if (engagement == null) {
            throw new FamilyException("engage-not-engaged");
        }
        engagements.remove(engagement.groom);
        engagements.remove(engagement.bride);
        return engagement;
    }

    public MarriageType typeOf(Engagement e) {
        Family gf = byMember.get(e.groom);
        Family bf = byMember.get(e.bride);
        if (gf != null && gf == bf) {
            return MarriageType.SAME_FAMILY;
        }
        boolean groomHead = gf != null && gf.isOwner(e.groom);
        boolean brideHead = bf != null && bf.isOwner(e.bride);
        if (groomHead && brideHead) {
            return MarriageType.HEADS;
        }
        if ((groomHead && bf != null && bf.getRank(e.bride) == FamilyRank.SUCCESSOR)
                || (brideHead && gf != null && gf.getRank(e.groom) == FamilyRank.SUCCESSOR)) {
            return MarriageType.HEAD_SUCCESSOR;
        }
        return MarriageType.CHOICE;
    }

    /** Mag de kiezer deze optie kiezen? Hele lijn meenemen kan alleen het hoofd of de Opvolger van lijn A. */
    public boolean isAllowed(Engagement e, MarriageOption option) {
        if (option == MarriageOption.ABSORB_LINE) {
            return isHeir(e.getChooser());
        }
        if (option == MarriageOption.ABSORB_PARTNER_LINE) {
            // een hele lijn meenemen kan alleen via het hoofd of de Opvolger van die lijn
            return isHeir(e.partnerOf(e.getChooser()));
        }
        return true;
    }

    /** Hoofd of Opvolger van zijn/haar eigen lijn. */
    private boolean isHeir(UUID player) {
        Family own = byMember.get(player);
        FamilyRank rank = own == null ? null : own.getRank(player);
        return rank == FamilyRank.HEAD || rank == FamilyRank.SUCCESSOR;
    }

    /** Alleen de kiezer (A) kiest de optie. */
    public void chooseOption(UUID actor, boolean bypass, MarriageOption option) throws FamilyException {
        Engagement e = engagements.get(actor);
        if (e == null || (!bypass && !e.getChooser().equals(actor))) {
            throw new FamilyException("wedding-not-chooser");
        }
        if (!isAllowed(e, option)) {
            throw new FamilyException("wedding-option-not-allowed");
        }
        e.setOption(option);
    }

    /**
     * Het hoofd van de familie van de bruid start de trouwerij. Wat er gebeurt hangt af van {@link #typeOf}:
     * hoofd + hoofd fuseert, hoofd + Opvolger laat de Opvolger overgaan, anders de keuze van A.
     */
    public Wedding startWedding(UUID actor, boolean bypass, Engagement e) throws FamilyException {
        Family gf = byMember.get(e.groom);
        Family bf = byMember.get(e.bride);
        if (gf == null || bf == null) {
            throw new FamilyException("engage-no-family");
        }
        if (!bypass && !bf.isOwner(actor)) {
            throw new FamilyException("wedding-not-bride-head");
        }
        if (genealogy.partnerOf(e.groom) != null || genealogy.partnerOf(e.bride) != null) {
            throw new FamilyException("marriage-already-married");
        }
        // de stamboom kan na de verloving nog veranderd zijn
        if (genealogy.isBloodRelated(e.groom, e.bride)) {
            throw new FamilyException("marriage-related");
        }
        MarriageType type = typeOf(e);
        Wedding wedding;
        switch (type) {
            case SAME_FAMILY:
                wedding = new Wedding(type, null, gf, List.of(), Map.of());
                break;
            case HEADS:
                wedding = new Wedding(type, null, fuse(gf, bf, e.bride), List.of(bf), Map.of());
                break;
            case HEAD_SUCCESSOR: {
                UUID heir = gf.isOwner(e.groom) ? e.bride : e.groom;
                Family headFamily = gf.isOwner(e.groom) ? gf : bf;
                Family heirFamily = headFamily == gf ? bf : gf;
                movePerson(heir, heirFamily, headFamily, FamilyRank.CONSORT);
                wedding = new Wedding(type, null, headFamily, List.of(), Map.of());
                break;
            }
            default:
                wedding = choice(e);
        }
        genealogy.setName(e.groom, nameOf(e.groom));
        genealogy.setName(e.bride, nameOf(e.bride));
        if (genealogy.partnerOf(e.groom) == null) { // de aftakking (marry) koppelt ze zelf al
            genealogy.linkPartner(e.groom, e.bride);
        }
        engagements.remove(e.groom);
        engagements.remove(e.bride);
        return wedding;
    }

    private String nameOf(UUID player) {
        Family f = byMember.get(player);
        return f == null ? null : f.getMembers().get(player);
    }

    private Wedding choice(Engagement e) throws FamilyException {
        UUID a = e.getChooser();
        UUID b = e.partnerOf(a);
        Family lineA = byMember.get(a);
        Family lineB = byMember.get(b);
        MarriageOption option = e.getOption();
        if (!isAllowed(e, option)) {
            throw new FamilyException("wedding-option-not-allowed");
        }
        switch (option) {
            case ABSORB_LINE: {
                FamilyRank spouseRank = spouseRank(lineB.getRank(b));
                absorb(lineA, lineB, a, spouseRank);
                return new Wedding(MarriageType.CHOICE, option, lineB, List.of(lineA), Map.of());
            }
            case ABSORB_PARTNER_LINE: {
                FamilyRank spouseRank = spouseRank(lineA.getRank(a));
                absorb(lineB, lineA, b, spouseRank);
                return new Wedding(MarriageType.CHOICE, option, lineA, List.of(lineB), Map.of());
            }
            case JOIN_PARTNER: {
                UUID newHead = lineB.isOwner(b) ? findHeir(lineB, b) : null;
                if (lineB.isOwner(b) && newHead == null) {
                    throw new FamilyException("marriage-no-successor");
                }
                movePerson(b, lineB, lineA, spouseRank(lineA.getRank(a)));
                return new Wedding(MarriageType.CHOICE, option, lineA, List.of(),
                        newHead == null ? Map.of() : Map.of(lineB, newHead));
            }
            case JOIN_PERSON: {
                UUID newHead = lineA.isOwner(a) ? findHeir(lineA, a) : null;
                if (lineA.isOwner(a) && newHead == null) {
                    throw new FamilyException("marriage-no-successor");
                }
                movePerson(a, lineA, lineB, spouseRank(lineB.getRank(b)));
                return new Wedding(MarriageType.CHOICE, option, lineB, List.of(),
                        newHead == null ? Map.of() : Map.of(lineA, newHead));
            }
            case NEW_LINE: {
                // alleen het bruidspaar, zoals een aftakking, maar het hoofd kiest meteen een eigen naam
                Marriage m = marry(e.groom, e.bride);
                m.branch.setNeedsRename(true);
                return new Wedding(MarriageType.CHOICE, option, m.branch, List.of(), heads(m));
            }
            default: {
                Marriage m = marry(e.groom, e.bride);
                return new Wedding(MarriageType.CHOICE, option, m.branch, List.of(), heads(m));
            }
        }
    }

    private static Map<Family, UUID> heads(Marriage m) {
        Map<Family, UUID> heads = new java.util.HashMap<>();
        if (m.newGroomFamilyHead != null) {
            heads.put(m.groomFamily, m.newGroomFamilyHead);
        }
        if (m.newBrideFamilyHead != null) {
            heads.put(m.brideFamily, m.newBrideFamilyHead);
        }
        return heads;
    }

    /** Rang van de aangetrouwde partner: Gemaal bij een hoofd, Gemaal van de Erfgenaam bij een erfgenaam. */
    private static FamilyRank spouseRank(FamilyRank partnerRank) {
        if (partnerRank == FamilyRank.HEAD) {
            return FamilyRank.CONSORT;
        }
        if (partnerRank == FamilyRank.SUCCESSOR || partnerRank == FamilyRank.SPARE_HEIR) {
            return FamilyRank.HEIRS_CONSORT;
        }
        return FamilyRank.CHILD;
    }

    /** Eén persoon gaat van familie {@code from} naar {@code to}. Was het de Opvolger: Reserve-erfgenaam schuift door. */
    private void movePerson(UUID player, Family from, Family to, FamilyRank rank) {
        String name = from.getMembers().get(player);
        FamilyRank old = from.getRank(player);
        if (from.isOwner(player)) {
            UUID heir = findHeir(from, player);
            from.putRank(heir, null);
            from.setOwner(heir);
        } else if (old == FamilyRank.SUCCESSOR) {
            for (UUID spare : from.membersWithRank(FamilyRank.SPARE_HEIR)) {
                from.putRank(spare, FamilyRank.SUCCESSOR);
                break;
            }
        }
        from.removeMember(player);
        genealogy.addOrigin(player, from.getName());
        to.addMember(player, name, rank);
        byMember.put(player, to);
        invites.remove(player);
    }

    /**
     * Hoofd + hoofd: alle leden van de familie van de bruid gaan naar die van de bruidegom, die "A-B" gaat heten.
     * Bruid = Gemaal, Opvolger/Reserve-erfgenaam van de bruid worden Kind van het Huis, de rest houdt zijn rang.
     */
    private Family fuse(Family groomFamily, Family brideFamily, UUID bride) {
        MergedName merged = merge(groomFamily.getName(), brideFamily.getName());
        String oldGroomName = groomFamily.getName();
        for (UUID member : new ArrayList<>(groomFamily.getMembers().keySet())) {
            genealogy.addOrigin(member, oldGroomName);
        }
        for (UUID member : new ArrayList<>(brideFamily.getMembers().keySet())) {
            FamilyRank rank = brideFamily.getRank(member);
            if (member.equals(bride)) {
                rank = FamilyRank.CONSORT;
            } else if (rank == FamilyRank.HEAD || rank == FamilyRank.SUCCESSOR || rank == FamilyRank.SPARE_HEIR) {
                rank = FamilyRank.CHILD;
            }
            genealogy.addOrigin(member, brideFamily.getName());
            groomFamily.addMember(member, brideFamily.getMembers().get(member), rank);
            byMember.put(member, groomFamily);
        }
        removeFamily(brideFamily);
        groomFamily.getLineage().add(oldGroomName);
        groomFamily.getLineage().add(brideFamily.getName());
        renameMerged(groomFamily, merged);
        return groomFamily;
    }

    /** Lijn A gaat op in lijn B: iedereen van A wordt lid van B (kiezer = partner-rang, de rest Kind van het Huis). */
    private void absorb(Family lineA, Family lineB, UUID chooser, FamilyRank chooserRank) {
        for (UUID member : new ArrayList<>(lineA.getMembers().keySet())) {
            genealogy.addOrigin(member, lineA.getName());
            lineB.addMember(member, lineA.getMembers().get(member), member.equals(chooser) ? chooserRank : FamilyRank.CHILD);
            byMember.put(member, lineB);
        }
        removeFamily(lineA);
        lineB.getLineage().add(lineA.getName());
    }

    /** Familie weghalen zonder de leden los te koppelen (die zijn al verhuisd). */
    private void removeFamily(Family family) {
        byTag.remove(family.getTag().toLowerCase(Locale.ROOT));
        cancelInvitesOf(family);
    }

    /** Samengestelde naam + tag zetten; bij A-B-C-D of een bezette naam/tag moet het hoofd een nieuwe naam kiezen. */
    private void renameMerged(Family family, MergedName merged) {
        byTag.remove(family.getTag().toLowerCase(Locale.ROOT));
        boolean rename = merged.parts() >= renameAtParts || isNameTaken(merged.name());
        String tag = merged.tag();
        if (getFamilyByTag(tag) != null) {
            rename = true;
            tag = uniqueTag(tag);
        }
        family.setNameAndTag(merged.name(), tag);
        family.setNeedsRename(family.needsRename() || rename);
        byTag.put(tag.toLowerCase(Locale.ROOT), family);
    }

    // ---- aftakking (optie BRANCH en /family marriage force) ----

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

        public UUID getNewGroomFamilyHead() {
            return newGroomFamilyHead;
        }

        public UUID getNewBrideFamilyHead() {
            return newBrideFamilyHead;
        }
    }

    private void checkMarriage(Family groomFamily, Family brideFamily, UUID groom, UUID bride) throws FamilyException {
        if (groom.equals(bride)) {
            throw new FamilyException("marriage-self");
        }
        if (genealogy.isBloodRelated(groom, bride)) {
            throw new FamilyException("marriage-related");
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

    /**
     * Aftakking: bruidegom en bruid verlaten hun familie en vormen samen "A-B" (tag uit de beginletters),
     * bruidegom = hoofd, bruid = Gemaal. Was een van beiden hoofd, dan neemt de Opvolger (of Reserve-erfgenaam) het over.
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
        genealogy.addOrigin(groom, groomFamily.getName());
        genealogy.addOrigin(bride, brideFamily.getName());
        genealogy.linkPartner(groom, bride);
        invites.remove(groom);
        invites.remove(bride);
        engagements.remove(groom);
        engagements.remove(bride);
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

    // ---- dev mode: nep-spelers ----

    /** Nep-spelers (dev mode), zodat je alles alleen kunt testen. Worden bewaard tot /family dev clear. */
    private final java.util.Set<UUID> fakes = ConcurrentHashMap.newKeySet();

    public static UUID fakeId(String name) {
        return UUID.nameUUIDFromBytes(("FenixCoreFake:" + name.toLowerCase(Locale.ROOT)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean isFake(UUID player) {
        return fakes.contains(player);
    }

    public java.util.Set<UUID> getFakes() {
        return fakes;
    }

    /** Nep-lid toevoegen aan een familie (zonder uitnodiging). */
    public UUID addFake(Family family, String name) throws FamilyException {
        UUID uuid = fakeId(name);
        if (byMember.containsKey(uuid)) {
            throw new FamilyException("dev-fake-exists");
        }
        fakes.add(uuid);
        family.addMember(uuid, name, FamilyRank.CHILD);
        byMember.put(uuid, family);
        genealogy.setName(uuid, name);
        return uuid;
    }

    /** Nieuwe familie met een nep-hoofd. */
    public Family createFakeFamily(String headName, String rawName, String rawTag) throws FamilyException {
        UUID uuid = fakeId(headName);
        if (byMember.containsKey(uuid)) {
            throw new FamilyException("dev-fake-exists");
        }
        Family family = create(uuid, headName, rawName, rawTag);
        fakes.add(uuid);
        return family;
    }

    /** Verloving direct vastleggen (dev: geen aanzoek/akkoord nodig). */
    public Engagement forceEngagement(UUID groom, UUID bride) throws FamilyException {
        if (groom.equals(bride)) {
            throw new FamilyException("marriage-self");
        }
        if (genealogy.isBloodRelated(groom, bride)) {
            throw new FamilyException("marriage-related");
        }
        if (byMember.get(groom) == null || byMember.get(bride) == null) {
            throw new FamilyException("engage-no-family");
        }
        if (engagements.containsKey(groom) || engagements.containsKey(bride)) {
            throw new FamilyException("engage-already-engaged");
        }
        Engagement engagement = new Engagement(groom, bride);
        loadEngagement(engagement);
        return engagement;
    }

    /**
     * Alle nep-spelers weghalen. Een familie met een nep-hoofd gaat naar een echt lid, of wordt opgeheven als er geen
     * echte leden zijn. @return aantal opgeheven families
     */
    public int clearFakes() {
        int disbanded = 0;
        for (UUID fake : new ArrayList<>(fakes)) {
            Family family = byMember.get(fake);
            if (family != null && isRegistered(family)) {
                if (family.isOwner(fake)) {
                    UUID real = null;
                    for (UUID member : family.getMembers().keySet()) {
                        if (!fakes.contains(member)) {
                            real = member;
                            break;
                        }
                    }
                    if (real == null) {
                        disband(family);
                        disbanded++;
                    } else {
                        family.putRank(real, null);
                        family.setOwner(real);
                        family.removeMember(fake);
                    }
                } else {
                    family.removeMember(fake);
                }
            }
            byMember.remove(fake);
            Engagement e = engagements.get(fake);
            if (e != null) {
                engagements.remove(e.groom);
                engagements.remove(e.bride);
            }
            invites.remove(fake);
            genealogy.forget(fake);
            fakes.remove(fake);
        }
        return disbanded;
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
