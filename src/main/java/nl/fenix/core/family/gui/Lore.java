package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.Genealogy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Gedeelde lore-regels voor families en leden. */
final class Lore {

    private Lore() {
    }

    /** Info over een familie (gui.family-lore), met de variabelen van FamilyInfo#format. */
    static List<String> family(FamilyService service, Family family) {
        List<String> lines = new ArrayList<>();
        String kingdom = service.info().kingdomOf(family);
        for (String line : service.config().getStringList("gui.family-lore")) {
            if (line.contains("%lineage%") && !family.isBranch()) {
                continue; // afkomst alleen bij aftakkingen
            }
            lines.add(service.info().format(line, family, kingdom));
        }
        return lines;
    }

    /** Rang en stamboom-relaties van een lid. */
    static List<String> member(FamilyService service, Family family, UUID member) {
        Genealogy tree = service.manager().getGenealogy();
        List<String> lines = new ArrayList<>();
        lines.add(line(service, "member-rank", service.rankName(family.getRank(member))));
        UUID partner = tree.partnerOf(member);
        lines.add(line(service, "member-partner", partner == null ? "-" : service.nameOf(partner)));
        lines.add(line(service, "member-parents", names(service, tree.parentsOf(member))));
        lines.add(line(service, "member-children", names(service, tree.childrenOf(member))));
        lines.add(line(service, "member-siblings", names(service, tree.siblingsOf(member))));
        if (!tree.originsOf(member).isEmpty()) {
            // afkomst: altijd terug te volgen uit welke lijn(en) iemand officieel stamt
            lines.add(line(service, "member-origins", String.join(" → ", tree.originsOf(member)) + " → " + family.getName()));
        }
        return lines;
    }

    private static String line(FamilyService service, String key, String value) {
        return service.raw("gui." + key, "&7" + key + ": &f%value%").replace("%value%", value);
    }

    static String names(FamilyService service, Collection<UUID> players) {
        if (players.isEmpty()) {
            return "-";
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : players) {
            names.add(service.nameOf(uuid));
        }
        return String.join(", ", names);
    }
}
