package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.Genealogy;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stamboom: elke rij is een generatie (bovenaan de oudste). Generatie = 1 + die van de ouders binnen de familie;
 * partners staan naast elkaar.
 */
public final class TreeGui extends Gui {

    private static final int GENERATIONS = 5;

    public TreeGui(FamilyService service, Family family, Player viewer) {
        super(service, 6, service.raw("gui.tree-title", "&8Stamboom: %name%").replace("%name%", family.getName()));
        Genealogy tree = service.manager().getGenealogy();
        List<UUID> members = new ArrayList<>(family.getMembers().keySet());
        Map<UUID, Integer> level = new HashMap<>();
        for (UUID member : members) {
            generation(member, family, tree, level, 0);
        }
        // partners in dezelfde generatie (de hoogste van de twee)
        for (UUID member : members) {
            UUID partner = tree.partnerOf(member);
            if (partner != null && level.containsKey(partner)) {
                int max = Math.max(level.get(member), level.get(partner));
                level.put(member, max);
                level.put(partner, max);
            }
        }

        for (int gen = 0; gen < GENERATIONS; gen++) {
            final int g = gen;
            List<UUID> row = new ArrayList<>();
            for (UUID member : members) {
                int l = Math.min(GENERATIONS - 1, level.get(member));
                if (l == g) {
                    row.add(member);
                }
            }
            row.sort(Comparator.comparing((UUID u) -> firstParentName(service, tree, u))
                    .thenComparing(u -> family.getRank(u).ordinal()));
            List<UUID> ordered = new ArrayList<>();
            for (UUID member : row) {
                if (ordered.contains(member)) {
                    continue;
                }
                ordered.add(member);
                UUID partner = tree.partnerOf(member);
                if (partner != null && row.contains(partner) && !ordered.contains(partner)) {
                    ordered.add(partner);
                }
            }
            for (int i = 0; i < 9 && i < ordered.size(); i++) {
                UUID member = ordered.get(i);
                int slot = g * 9 + i;
                if (i == 8 && ordered.size() > 9) {
                    set(slot, GuiItems.item(Material.PAPER, text("tree-more", "&7+%count% meer")
                            .replace("%count%", String.valueOf(ordered.size() - 8)), List.of()));
                    break;
                }
                set(slot, GuiItems.head(member, "&f" + service.nameOf(member), Lore.member(service, family, member)),
                        e -> {
                            if (service.canEdit((Player) e.getWhoClicked(), family)) {
                                openLater((Player) e.getWhoClicked(), new MemberGui(service, family, member));
                            }
                        });
            }
        }

        set(45, GuiItems.item(Material.ARROW, text("back", "&eTerug"), List.of()),
                e -> openLater((Player) e.getWhoClicked(), new EditorGui(service, family, (Player) e.getWhoClicked())));
        set(49, GuiItems.item(Material.BOOK, text("tree-help", "&6Stamboom"), service.config().getStringList("gui.tree-help-lore")));
        set(53, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        fill();
    }

    /** Generatie binnen de familie (met beveiliging tegen lussen). */
    private static int generation(UUID member, Family family, Genealogy tree, Map<UUID, Integer> level, int depth) {
        Integer known = level.get(member);
        if (known != null) {
            return known;
        }
        int gen = 0;
        if (depth < 32) {
            for (UUID parent : tree.parentsOf(member)) {
                if (family.isMember(parent)) {
                    gen = Math.max(gen, generation(parent, family, tree, level, depth + 1) + 1);
                }
            }
        }
        level.put(member, gen);
        return gen;
    }

    private static String firstParentName(FamilyService service, Genealogy tree, UUID member) {
        for (UUID parent : tree.parentsOf(member)) {
            return service.nameOf(parent).toLowerCase();
        }
        return "";
    }
}
