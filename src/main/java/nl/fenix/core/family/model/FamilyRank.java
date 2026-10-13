package nl.fenix.core.family.model;

import java.util.Locale;

/** Rangen binnen een familie (hiërarchie), van hoog naar laag. Weergavenamen zijn instelbaar in family.yml. */
public enum FamilyRank {
    HEAD("family-head", "Family-head"),
    SUCCESSOR("successor", "Successor"),
    SPARE_HEIR("spare-heir", "Spare-Heir"),
    CONSORT("consort", "Consort"),
    HEIRS_CONSORT("heirs-consort", "Heir's Consort"),
    CHILD("children-of-the-house", "Children of the House");

    private final String id;
    private final String defaultName;

    FamilyRank(String id, String defaultName) {
        this.id = id;
        this.defaultName = defaultName;
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
            if (simplify(rank.id).equals(key) || simplify(rank.defaultName).equals(key) || simplify(rank.name()).equals(key)) {
                return rank;
            }
        }
        return null;
    }

    private static String simplify(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "");
    }
}
