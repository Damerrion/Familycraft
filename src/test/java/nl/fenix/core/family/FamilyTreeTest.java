package nl.fenix.core.family;

import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** Rangen, stamboom, huwelijken en aftakkingen. */
public class FamilyTreeTest {

    private FamilyManager manager;
    private final UUID headA = UUID.randomUUID();
    private final UUID sonA = UUID.randomUUID();
    private final UUID heirA = UUID.randomUUID();
    private final UUID headB = UUID.randomUUID();
    private final UUID daughterB = UUID.randomUUID();
    private Family a;
    private Family b;

    @Before
    public void setUp() throws Exception {
        manager = new FamilyManager(30, 3, 0, 60);
        a = manager.create(headA, "HeadA", "Dokker_Lijn", "DL");
        join(a, headA, sonA, "SonA");
        join(a, headA, heirA, "HeirA");
        b = manager.create(headB, "HeadB", "Adamantie_Lijn", "AL");
        join(b, headB, daughterB, "DaughterB");
        a.setKingdomName("Gast");
    }

    private void join(Family family, UUID head, UUID player, String name) throws FamilyException {
        manager.invite(family, head, player);
        manager.accept(player, name);
    }

    private void assertKey(String key, FamilyManager.ThrowingRunnable r) {
        try {
            r.run();
            fail("expected " + key);
        } catch (FamilyException ex) {
            assertEquals(key, ex.getKey());
        }
    }

    @Test
    public void ranksDefaultsLimitsAndHeadTransfer() throws Exception {
        assertEquals(FamilyRank.HEAD, a.getRank(headA));
        assertEquals(FamilyRank.CHILD, a.getRank(sonA));
        manager.setRank(a, headA, false, heirA, FamilyRank.SUCCESSOR);
        assertKey("rank-full", () -> manager.setRank(a, headA, false, sonA, FamilyRank.SUCCESSOR));
        assertKey("not-owner", () -> manager.setRank(a, sonA, false, sonA, FamilyRank.CONSORT));
        assertKey("rank-head-change", () -> manager.setRank(a, headA, false, headA, FamilyRank.CONSORT));
        manager.setRank(a, headA, false, sonA, FamilyRank.HEAD);
        assertTrue(a.isOwner(sonA));
        assertEquals(FamilyRank.CHILD, a.getRank(headA));
        assertEquals(FamilyRank.parse("heir's consort"), FamilyRank.HEIRS_CONSORT);
        assertEquals(FamilyRank.parse("Children of the House"), FamilyRank.CHILD);
    }

    @Test
    public void treeLinksWithinFamily() throws Exception {
        Genealogy tree = manager.getGenealogy();
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, sonA);
        manager.link(a, headA, false, Genealogy.Relation.SIBLING, sonA, heirA);
        assertTrue(tree.childrenOf(headA).contains(sonA));
        assertTrue(tree.siblingsOf(heirA).contains(sonA));
        assertKey("tree-cycle", () -> manager.link(a, headA, false, Genealogy.Relation.PARENT, sonA, headA));
        assertKey("tree-already", () -> manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, sonA));
        assertKey("tree-not-member", () -> manager.link(a, headA, false, Genealogy.Relation.PARTNER, sonA, daughterB));
        manager.unlink(a, headA, false, headA, sonA);
        assertTrue(tree.parentsOf(sonA).isEmpty());
        assertKey("tree-no-relation", () -> manager.unlink(a, headA, false, headA, sonA));
    }

    @Test
    public void parentAndChildCannotBeSiblings() throws Exception {
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, sonA);
        assertKey("tree-sibling-parent", () -> manager.link(a, headA, false, Genealogy.Relation.SIBLING, sonA, headA));
        manager.link(a, headA, false, Genealogy.Relation.SIBLING, sonA, heirA);
        assertKey("tree-sibling-parent", () -> manager.link(a, headA, false, Genealogy.Relation.PARENT, heirA, sonA));
        // kleinkind kan ook geen broer/zus van de grootouder zijn
        UUID grandchild = UUID.randomUUID();
        join(a, headA, grandchild, "Grandchild");
        manager.link(a, headA, false, Genealogy.Relation.PARENT, sonA, grandchild);
        assertKey("tree-sibling-parent", () -> manager.link(a, headA, false, Genealogy.Relation.SIBLING, headA, grandchild));
    }

    @Test
    public void relationsWithAnotherFamilyCanBeUnlinked() throws Exception {
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, sonA);
        // zoon begint een eigen familie: de oude relatie moet nog te verbreken zijn
        manager.leave(sonA);
        Family own = manager.create(sonA, "SonA", "Nieuwe_Lijn", "NL");
        assertTrue(manager.getGenealogy().relativesOf(sonA).contains(headA));
        manager.unlinkSelf(sonA, headA);
        assertTrue(manager.getGenealogy().parentsOf(sonA).isEmpty());
        // ook als hoofd via de familie (de ander zit in een andere familie)
        manager.getGenealogy().loadParent(headA, sonA);
        manager.unlink(own, sonA, false, sonA, headA);
        assertTrue(manager.getGenealogy().parentsOf(sonA).isEmpty());
        // binnen dezelfde familie beslist het hoofd
        UUID other = UUID.randomUUID();
        join(a, headA, other, "Other");
        manager.link(a, headA, false, Genealogy.Relation.SIBLING, heirA, other);
        assertKey("tree-unlink-ask-head", () -> manager.unlinkSelf(heirA, other));
    }

    @Test
    public void devFakesCanBeUsedAndCleared() throws Exception {
        UUID fake = manager.addFake(a, "Testkind");
        assertTrue(manager.isFake(fake));
        assertSame(a, manager.getFamily(fake));
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, fake);
        Family fakeFamily = manager.createFakeFamily("Testhoofd", "Test_Lijn", "TL");
        UUID fakeHead = FamilyManager.fakeId("Testhoofd");
        manager.forceEngagement(fake, fakeHead);
        FamilyManager.Engagement e = manager.getEngagement(fake);
        assertNotNull(e);
        assertEquals(1, manager.clearFakes()); // alleen Test Lijn (nep-hoofd, geen echte leden)
        assertNull(manager.getFamily(fake));
        assertFalse(a.isMember(fake));
        assertNull(manager.getFamilyByTag("TL"));
        assertTrue(manager.getGenealogy().childrenOf(headA).isEmpty());
        assertNull(manager.getEngagement(fake));
        assertTrue(manager.getFakes().isEmpty());
        assertNotNull(fakeFamily);
    }

    @Test
    public void siblingsAndParentsCannotMarry() throws Exception {
        manager.link(a, headA, false, Genealogy.Relation.SIBLING, sonA, heirA);
        assertKey("marriage-related", () -> manager.requestEngagement(sonA, heirA));
        assertKey("marriage-related", () -> manager.forceEngagement(heirA, sonA));
        assertKey("marriage-related", () -> manager.link(a, headA, false, Genealogy.Relation.PARTNER, sonA, heirA));
        // zelfde ouder, niet als broer/zus gekoppeld: ook niet
        UUID kid1 = UUID.randomUUID(), kid2 = UUID.randomUUID();
        join(a, headA, kid1, "Kid1");
        join(a, headA, kid2, "Kid2");
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, kid1);
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, kid2);
        assertKey("marriage-related", () -> manager.requestEngagement(kid1, kid2));
        // ouder en kind
        assertKey("marriage-related", () -> manager.requestEngagement(headA, kid1));
        // verloofd en daarna als broer/zus gekoppeld: de trouwerij gaat niet door
        UUID x = UUID.randomUUID(), y = UUID.randomUUID();
        join(a, headA, x, "X");
        join(a, headA, y, "Y");
        manager.requestEngagement(x, y);
        FamilyManager.Engagement e = manager.acceptEngagement(y);
        manager.link(a, headA, false, Genealogy.Relation.SIBLING, x, y);
        assertKey("marriage-related", () -> manager.startWedding(headA, false, e));
    }

    @Test
    public void divorceNeedsBothPartners() throws Exception {
        assertKey("divorce-not-married", () -> manager.requestDivorce(sonA));
        Family branch = wed(sonA, daughterB, true, FamilyManager.MarriageOption.BRANCH).getFamily();
        assertEquals(FamilyRank.CONSORT, branch.getRank(daughterB));
        assertKey("divorce-no-request", () -> manager.acceptDivorce(daughterB));
        assertEquals(daughterB, manager.requestDivorce(sonA));
        assertKey("divorce-no-request", () -> manager.acceptDivorce(sonA)); // de aanvrager zelf kan niet bevestigen
        assertEquals(sonA, manager.acceptDivorce(daughterB));
        assertNull(manager.getGenealogy().partnerOf(sonA));
        assertEquals(FamilyRank.CHILD, branch.getRank(daughterB)); // Gemaal -> Kind van het Huis
        assertSame(branch, manager.getFamily(daughterB)); // families blijven zoals ze zijn
        assertTrue(branch.isOwner(sonA));
    }

    @Test
    public void dutchRankNames() {
        assertEquals(FamilyRank.SUCCESSOR, FamilyRank.parse("opvolger"));
        assertEquals(FamilyRank.CHILD, FamilyRank.parse("Kind van het Huis"));
        assertEquals(FamilyRank.HEIRS_CONSORT, FamilyRank.parse("gemaal-van-de-erfgenaam"));
        assertEquals(FamilyRank.HEAD, FamilyRank.parse("Family-head"));
    }

    @Test
    public void mergedNamesAndTags() {
        FamilyManager.MergedName m = FamilyManager.merge("Dokker Lijn", "Adamantie Lijn");
        assertEquals("Dokker-Adamantie Lijn", m.name());
        assertEquals("DAL", m.tag());
        assertEquals(2, m.parts());
        m = FamilyManager.merge("Rheanos Decendants", "Triton Wachters");
        assertEquals("Rheanos Decendants-Triton Wachters", m.name());
        assertEquals("RT", m.tag());
        m = FamilyManager.merge("Dokker-Adamantie Lijn", "Corvin-Eshar Lijn");
        assertEquals("Dokker-Adamantie-Corvin-Eshar Lijn", m.name());
        assertEquals(4, m.parts());
    }

    /** Verloven (groom vraagt, bride zegt ja), rollen/kiezer/optie instellen en het hoofd van de bruid start. */
    private FamilyManager.Wedding wed(UUID groom, UUID bride, boolean groomChooses, FamilyManager.MarriageOption option)
            throws FamilyException {
        manager.requestEngagement(groom, bride);
        FamilyManager.Engagement e = manager.acceptEngagement(bride);
        e.setGroomChooses(groomChooses);
        if (option != null) {
            manager.chooseOption(e.getChooser(), false, option);
        }
        return manager.startWedding(manager.getFamily(bride).getOwner(), false, e);
    }

    @Test
    public void engagementRules() throws Exception {
        assertKey("engage-no-request", () -> manager.acceptEngagement(daughterB));
        manager.requestEngagement(sonA, daughterB);
        FamilyManager.Engagement e = manager.acceptEngagement(daughterB);
        assertKey("engage-already-engaged", () -> manager.requestEngagement(heirA, daughterB));
        assertKey("wedding-not-chooser", () -> manager.chooseOption(daughterB, false, FamilyManager.MarriageOption.JOIN_PERSON));
        assertKey("wedding-option-not-allowed", () -> manager.chooseOption(sonA, false, FamilyManager.MarriageOption.ABSORB_LINE));
        assertKey("wedding-not-bride-head", () -> manager.startWedding(headA, false, e));
        e.swapRoles();
        assertEquals(daughterB, e.getGroom());
        assertEquals(sonA, e.getChooser());
        manager.cancelEngagement(sonA);
        assertNull(manager.getEngagement(daughterB));
    }

    @Test
    public void branchOptionCreatesBranch() throws Exception {
        FamilyManager.Wedding w = wed(sonA, daughterB, true, FamilyManager.MarriageOption.BRANCH);
        Family branch = w.getFamily();
        assertEquals(FamilyManager.MarriageType.CHOICE, w.getType());
        assertEquals("Dokker-Adamantie Lijn", branch.getName());
        assertEquals("DAL", branch.getTag());
        assertSame(branch, manager.getFamilyByTag("dal"));
        assertTrue(branch.isOwner(sonA));
        assertEquals(FamilyRank.CONSORT, branch.getRank(daughterB));
        assertEquals("Gast", branch.getKingdomName());
        assertFalse(a.isMember(sonA));
        assertFalse(b.isMember(daughterB));
        assertEquals(daughterB, manager.getGenealogy().partnerOf(sonA));
        assertEquals(java.util.List.of("Dokker Lijn"), manager.getGenealogy().originsOf(sonA));
        assertKey("marriage-already-married", () -> manager.requestEngagement(sonA, headB));
    }

    @Test
    public void headsFuse() throws Exception {
        manager.setRank(b, headB, false, daughterB, FamilyRank.SUCCESSOR);
        FamilyManager.Wedding w = wed(headA, headB, true, null);
        assertEquals(FamilyManager.MarriageType.HEADS, w.getType());
        Family fused = w.getFamily();
        assertSame(a, fused);
        assertEquals("Dokker-Adamantie Lijn", fused.getName());
        assertEquals("DAL", fused.getTag());
        assertNull(manager.getFamilyByTag("AL"));
        assertNull(manager.getFamilyByTag("DL"));
        assertTrue(fused.isOwner(headA));
        assertEquals(FamilyRank.CONSORT, fused.getRank(headB));
        assertEquals(FamilyRank.CHILD, fused.getRank(daughterB)); // Opvolger van B wordt Kind van het Huis
        assertSame(fused, manager.getFamily(daughterB));
        assertEquals(java.util.List.of("Adamantie Lijn"), manager.getGenealogy().originsOf(daughterB));
        assertEquals(java.util.List.of("Dokker Lijn"), manager.getGenealogy().originsOf(sonA));
    }

    @Test
    public void successorJoinsHead() throws Exception {
        manager.setRank(b, headB, false, daughterB, FamilyRank.SUCCESSOR);
        UUID spare = UUID.randomUUID();
        join(b, headB, spare, "Spare");
        manager.setRank(b, headB, false, spare, FamilyRank.SPARE_HEIR);
        FamilyManager.Wedding w = wed(headA, daughterB, true, null);
        assertEquals(FamilyManager.MarriageType.HEAD_SUCCESSOR, w.getType());
        assertSame(a, manager.getFamily(daughterB));
        assertEquals(FamilyRank.CONSORT, a.getRank(daughterB));
        assertEquals(FamilyRank.SUCCESSOR, b.getRank(spare)); // Reserve-erfgenaam schuift door
        assertEquals("Dokker Lijn", a.getName());
    }

    @Test
    public void absorbLine() throws Exception {
        manager.setRank(a, headA, false, heirA, FamilyRank.SUCCESSOR);
        FamilyManager.Wedding w = wed(heirA, daughterB, true, FamilyManager.MarriageOption.ABSORB_LINE);
        assertSame(b, w.getFamily());
        assertNull(manager.getFamilyByTag("DL"));
        assertSame(b, manager.getFamily(headA));
        assertSame(b, manager.getFamily(sonA));
        assertEquals(FamilyRank.CHILD, b.getRank(heirA)); // partner is Kind van het Huis
        assertEquals(FamilyRank.CHILD, b.getRank(headA));
        assertTrue(b.getLineage().contains("Dokker Lijn"));
        assertEquals(java.util.List.of("Dokker Lijn"), manager.getGenealogy().originsOf(headA));
    }

    @Test
    public void absorbPartnerLineTheOtherWayAround() throws Exception {
        // sonA kiest, maar daughterB is geen hoofd/Opvolger: lijn B meenemen mag niet
        manager.requestEngagement(sonA, daughterB);
        FamilyManager.Engagement e = manager.acceptEngagement(daughterB);
        assertKey("wedding-option-not-allowed", () -> manager.chooseOption(sonA, false, FamilyManager.MarriageOption.ABSORB_PARTNER_LINE));
        manager.cancelEngagement(sonA);
        // met het hoofd van B wel: B gaat op in A
        FamilyManager.Wedding w = wed(sonA, headB, true, FamilyManager.MarriageOption.ABSORB_PARTNER_LINE);
        assertSame(a, w.getFamily());
        assertNull(manager.getFamilyByTag("AL"));
        assertSame(a, manager.getFamily(daughterB));
        assertSame(a, manager.getFamily(headB));
        assertEquals(java.util.List.of("Adamantie Lijn"), manager.getGenealogy().originsOf(headB));
        assertTrue(a.getLineage().contains("Adamantie Lijn"));
        assertNotNull(e);
    }

    @Test
    public void joinPartnerTheOtherWayAround() throws Exception {
        // sonA kiest: zijn partner (daughterB) gaat over naar lijn A, beide lijnen gaan verder
        FamilyManager.Wedding w = wed(sonA, daughterB, true, FamilyManager.MarriageOption.JOIN_PARTNER);
        assertSame(a, w.getFamily());
        assertSame(a, manager.getFamily(daughterB));
        assertNotNull(manager.getFamilyByTag("AL"));
        // partner van het hoofd van B overzetten kan pas als B een opvolger heeft
        assertKey("marriage-no-successor", () -> wed(heirA, headB, true, FamilyManager.MarriageOption.JOIN_PARTNER));
    }

    @Test
    public void joinPersonBothLinesContinue() throws Exception {
        FamilyManager.Wedding w = wed(sonA, headB, true, FamilyManager.MarriageOption.JOIN_PERSON);
        assertSame(b, w.getFamily());
        assertSame(b, manager.getFamily(sonA));
        assertEquals(FamilyRank.CONSORT, b.getRank(sonA)); // trouwt met het hoofd van B
        assertNotNull(manager.getFamilyByTag("DL"));
        assertFalse(a.isMember(sonA));
    }

    @Test
    public void newLineNeedsAName() throws Exception {
        // een gewoon lid mag ook samen een nieuwe lijn beginnen: alleen het paar, A en B gaan verder
        FamilyManager.Wedding w = wed(sonA, daughterB, true, FamilyManager.MarriageOption.NEW_LINE);
        Family c = w.getFamily();
        assertTrue(c.needsRename());
        assertNotNull(manager.getFamilyByTag("DL"));
        assertNotNull(manager.getFamilyByTag("AL"));
        assertTrue(c.isOwner(sonA));
        assertEquals(FamilyRank.CONSORT, c.getRank(daughterB));
        assertEquals(2, c.getMembers().size());
        manager.rename(c, sonA, false, "Hoge_Lijn", "HL");
        assertSame(c, manager.getFamilyByTag("HL"));
    }

    @Test
    public void sameFamilyOnlyLinksPartners() throws Exception {
        FamilyManager.Wedding w = wed(sonA, heirA, true, null);
        assertEquals(FamilyManager.MarriageType.SAME_FAMILY, w.getType());
        assertSame(a, manager.getFamily(sonA));
        assertEquals(heirA, manager.getGenealogy().partnerOf(sonA));
    }

    @Test
    public void headBranchNeedsSuccessorWhoTakesOver() throws Exception {
        assertKey("marriage-no-successor", () -> wed(headA, daughterB, true, FamilyManager.MarriageOption.BRANCH));
        manager.cancelEngagement(headA);
        manager.setRank(a, headA, false, heirA, FamilyRank.SUCCESSOR);
        FamilyManager.Wedding w = wed(headA, daughterB, true, FamilyManager.MarriageOption.BRANCH);
        assertEquals(heirA, w.getNewHeads().get(a));
        assertTrue(a.isOwner(heirA));
        assertTrue(w.getFamily().isOwner(headA));
    }

    @Test
    public void fourFamiliesInTheNameMeansRename() throws Exception {
        Family first = wed(sonA, daughterB, true, FamilyManager.MarriageOption.BRANCH).getFamily();
        UUID c = UUID.randomUUID(), c2 = UUID.randomUUID(), d = UUID.randomUUID(), d2 = UUID.randomUUID();
        Family fc = manager.create(c, "C", "Corvin_Lijn", "CL");
        join(fc, c, c2, "C2");
        Family fd = manager.create(d, "D", "Eshar_Lijn", "EL");
        join(fd, d, d2, "D2");
        Family second = wed(c2, d2, true, FamilyManager.MarriageOption.BRANCH).getFamily();
        UUID kid1 = UUID.randomUUID(), kid2 = UUID.randomUUID();
        join(first, sonA, kid1, "Kid1");
        join(second, c2, kid2, "Kid2");
        Family merged = wed(kid1, kid2, true, FamilyManager.MarriageOption.BRANCH).getFamily();
        assertEquals("Dokker-Adamantie-Corvin-Eshar Lijn", merged.getName());
        assertTrue(merged.needsRename());
        assertKey("rename-not-needed", () -> manager.rename(first, sonA, false, "Nieuw", "NW"));
        manager.rename(merged, kid1, false, "Hoge_Lijn", "HL");
        assertEquals("Hoge Lijn", merged.getName());
        assertSame(merged, manager.getFamilyByTag("hl"));
        assertFalse(merged.needsRename());
    }

    @Test
    public void disbandCanBeUndone() throws Exception {
        manager.setRank(a, headA, false, heirA, FamilyRank.SUCCESSOR);
        a.setCrest("crest-a");
        manager.disband(a);
        assertNull(manager.getFamily(sonA));
        assertEquals(1, manager.getArchive().size());
        String id = manager.getArchive().get(0).getId();
        assertEquals("disband", manager.getArchive().get(0).getReason());

        FamilyManager.Restored r = manager.unarchive(id);
        Family back = r.getFamily();
        assertTrue(r.getSkipped().isEmpty());
        assertEquals("Dokker Lijn", back.getName());
        assertSame(back, manager.getFamilyByTag("DL"));
        assertSame(back, manager.getFamily(sonA));
        assertTrue(back.isOwner(headA));
        assertEquals(FamilyRank.SUCCESSOR, back.getRank(heirA));
        assertEquals("crest-a", back.getCrest());
        assertEquals("Gast", back.getKingdomName());
        assertTrue(manager.getArchive().isEmpty());
        assertKey("archive-not-found", () -> manager.unarchive(id));
    }

    @Test
    public void unarchiveSkipsHeadsOfOtherFamiliesAndChecksTag() throws Exception {
        manager.disband(a);
        String id = manager.getArchive().get(0).getId();
        Family other = manager.create(sonA, "SonA", "Nieuwe_Lijn", "NL");
        Family taken = manager.create(heirA, "HeirA", "Andere_Lijn", "DL");
        assertKey("archive-tag-taken", () -> manager.unarchive(id));
        manager.disband(taken);
        FamilyManager.Restored r = manager.unarchive(id);
        assertEquals(java.util.List.of(sonA), r.getSkipped());
        assertSame(other, manager.getFamily(sonA));
        assertSame(r.getFamily(), manager.getFamily(heirA));
        assertSame(r.getFamily(), manager.getFamily(headA));
    }

    @Test
    public void fusionCanBeUndone() throws Exception {
        manager.setRank(b, headB, false, daughterB, FamilyRank.SUCCESSOR);
        wed(headA, headB, true, null);
        assertEquals(2, manager.getArchive().size());
        String idA = null;
        String idB = null;
        for (nl.fenix.core.family.model.ArchivedFamily x : manager.getArchive()) {
            assertEquals("fused", x.getReason());
            if (x.getTag().equals("DL")) {
                idA = x.getId();
            } else {
                idB = x.getId();
            }
        }
        Family restoredA = manager.unarchive(idA).getFamily();
        assertSame(a, restoredA); // dezelfde familie, teruggedraaid
        assertEquals("Dokker Lijn", a.getName());
        assertNull(manager.getFamily(headB));
        Family restoredB = manager.unarchive(idB).getFamily();
        assertEquals("Adamantie Lijn", restoredB.getName());
        assertTrue(restoredB.isOwner(headB));
        assertEquals(FamilyRank.SUCCESSOR, restoredB.getRank(daughterB));
        assertSame(restoredB, manager.getFamily(daughterB));
        assertFalse(a.isBranch());
    }

    @Test
    public void bannedWords() throws Exception {
        manager.setBannedWords(java.util.List.of("Nazi", "k k k"));
        assertKey("name-banned", () -> manager.validateName("De_NAZI_lijn"));
        assertKey("name-banned", () -> manager.validateTag("KKK"));
        manager.validateName("Dokker_Lijn");
        manager.validateTag("ABC");
        assertKey("name-banned", () -> manager.create(UUID.randomUUID(), "X", "Na_zi", "NZ"));
    }

    @Test
    public void onlyOneOfEachSpecialRank() throws Exception {
        for (FamilyRank rank : new FamilyRank[]{FamilyRank.SUCCESSOR, FamilyRank.SPARE_HEIR, FamilyRank.CONSORT,
                FamilyRank.HEIRS_CONSORT}) {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            join(a, headA, first, "First" + rank.ordinal());
            join(a, headA, second, "Second" + rank.ordinal());
            manager.setRank(a, headA, false, first, rank);
            assertKey("rank-full", () -> manager.setRank(a, headA, false, second, rank));
        }
    }

    @Test
    public void newConsortReplacesAnOldOne() throws Exception {
        // oud Gemaal (bv. van een vorig hoofd) wordt Kind van het Huis als het hoofd trouwt
        manager.setRank(a, headA, false, sonA, FamilyRank.CONSORT);
        manager.setRank(b, headB, false, daughterB, FamilyRank.SUCCESSOR);
        wed(headA, daughterB, true, null);
        assertEquals(FamilyRank.CONSORT, a.getRank(daughterB));
        assertEquals(FamilyRank.CHILD, a.getRank(sonA));
    }

    @Test
    public void deceasedCharacterKeepsTheTreeAndThePlayerStartsFresh() throws Exception {
        manager.setRank(a, headA, false, heirA, FamilyRank.SUCCESSOR);
        manager.link(a, headA, false, Genealogy.Relation.PARENT, headA, sonA);
        Genealogy tree = manager.getGenealogy();
        // het hoofd overlijdt: de Opvolger neemt het over, het karakter blijft als gedenk-lid ouder van sonA
        FamilyManager.Death death = manager.declareDeceased(a, headA, false, headA, "Koning_Aldric");
        UUID memorial = death.memorial();
        assertEquals(heirA, death.newHead());
        assertTrue(a.isOwner(heirA));
        assertTrue(manager.isDeceased(memorial));
        assertEquals("Koning Aldric", a.getMembers().get(memorial));
        assertTrue(tree.parentsOf(sonA).contains(memorial));
        assertTrue(tree.relativesOf(headA).isEmpty());
        assertSame(a, manager.getFamily(headA)); // speler blijft lid, nieuw karakter
        assertEquals(FamilyRank.CHILD, a.getRank(headA));
        assertEquals(3, manager.livingCount(a));
        assertKey("deceased-no-rank", () -> manager.setRank(a, heirA, false, memorial, FamilyRank.CONSORT));
        assertKey("deceased-kick", () -> manager.kick(a, heirA, false, memorial));
        assertKey("deceased-cannot", () -> manager.requestEngagement(memorial, headB));

        // vergissing: terugdraaien
        assertEquals(headA, manager.revive(a, heirA, false, memorial));
        assertFalse(manager.isDeceased(memorial));
        assertFalse(a.isMember(memorial));
        assertTrue(tree.parentsOf(sonA).contains(headA));
    }

    @Test
    public void widowCanRemarry() throws Exception {
        manager.link(a, headA, false, Genealogy.Relation.PARTNER, sonA, heirA);
        assertKey("marriage-already-married", () -> manager.requestEngagement(sonA, daughterB));
        UUID memorial = manager.declareDeceased(a, headA, false, heirA, null).memorial();
        assertEquals(memorial, manager.getGenealogy().partnerOf(sonA));
        assertNull(manager.livingPartner(sonA));
        assertKey("divorce-deceased", () -> manager.requestDivorce(sonA));
        wed(sonA, daughterB, true, FamilyManager.MarriageOption.BRANCH);
        assertEquals(daughterB, manager.getGenealogy().partnerOf(sonA));
    }
}
