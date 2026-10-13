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
        FamilyManager.Wedding w = wed(headA, daughterB, true, FamilyManager.MarriageOption.NEW_LINE);
        Family c = w.getFamily();
        assertTrue(c.needsRename());
        assertNull(manager.getFamilyByTag("DL"));
        assertNull(manager.getFamilyByTag("AL"));
        assertTrue(c.isOwner(headA));
        assertEquals(FamilyRank.CONSORT, c.getRank(daughterB));
        assertEquals(FamilyRank.CHILD, c.getRank(headB));
        assertEquals(5, c.getMembers().size()); // 3 van A + 2 van B
        manager.rename(c, headA, false, "Hoge_Lijn", "HL");
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
}
