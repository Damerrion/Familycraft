package com.familycraft.placeholder;

import com.familycraft.model.Family;
import com.familycraft.model.FamilyManager;
import com.gufli.kingdomcraft.api.KingdomCraft;
import com.gufli.kingdomcraft.api.domain.User;
import com.gufli.kingdomcraft.api.placeholders.PlaceholderReplacer;

/**
 * Registers {family_tag}, {family_prefix} and {family_name} in KingdomCraft so they can be
 * used in chat.yml formats, scoreboards, etc.
 */
public class FamilyPlaceholders implements PlaceholderReplacer {

    private static final String[] NAMES = {"family_tag", "family_prefix", "family_name"};

    private final KingdomCraft kc;
    private final FamilyManager manager;
    private final String prefixFormat;

    public FamilyPlaceholders(KingdomCraft kc, FamilyManager manager, String prefixFormat) {
        this.kc = kc;
        this.manager = manager;
        this.prefixFormat = prefixFormat;
    }

    public void register() {
        kc.getPlaceholderManager().addPlaceholderReplacer(this, NAMES);
    }

    public void unregister() {
        kc.getPlaceholderManager().removePlaceholderReplacer(this);
    }

    /** The colored {family_prefix} text of a family, exactly as it ends up in the chat line. */
    public String prefixFor(Family family) {
        return kc.colorify(prefixFormat.replace("%tag%", family.getTag()));
    }

    @Override
    public String replace(User user, String placeholder) {
        if (user == null) {
            return "";
        }
        Family family = manager.getFamily(user.getUniqueId());
        if (family == null) {
            return "";
        }
        switch (placeholder.toLowerCase()) {
            case "family_tag":
                return family.getTag();
            case "family_name":
                return family.getName();
            case "family_prefix":
                return prefixFor(family);
            default:
                return "";
        }
    }
}
