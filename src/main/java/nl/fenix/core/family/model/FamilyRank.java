package nl.fenix.core.family.model;

import java.util.Locale;

/** Rangen binnen een familie (hiërarchie), van hoog naar laag. Weergavenamen zijn instelbaar in family.yml. */
public enum FamilyRank {
    HEAD("family-head", "familiehoofd", "Familiehoofd", "Family-head"),
    SUCCESSOR("successor", "opvolger", "Opvolger", "Successor"),
    SPARE_HEIR("spare-heir", "reserve-erfgenaam", "Reserve-erfgenaam", "Spare-Heir"),
    CONSORT("consort", "gemaal", "Gemaal", "Consort"),
    HEIRS_CONSORT("heirs-consort", "gemaal-van-de-erfgenaam", "Gemaal van de Erfgenaam", "Heir's Consort"),
    CHILD("children-of-the-house", "kind-van-het-huis", "Kind van het Huis", "Children of the House");

    /** id in families.yml en family.yml (Engels, verandert nooit) */
    private final String id;
    /** Nederlandse naam voor commando's (/family rank give X opvolger) */
    private final String dutchId;
    private final String defaultName;
    /** Engelse naam uit eerdere versies (oude family.yml wordt hiermee omgezet) */
    private final String oldName;

    FamilyRank(String id, String dutchId, String defaultName, String oldName) {
        this.id = id;
        this.dutchId = dutchId;
        this.defaultName = defaultName;
        this.oldName = oldName;
    }

    public String dutchId() {
        return dutchId;
    }

    public String oldName() {
        return oldName;
    }

    public String id() {
        return id;
    }

    public String defaultName() {
        return defaultName;
    }

    /** Zoekt op id of naam, zonder op hoofdletters, spaties, streepjes of apostrofs te letten. Null als onbekend. */
    public static FamilyRank parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = simplify(raw);
        for (FamilyRank rank : values()) {
            if (simplify(rank.id).equals(key) || simplify(rank.dutchId).equals(key) || simplify(rank.defaultName).equals(key)
                    || simplify(rank.oldName).equals(key) || simplify(rank.name()).equals(key)) {
                return rank;
            }
        }
        return null;
    }

    private static String simplify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }
}
