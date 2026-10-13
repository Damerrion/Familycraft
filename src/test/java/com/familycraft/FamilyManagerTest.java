package com.familycraft;

import com.familycraft.model.Family;
import com.familycraft.model.FamilyException;
import com.familycraft.model.FamilyManager;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class FamilyManagerTest {

    private FamilyManager manager;
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Before
    public void setUp() {
        manager = new FamilyManager(30, 3, 0, 60);
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
    public void createsFamilyWithSpacesFromUnderscores() throws Exception {
        Family f = manager.create(owner, "Owner", "Rheanos_Decendants", "RD");
        assertEquals("Rheanos Decendants", f.getName());
        assertEquals("RD", f.getTag());
        assertSame(f, manager.getFamily(owner));
        assertSame(f, manager.getFamilyByTag("rd"));
    }

    @Test
    public void rejectsDigitsAndLengths() {
        assertKey("name-invalid", () -> manager.create(owner, "O", "Fam1", "AB"));
        assertKey("tag-invalid", () -> manager.create(owner, "O", "Fam", "A1"));
        assertKey("tag-invalid", () -> manager.create(owner, "O", "Fam", "ABCD"));
        assertKey("name-invalid", () -> manager.create(owner, "O", "abcdefghijklmnopqrstuvwxyzabcde", "AB"));
        assertKey("name-invalid", () -> manager.create(owner, "O", "___", "AB"));
        assertTrue(manager.getFamilies().isEmpty());
    }

    @Test
    public void nameAndTagMustBeUnique() throws Exception {
        manager.create(owner, "O", "Fam", "AB");
        assertKey("name-taken", () -> manager.create(other, "P", "fam", "CD"));
        assertKey("tag-taken", () -> manager.create(other, "P", "Other", "ab"));
        assertKey("already-in-family", () -> manager.create(owner, "O", "Third", "EF"));
    }

    @Test
    public void inviteAcceptLeaveKick() throws Exception {
        Family f = manager.create(owner, "O", "Fam", "AB");
        assertKey("not-owner", () -> manager.invite(f, other, UUID.randomUUID()));
        assertKey("invite-self", () -> manager.invite(f, owner, owner));
        manager.invite(f, owner, other);
        assertKey("invite-already-sent", () -> manager.invite(f, owner, other));
        assertEquals(f, manager.accept(other, "P"));
        assertTrue(f.isMember(other));
        assertKey("no-invite", () -> manager.accept(UUID.randomUUID(), "X"));
        assertKey("owner-cannot-leave", () -> manager.leave(owner));
        manager.leave(other);
        assertNull(manager.getFamily(other));

        manager.invite(f, owner, other);
        manager.accept(other, "P");
        assertKey("not-owner", () -> manager.kick(f, other, false, owner));
        assertKey("kick-self", () -> manager.kick(f, owner, false, owner));
        manager.kick(f, owner, false, other);
        assertNull(manager.getFamily(other));
    }

    @Test
    public void declineAndCancelInvites() throws Exception {
        Family f = manager.create(owner, "O", "Fam", "AB");
        manager.invite(f, owner, other);
        assertNotNull(manager.decline(other));
        assertNull(manager.getInvite(other));
        manager.invite(f, owner, other);
        assertEquals(1, manager.cancelInvitesOf(f));
        assertNull(manager.getInvite(other));
    }

    @Test
    public void invitesExpire() throws Exception {
        FamilyManager quick = new FamilyManager(30, 3, 0, -1);
        Family f = quick.create(owner, "O", "Fam", "AB");
        quick.invite(f, owner, other);
        assertNull(quick.getInvite(other));
    }

    @Test
    public void maxMembersEnforced() throws Exception {
        FamilyManager small = new FamilyManager(30, 3, 1, 60);
        Family f = small.create(owner, "O", "Fam", "AB");
        assertKey("family-full", () -> small.invite(f, owner, other));
    }

    @Test
    public void disbandFreesEveryone() throws Exception {
        Family f = manager.create(owner, "O", "Fam", "AB");
        manager.invite(f, owner, other);
        manager.disband(f);
        assertNull(manager.getFamily(owner));
        assertNull(manager.getFamilyByTag("AB"));
        assertNull(manager.getInvite(other));
        manager.create(owner, "O", "Fam", "AB");
    }
}
