package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.Genealogy;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
        // iemand uit een andere familie (bv. bij ontkoppelen): die familie en zijn rang daar tonen
        Family actual = family.isMember(member) ? family : service.manager().getFamily(member);
        if (actual != family) {
            lines.add(line(service, "member-family", actual == null ? "-" : actual.getName()));
        }
        FamilyManager.Deceased dead = service.manager().getDeceased(member);
        if (dead != null) {
            // overleden karakter: wanneer, wat hij was en wie hem speelde
            for (String text : service.config().getStringList("gui.deceased-lore")) {
                lines.add(text.replace("%date%", FamilyService.formatDate(dead.getDiedAt()))
                        .replace("%rank%", service.rankName(dead.getFormerRank()))
                        .replace("%player%", service.manager().getGenealogy().name(dead.getPlayer())));
            }
        } else if (actual != null) {
            lines.add(line(service, "member-rank", service.rankName(actual.getRank(member))));
        }
        UUID partner = tree.partnerOf(member);
        lines.add(line(service, "member-partner", partner == null ? "-" : nameOf(service, partner)));
        lines.add(line(service, "member-parents", names(service, tree.parentsOf(member))));
        lines.add(line(service, "member-children", names(service, tree.childrenOf(member))));
        lines.add(line(service, "member-siblings", names(service, tree.siblingsOf(member))));
        if (!tree.originsOf(member).isEmpty()) {
            // afkomst: altijd terug te volgen uit welke lijn(en) iemand officieel stamt
            lines.add(line(service, "member-origins", String.join(" → ", tree.originsOf(member))
                    + (actual == null ? "" : " → " + actual.getName())));
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
            names.add(nameOf(service, uuid));
        }
        return String.join(", ", names);
    }

    /** Naam, met ✝ voor een overleden karakter. */
    static String nameOf(FamilyService service, UUID player) {
        return (service.manager().isDeceased(player) ? "✝ " : "") + service.nameOf(player);
    }

    /** Hoofd van een lid, of een skelet-schedel met ✝ en grijze naam voor een overleden karakter. */
    static ItemStack icon(FamilyService service, Family family, UUID member, String name, List<String> lore) {
        if (service.manager().isDeceased(member)) {
            return GuiItems.item(Material.SKELETON_SKULL, service.raw("gui.deceased-name", "&7✝ %name%")
                    .replace("%name%", service.nameOf(member)), lore);
        }
        return GuiItems.head(member, name, lore);
    }
}
