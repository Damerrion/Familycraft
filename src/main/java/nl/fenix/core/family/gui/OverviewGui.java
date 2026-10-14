package nl.fenix.core.family.gui;

import com.gufli.kingdomcraft.api.domain.Kingdom;
import com.gufli.kingdomcraft.api.domain.User;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * /family list: de families van één kingdom (afstamming) of van alle kingdoms, met hun familiewapen.
 * Klik = leden en stamboom bekijken.
 */
public final class OverviewGui extends Gui {

    private static final int PER_PAGE = 45;

    /** Filter: {@link #ALL} = alle kingdoms, {@link #NONE} = families zonder kingdom, anders de kingdomnaam. */
    public static final String ALL = null;
    public static final String NONE = "";

    /** Families uit het kingdom waar de speler nu in zit (zonder kingdom: families zonder kingdom). */
    public static OverviewGui forViewer(FamilyService service, Player viewer) {
        return new OverviewGui(service, viewer, kingdomOf(service, viewer), 0);
    }

    public static String kingdomOf(FamilyService service, Player player) {
        User user = service.kingdomCraft().getOnlineUser(player.getUniqueId());
        Kingdom kingdom = user == null ? null : user.getKingdom();
        return kingdom == null ? NONE : kingdom.getName();
    }

    public OverviewGui(FamilyService service, Player viewer, String kingdom, int page) {
        super(service, 6, service.raw("gui.overview-title", "&8Families: %kingdom%")
                .replace("%kingdom%", title(service, kingdom)));
        List<Family> families = new ArrayList<>();
        for (Family family : service.manager().getFamilies()) {
            if (matches(family, kingdom)) {
                families.add(family);
            }
        }
        families.sort(Comparator.comparing(f -> f.getName().toLowerCase()));
        int pages = Math.max(1, (families.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.max(0, Math.min(pages - 1, page));

        for (int i = 0; i < PER_PAGE && p * PER_PAGE + i < families.size(); i++) {
            Family family = families.get(p * PER_PAGE + i);
            List<String> lore = new ArrayList<>(Lore.family(service, family));
            lore.add("");
            lore.add(text("overview-click", "&eKlik voor de leden en de stamboom"));
            set(i, GuiItems.crest(family.getCrest(), text("family-name", "&d%name%").replace("%name%", family.getName()), lore),
                    e -> openLater((Player) e.getWhoClicked(), new EditorGui(service, family, (Player) e.getWhoClicked())));
        }
        if (families.isEmpty()) {
            set(22, GuiItems.item(Material.BARRIER, text("overview-empty", "&7Geen families in %kingdom%")
                    .replace("%kingdom%", title(service, kingdom)), List.of()));
        }
        if (p > 0) {
            set(45, GuiItems.item(Material.ARROW, text("previous", "&eVorige pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new OverviewGui(service, viewer, kingdom, p - 1)));
        }
        // wisselen tussen je eigen kingdom en alle kingdoms
        if (kingdom == ALL) {
            set(47, GuiItems.item(Material.COMPASS, text("overview-show-own", "&eMijn kingdom"),
                    List.of(text("overview-show-own-lore", "&7Alleen families uit jouw kingdom"))),
                    e -> openLater((Player) e.getWhoClicked(), forViewer(service, (Player) e.getWhoClicked())));
        } else {
            set(47, GuiItems.item(Material.FILLED_MAP, text("overview-show-all", "&eAlle kingdoms"),
                    List.of(text("overview-show-all-lore", "&7Families van alle kingdoms bekijken"))),
                    e -> openLater((Player) e.getWhoClicked(), new OverviewGui(service, viewer, ALL, 0)));
        }
        set(49, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        // archief (opgeheven/samengevoegde families terugzetten): alleen in admin mode
        if (viewer != null && service.isAdmin(viewer)) {
            set(51, GuiItems.item(Material.CHEST, text("overview-archive", "&6Archief"),
                    List.of(text("overview-archive-lore", "&7Opgeheven en samengevoegde families terugzetten"))),
                    e -> openLater((Player) e.getWhoClicked(), new ArchiveGui(service, 0)));
        }
        if (p < pages - 1) {
            set(53, GuiItems.item(Material.ARROW, text("next", "&eVolgende pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new OverviewGui(service, viewer, kingdom, p + 1)));
        }
        fill();
    }

    private static boolean matches(Family family, String kingdom) {
        if (kingdom == ALL) {
            return true;
        }
        String own = family.getKingdomName();
        return kingdom.isEmpty() ? own == null || own.isEmpty() : kingdom.equalsIgnoreCase(own);
    }

    private static String title(FamilyService service, String kingdom) {
        if (kingdom == ALL) {
            return service.raw("gui.overview-all", "alle kingdoms");
        }
        if (kingdom.isEmpty()) {
            return service.raw("gui.overview-none", "zonder kingdom");
        }
        Kingdom k = service.kingdomCraft().getKingdom(kingdom);
        String display = k == null || k.getDisplay() == null || k.getDisplay().isEmpty() ? kingdom : k.getDisplay();
        return display + "&8";
    }
}
