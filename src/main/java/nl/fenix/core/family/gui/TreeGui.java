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
import java.util.TreeSet;
import java.util.UUID;

/**
 * Stamboom: elke rij is een generatie (bovenaan de oudste), links een gekleurde generatie-markering. Koppels staan
 * naast elkaar met een hartje ertussen, kinderen onder hun ouders, broers/zussen bij elkaar. Naamkleur = rang.
 * Past het niet, dan scroll je met de pijlen omhoog/omlaag en naar links/rechts.
 */
public final class TreeGui extends Gui {

    private static final int ROWS = 5;
    private static final int COLUMNS = 8;
    private static final Material[] GENERATION_PANES = {Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE, Material.CYAN_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE};
    private static final char[] GENERATION_COLORS = {'e', '6', 'a', 'b', 'd', '3', 'f'};

    /** Een plek in een rij: een lid, een hartje tussen partners, of leeg. */
    private record Cell(UUID member, UUID partnerA, UUID partnerB) {
        static final Cell GAP = new Cell(null, null, null);

        static Cell of(UUID member) {
            return new Cell(member, null, null);
        }

        static Cell heart(UUID a, UUID b) {
            return new Cell(null, a, b);
        }
    }

    public TreeGui(FamilyService service, Family family, Player viewer) {
        this(service, family, viewer, 0, 0);
    }

    public TreeGui(FamilyService service, Family family, Player viewer, int rowOffset, int columnOffset) {
        super(service, 6, service.raw("gui.tree-title", "&8Stamboom: %name%").replace("%name%", family.getName()));
        Genealogy tree = service.manager().getGenealogy();
        List<List<Cell>> generations = layout(service, family, tree);

        int widest = 0;
        for (List<Cell> row : generations) {
            widest = Math.max(widest, row.size());
        }
        int maxRowOffset = Math.max(0, generations.size() - ROWS);
        int maxColumnOffset = Math.max(0, widest - COLUMNS);
        int top = Math.max(0, Math.min(maxRowOffset, rowOffset));
        int left = Math.max(0, Math.min(maxColumnOffset, columnOffset));

        for (int r = 0; r < ROWS && top + r < generations.size(); r++) {
            int gen = top + r;
            List<Cell> row = generations.get(gen);
            int members = (int) row.stream().filter(c -> c.member() != null).count();
            char color = GENERATION_COLORS[gen % GENERATION_COLORS.length];
            set(r * 9, GuiItems.item(GENERATION_PANES[gen % GENERATION_PANES.length],
                    text("tree-generation", "&%color%Generatie %gen%").replace("%color%", String.valueOf(color))
                            .replace("%gen%", String.valueOf(gen + 1)),
                    List.of(text("tree-generation-lore", "&7%count% lid/leden").replace("%count%", String.valueOf(members)))));
            // midden uitlijnen als de rij in het scherm past
            int pad = row.size() <= COLUMNS ? (COLUMNS - row.size()) / 2 : -left;
            for (int i = 0; i < row.size(); i++) {
                int column = pad + i;
                if (column < 0 || column >= COLUMNS) {
                    continue;
                }
                int slot = r * 9 + 1 + column;
                Cell cell = row.get(i);
                if (cell.member() != null) {
                    UUID member = cell.member();
                    set(slot, GuiItems.head(member, service.rankColor(family.getRank(member)) + service.nameOf(member),
                            Lore.member(service, family, member)), e -> {
                        if (service.canEdit((Player) e.getWhoClicked(), family)) {
                            openLater((Player) e.getWhoClicked(), new MemberGui(service, family, member));
                        }
                    });
                } else if (cell.partnerA() != null) {
                    set(slot, GuiItems.item(Material.PINK_STAINED_GLASS_PANE, text("tree-partner", "&d❤ Getrouwd"),
                            List.of(text("tree-partner-lore", "&7%a% &d& &7%b%")
                                    .replace("%a%", service.nameOf(cell.partnerA()))
                                    .replace("%b%", service.nameOf(cell.partnerB())))));
                }
            }
        }

        set(45, GuiItems.item(Material.ARROW, text("back", "&eTerug"), List.of()),
                e -> openLater((Player) e.getWhoClicked(), new EditorGui(service, family, (Player) e.getWhoClicked())));
        if (top > 0) {
            set(46, GuiItems.item(Material.SPECTRAL_ARROW, text("tree-up", "&eOudere generaties"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new TreeGui(service, family, viewer, top - 1, left)));
        }
        if (top < maxRowOffset) {
            set(47, GuiItems.item(Material.SPECTRAL_ARROW, text("tree-down", "&eJongere generaties"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new TreeGui(service, family, viewer, top + 1, left)));
        }
        set(49, GuiItems.item(Material.BOOK, text("tree-help", "&6Stamboom"), service.config().getStringList("gui.tree-help-lore")));
        if (left > 0) {
            set(51, GuiItems.item(Material.ARROW, text("tree-left", "&eNaar links"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new TreeGui(service, family, viewer, top, Math.max(0, left - 4))));
        }
        if (left < maxColumnOffset) {
            set(52, GuiItems.item(Material.ARROW, text("tree-right", "&eNaar rechts"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new TreeGui(service, family, viewer, top, left + 4)));
        }
        set(53, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        fill();
    }

    /** Rijen per generatie met koppels (hartje ertussen), gesorteerd op de plek van de ouders in de rij erboven. */
    private static List<List<Cell>> layout(FamilyService service, Family family, Genealogy tree) {
        List<UUID> members = new ArrayList<>(family.getMembers().keySet());
        Map<UUID, Integer> level = new HashMap<>();
        for (UUID member : members) {
            generation(member, family, tree, level, 0);
        }
        // partners in dezelfde generatie (de jongste van de twee, zodat ze onder hun eigen ouders blijven staan)
        for (UUID member : members) {
            UUID partner = tree.partnerOf(member);
            if (partner != null && level.containsKey(partner)) {
                int max = Math.max(level.get(member), level.get(partner));
                level.put(member, max);
                level.put(partner, max);
            }
        }
        int deepest = level.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<List<Cell>> rows = new ArrayList<>();
        Map<UUID, Integer> position = new HashMap<>();
        for (int gen = 0; gen <= deepest; gen++) {
            List<List<UUID>> units = new ArrayList<>();
            List<UUID> placed = new ArrayList<>();
            for (UUID member : members) {
                if (level.get(member) != gen || placed.contains(member)) {
                    continue;
                }
                List<UUID> unit = new ArrayList<>();
                unit.add(member);
                placed.add(member);
                UUID partner = tree.partnerOf(member);
                if (partner != null && family.isMember(partner) && level.get(partner) == gen && !placed.contains(partner)) {
                    unit.add(partner);
                    placed.add(partner);
                }
                units.add(unit);
            }
            units.sort(Comparator.comparingInt((List<UUID> u) -> parentPosition(u, tree, position))
                    .thenComparing(u -> siblingKey(service, tree, u.get(0)))
                    .thenComparingInt(u -> family.getRank(u.get(0)).ordinal())
                    .thenComparing(u -> service.nameOf(u.get(0)).toLowerCase()));

            List<Cell> row = new ArrayList<>();
            for (List<UUID> unit : units) {
                if (!row.isEmpty()) {
                    row.add(Cell.GAP);
                }
                position.put(unit.get(0), row.size());
                row.add(Cell.of(unit.get(0)));
                if (unit.size() == 2) {
                    row.add(Cell.heart(unit.get(0), unit.get(1)));
                    position.put(unit.get(1), row.size());
                    row.add(Cell.of(unit.get(1)));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /** Plek van de eerste ouder in de rij erboven (zonder ouders in de familie: achteraan). */
    private static int parentPosition(List<UUID> unit, Genealogy tree, Map<UUID, Integer> position) {
        int best = Integer.MAX_VALUE;
        for (UUID member : unit) {
            for (UUID parent : tree.parentsOf(member)) {
                Integer pos = position.get(parent);
                if (pos != null) {
                    best = Math.min(best, pos);
                }
            }
        }
        return best;
    }

    /** Broers/zussen bij elkaar: sorteren op de kleinste naam van de groep. */
    private static String siblingKey(FamilyService service, Genealogy tree, UUID member) {
        TreeSet<String> names = new TreeSet<>();
        names.add(service.nameOf(member).toLowerCase());
        for (UUID sibling : tree.siblingsOf(member)) {
            names.add(service.nameOf(sibling).toLowerCase());
        }
        return names.first();
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
}
