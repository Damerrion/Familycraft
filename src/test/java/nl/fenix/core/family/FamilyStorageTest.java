package nl.fenix.core.family;

import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import nl.fenix.core.family.storage.FamilyStorage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class FamilyStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Logger logger = Logger.getLogger("test");

    @Test
    public void savesAndLoadsRanksTreeCrestAndBranches() throws Exception {
        UUID head = UUID.randomUUID(), son = UUID.randomUUID(), heir = UUID.randomUUID(), bride = UUID.randomUUID();
        FamilyManager manager = new FamilyManager(30, 3, 0, 60);
        Family a = manager.create(head, "Head", "Dokker_Lijn", "DL");
        manager.invite(a, head, son);
        manager.accept(son, "Son");
        manager.invite(a, head, heir);
        manager.accept(heir, "Heir");
        manager.setRank(a, head, false, heir, FamilyRank.SUCCESSOR);
        manager.link(a, head, false, Genealogy.Relation.PARENT, head, son);
        manager.setCrest(a, head, false, "Y3Jlc3Q=");
        a.setKingdomName("Gast");
        Family b = manager.create(bride, "Bride", "Adamantie_Lijn", "AL");
        // bruid is hoofd: haar familie heeft een Successor nodig om het over te nemen
        UUID sister = UUID.randomUUID();
        manager.invite(b, bride, sister);
        manager.accept(sister, "Sister");
        manager.setRank(b, bride, false, sister, FamilyRank.SUCCESSOR);
        manager.requestEngagement(son, bride);
        FamilyManager.Engagement e = manager.acceptEngagement(bride);
        Family branch = manager.startWedding(bride, false, e).getFamily();
        // een openstaande verloving wordt ook bewaard
        UUID x = UUID.randomUUID();
        manager.invite(a, head, x);
        manager.accept(x, "X");
        manager.requestEngagement(x, sister);
        manager.acceptEngagement(sister).setGroomChooses(false);

        File file = folder.newFile("families.yml");
        new FamilyStorage(file, logger).save(manager);
        FamilyManager loaded = new FamilyManager(30, 3, 0, 60);
        new FamilyStorage(file, logger).load(loaded);

        Family la = loaded.getFamilyByTag("DL");
        assertEquals(FamilyRank.SUCCESSOR, la.getRank(heir));
        assertEquals(FamilyRank.HEAD, la.getRank(head));
        assertEquals("Y3Jlc3Q=", la.getCrest());
        assertEquals("Gast", la.getKingdomName());
        Family lb = loaded.getFamilyByTag(branch.getTag());
        assertEquals("Dokker-Adamantie Lijn", lb.getName());
        assertTrue(lb.isOwner(son));
        assertEquals(FamilyRank.CONSORT, lb.getRank(bride));
        assertEquals(2, lb.getLineage().size());
        assertEquals("Gast", lb.getKingdomName());
        assertTrue(loaded.getFamilyByTag("AL").isOwner(sister));
        assertTrue(loaded.getGenealogy().parentsOf(son).contains(head));
        assertEquals(bride, loaded.getGenealogy().partnerOf(son));
        assertSame(lb, loaded.getFamily(bride));
        assertEquals(java.util.List.of("Dokker Lijn"), loaded.getGenealogy().originsOf(son));
        FamilyManager.Engagement le = loaded.getEngagement(x);
        assertNotNull(le);
        assertEquals(sister, le.getBride());
        assertEquals(sister, le.getChooser());
    }

    @Test
    public void loadsOldFamilyCraftFormat() throws Exception {
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID();
        File file = folder.newFile("families.yml");
        Files.write(file.toPath(), ("families:\n  tw:\n    name: Triton Wachters\n    tag: TW\n    owner: " + owner
                + "\n    created: 1700000000000\n    kingdom: Tygron\n    members:\n      " + owner + ": Boss\n      "
                + member + ": Kid\n").getBytes(StandardCharsets.UTF_8));
        FamilyManager manager = new FamilyManager(30, 3, 0, 60);
        new FamilyStorage(file, logger).load(manager);
        Family family = manager.getFamilyByTag("tw");
        assertEquals("Boss", family.getOwnerName());
        assertEquals("Kid", family.getMembers().get(member));
        assertEquals(FamilyRank.CHILD, family.getRank(member));
        assertEquals("Tygron", family.getKingdomName());
        assertFalse(family.isBranch());
    }

    @Test
    public void archiveSurvivesARestart() throws Exception {
        UUID head = UUID.randomUUID(), son = UUID.randomUUID();
        FamilyManager manager = new FamilyManager(30, 3, 0, 60);
        Family a = manager.create(head, "Head", "Dokker_Lijn", "DL");
        manager.invite(a, head, son);
        manager.accept(son, "Son");
        manager.setRank(a, head, false, son, FamilyRank.SUCCESSOR);
        manager.setCrest(a, head, false, "Y3Jlc3Q=");
        a.setKingdomName("Gast");
        manager.disband(a);

        File file = folder.newFile("families.yml");
        new FamilyStorage(file, logger).save(manager);
        FamilyManager loaded = new FamilyManager(30, 3, 0, 60);
        new FamilyStorage(file, logger).load(loaded);

        assertEquals(1, loaded.getArchive().size());
        nl.fenix.core.family.model.ArchivedFamily entry = loaded.getArchive().get(0);
        assertEquals("disband", entry.getReason());
        Family back = loaded.unarchive(entry.getId()).getFamily();
        assertEquals("Dokker Lijn", back.getName());
        assertTrue(back.isOwner(head));
        assertEquals(FamilyRank.SUCCESSOR, back.getRank(son));
        assertEquals("Y3Jlc3Q=", back.getCrest());
        assertEquals("Gast", back.getKingdomName());
        assertEquals(a.getCreatedAt().toEpochMilli(), back.getCreatedAt().toEpochMilli());
    }
}
