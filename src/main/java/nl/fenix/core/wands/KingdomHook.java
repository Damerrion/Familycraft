package nl.fenix.core.wands;

import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.KingdomCraftProvider;
import com.gufli.kingdomcraft.api.domain.Kingdom;
import com.gufli.kingdomcraft.api.domain.Relation;
import com.gufli.kingdomcraft.api.domain.RelationType;
import com.gufli.kingdomcraft.api.domain.User;
import org.bukkit.entity.Player;

/**
 * Koppeling met KingdomCraft. Alleen nodig om te bepalen wie NIET geraakt wordt:
 * leden van het wand-kingdom zelf en de bondgenoten (ALLY) daarvan.
 */
public final class KingdomHook {

    private final KingdomCraft kdc;

    private KingdomHook(KingdomCraft kdc) {
        this.kdc = kdc;
    }

    /** @return de hook, of null als KingdomCraft niet geladen is. */
    public static KingdomHook create() {
        try {
            return new KingdomHook(KingdomCraftProvider.get());
        } catch (IllegalStateException | LinkageError e) {
            return null;
        }
    }

    public Kingdom getKingdomOf(Player player) {
        User user = kdc.getOnlineUser(player.getUniqueId());
        return user == null ? null : user.getKingdom();
    }

    /**
     * @return true als {@code target} in {@code kingdomName} zit of in een kingdom dat daarmee bondgenoot is.
     *         Spelers zonder kingdom, neutrale, truce en vijandelijke kingdoms: false.
     */
    public boolean isFriendly(Player target, String kingdomName) {
        Kingdom own = kdc.getKingdom(kingdomName);
        Kingdom other = getKingdomOf(target);
        if (own == null || other == null) {
            return false;
        }
        return own.getId() == other.getId() || relationType(own, other) == RelationType.ALLY;
    }

    /** Geaccepteerde relatie tussen twee kingdoms (NEUTRAL als er geen is). Openstaande verzoeken tellen niet. */
    public RelationType relationType(Kingdom own, Kingdom other) {
        Relation direct = kdc.getRelation(own, other);
        if (direct != null) {
            return direct.getType();
        }
        // terugval: vergelijken op id, voor het geval de kingdom-objecten niet dezelfde instantie zijn
        for (Relation relation : kdc.getRelations(own)) {
            if (relation.isRequest()) {
                continue;
            }
            Kingdom a = relation.getKingdom1();
            Kingdom b = relation.getKingdom2();
            if ((a != null && a.getId() == other.getId()) || (b != null && b.getId() == other.getId())) {
                return relation.getType();
            }
        }
        return RelationType.NEUTRAL;
    }

    /**
     * Uitleg voor /kwa check: kingdom van de speler en de relatie met {@code kingdomName} (MiniMessage).
     */
    public String describe(Player target, String kingdomName) {
        Kingdom own = kdc.getKingdom(kingdomName);
        if (own == null) {
            return "<red>kingdom '" + kingdomName + "' bestaat niet in KingdomCraft <gray>(controleer 'kingdom' in wands.yml)";
        }
        Kingdom other = getKingdomOf(target);
        if (other == null) {
            return "<white>geen kingdom";
        }
        if (own.getId() == other.getId()) {
            return "<white>" + other.getName() + " <gray>(zelfde kingdom)";
        }
        String text = "<white>" + other.getName() + " <gray>- relatie met " + own.getName() + ": <white>"
                + relationType(own, other).name();
        Relation request = kdc.getRelationRequest(own, other);
        if (request == null) {
            request = kdc.getRelationRequest(other, own);
        }
        if (request != null) {
            text += " <gold>(openstaand " + request.getType().name() + "-verzoek, nog niet geaccepteerd)";
        }
        return text;
    }
}
