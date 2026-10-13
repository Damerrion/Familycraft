package com.familycraft.chat;

import com.gufli.kingdomcraft.api.entity.PlatformPlayer;

import java.util.ArrayList;

/**
 * Receiver list holding {@link HoverReceiver}s. KingdomCraft checks it with contains() against the real
 * player objects (to add the sender and to skip social spies that already got the message), so players
 * are compared by UUID.
 */
class ReceiverList extends ArrayList<PlatformPlayer> {

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        if (!(o instanceof PlatformPlayer)) {
            return -1;
        }
        for (int i = 0; i < size(); i++) {
            if (get(i).getUniqueId().equals(((PlatformPlayer) o).getUniqueId())) {
                return i;
            }
        }
        return -1;
    }
}
