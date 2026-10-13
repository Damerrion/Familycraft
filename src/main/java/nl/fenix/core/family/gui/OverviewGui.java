package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** /family list: alle families met hun familiewapen. Klik = leden en stamboom bekijken. */
public final class OverviewGui extends Gui {

    private static final int PER_PAGE = 45;

    public OverviewGui(FamilyService service, int page) {
        super(service, 6, service.raw("gui.overview-title", "&8Families"));
        List<Family> families = new ArrayList<>(service.manager().getFamilies());
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
            set(22, GuiItems.item(Material.BARRIER, text("overview-empty", "&7Er zijn nog geen families"), List.of()));
        }
        if (p > 0) {
            set(45, GuiItems.item(Material.ARROW, text("previous", "&eVorige pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new OverviewGui(service, p - 1)));
        }
        set(49, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        if (p < pages - 1) {
            set(53, GuiItems.item(Material.ARROW, text("next", "&eVolgende pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new OverviewGui(service, p + 1)));
        }
        fill();
    }
}
