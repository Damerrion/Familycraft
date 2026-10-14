package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.ArchivedFamily;
import nl.fenix.core.family.model.FamilyException;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * /family archive (admin mode): opgeheven, opgegane en gefuseerde families, nieuwste eerst.
 * Klik = terugzetten, shift + rechtsklik = definitief uit het archief halen.
 */
public final class ArchiveGui extends Gui {

    private static final int PER_PAGE = 45;
    private static final int MAX_NAMES = 10;

    public ArchiveGui(FamilyService service, int page) {
        super(service, 6, service.raw("gui.archive-title", "&8Archief"));
        List<ArchivedFamily> archive = new ArrayList<>(service.manager().getArchive());
        int pages = Math.max(1, (archive.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.max(0, Math.min(pages - 1, page));

        for (int i = 0; i < PER_PAGE && p * PER_PAGE + i < archive.size(); i++) {
            ArchivedFamily a = archive.get(p * PER_PAGE + i);
            String name = text("archive-item-name", "&d%name% &7[%tag%]")
                    .replace("%name%", a.getName()).replace("%tag%", a.getTag());
            set(i, GuiItems.crest(a.getCrest(), name, lore(a)), e -> {
                Player admin = (Player) e.getWhoClicked();
                try {
                    if (e.getClick() == ClickType.SHIFT_RIGHT) {
                        service.deleteArchived(admin, a.getId());
                    } else if (e.getClick() == ClickType.LEFT) {
                        service.unarchive(admin, a.getId());
                    } else {
                        return;
                    }
                } catch (FamilyException ex) {
                    service.send(admin, ex.getKey());
                }
                openLater(admin, new ArchiveGui(service, p));
            });
        }
        if (archive.isEmpty()) {
            set(22, GuiItems.item(Material.BARRIER, text("archive-empty", "&7Het archief is leeg"), List.of()));
        }
        if (p > 0) {
            set(45, GuiItems.item(Material.ARROW, text("previous", "&eVorige pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new ArchiveGui(service, p - 1)));
        }
        set(48, GuiItems.item(Material.ARROW, text("back", "&eTerug"), List.of()),
                e -> openLater((Player) e.getWhoClicked(), OverviewGui.forViewer(service, (Player) e.getWhoClicked())));
        set(49, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        if (p < pages - 1) {
            set(53, GuiItems.item(Material.ARROW, text("next", "&eVolgende pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new ArchiveGui(service, p + 1)));
        }
        fill();
    }

    private List<String> lore(ArchivedFamily a) {
        List<String> names = new ArrayList<>(a.getMembers().values());
        String members = String.join(", ", names.subList(0, Math.min(MAX_NAMES, names.size())))
                + (names.size() > MAX_NAMES ? ", ..." : "");
        List<String> lines = new ArrayList<>();
        for (String line : service.config().getStringList("gui.archive-lore")) {
            lines.add(line.replace("%reason%", service.archiveReason(a.getReason()))
                    .replace("%date%", FamilyService.formatDate(a.getArchivedAt()))
                    .replace("%owner%", a.getMembers().getOrDefault(a.getOwner(), "?"))
                    .replace("%count%", String.valueOf(a.getMembers().size()))
                    .replace("%members%", members)
                    .replace("%kingdom%", a.getKingdomName() == null || a.getKingdomName().isEmpty() ? "-" : a.getKingdomName())
                    .replace("%id%", a.getId()));
        }
        return lines;
    }
}
