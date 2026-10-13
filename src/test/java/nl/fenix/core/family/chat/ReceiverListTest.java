package nl.fenix.core.family.chat;

import com.gufli.kingdomcraft.api.entity.PlatformPlayer;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ReceiverListTest {

    /** A fake KingdomCraft player that records the plain messages it gets. */
    private static PlatformPlayer player(UUID uuid, List<String> inbox) {
        return (PlatformPlayer) Proxy.newProxyInstance(PlatformPlayer.class.getClassLoader(), new Class[]{PlatformPlayer.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId":
                            return uuid;
                        case "sendMessage":
                            inbox.add((String) args[0]);
                            return null;
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        default:
                            return null;
                    }
                });
    }

    @Test
    public void kingdomCraftDoesNotAddSenderTwiceOrSpyOnReceivers() {
        List<String> senderInbox = new ArrayList<>();
        List<String> otherInbox = new ArrayList<>();
        PlatformPlayer sender = player(UUID.randomUUID(), senderInbox);
        PlatformPlayer other = player(UUID.randomUUID(), otherInbox);
        PlatformPlayer spy = player(UUID.randomUUID(), new ArrayList<>());

        ReceiverList receivers = new ReceiverList();
        receivers.add(new HoverReceiver(sender, "[TW] ", null, null));
        receivers.add(new HoverReceiver(other, "[TW] ", null, null));

        // what KingdomCraft's ChatManagerImpl.dispatch does with the (real) player objects
        if (!receivers.contains(sender)) {
            receivers.add(sender);
        }
        assertEquals(2, receivers.size());
        assertTrue(receivers.contains(other));
        assertFalse(receivers.contains(spy));

        // no Bukkit player online in the test, so the wrapper falls back to the plain message
        for (PlatformPlayer p : receivers) {
            p.sendMessage("line");
        }
        assertEquals(1, senderInbox.size());
        assertEquals(1, otherInbox.size());
    }
}
