package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * /family editor: familiewapen, info, leden met hun rang en relaties, stamboom. Het hoofd (of een admin) kan
 * bewerken; anderen kunnen alleen kijken.
 */
public final class EditorGui extends Gui {

    private static final int FIRST_MEMBER_SLOT = 18;
    private static final int MEMBERS_PER_PAGE = 27;

    private final Family family;
    private final boolean editable;

    public EditorGui(FamilyService service, Family family, Player viewer) {
        this(service, family, viewer, 0);
    }

    public EditorGui(FamilyService service, Family family, Player viewer, int page) {
        super(service, 6, service.raw("gui.editor-title", "&8Familie: %name%").replace("%name%", family.getName()));
        this.family = family;
        this.editable = service.canEdit(viewer, family);

        // familiewapen: met een banner op je cursor klikken = nieuw wapen
        List<String> crestLore = new ArrayList<>();
        if (editable) {
            crestLore.add(text("crest-hint", "&eKlik met een banner om het familiewapen te veranderen"));
        }
        if (family.getCrest() != null && (family.isMember(viewer.getUniqueId()) || service.isAdmin(viewer))) {
            crestLore.add(text("crest-copy", "&7Shift-klik: een kopie van het wapen"));
        }
        set(4, GuiItems.crest(family.getCrest(), text("crest-name", "&6Familiewapen"), crestLore), this::clickCrest);
        set(0, GuiItems.item(Material.BOOK, text("family-name", "&d%name%").replace("%name%", family.getName()),
                Lore.family(service, family)));
        set(6, GuiItems.item(Material.WRITTEN_BOOK, text("lineage-name", "&6&lKroniek van het Huis"), lineageLore()));
        set(8, GuiItems.item(Material.OAK_SAPLING, text("tree-button", "&aStamboom"),
                List.of(text("tree-button-lore", "&7Generaties, partners en broers/zussen"))),
                e -> openLater((Player) e.getWhoClicked(), new TreeGui(service, family, (Player) e.getWhoClicked())));

        List<UUID> members = new ArrayList<>(family.getMembers().keySet());
        members.sort(Comparator.comparing((UUID u) -> family.getRank(u).ordinal())
                .thenComparing(u -> service.nameOf(u).toLowerCase()));
        int pages = Math.max(1, (members.size() + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE);
        int p = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < MEMBERS_PER_PAGE && p * MEMBERS_PER_PAGE + i < members.size(); i++) {
            UUID member = members.get(p * MEMBERS_PER_PAGE + i);
            List<String> lore = Lore.member(service, family, member);
            if (editable) {
                lore.add("");
                lore.add(text("member-click", "&eKlik om rang en stamboom te bewerken"));
            }
            set(FIRST_MEMBER_SLOT + i, Lore.icon(service, family, member, service.rankColor(family.getRank(member)) + service.nameOf(member), lore), e -> {
                if (editable) {
                    openLater((Player) e.getWhoClicked(), new MemberGui(service, family, member));
                }
            });
        }

        set(45, GuiItems.item(Material.ARROW, text("overview-button", "&eAlle families"), List.of()),
                e -> openLater((Player) e.getWhoClicked(), OverviewGui.forViewer(service, (Player) e.getWhoClicked())));
        if (p > 0) {
            set(47, GuiItems.item(Material.ARROW, text("previous", "&eVorige pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new EditorGui(service, family, (Player) e.getWhoClicked(), p - 1)));
        }
        if (editable && family.needsRename()) {
            set(48, GuiItems.glow(GuiItems.item(Material.NAME_TAG, text("rename-button", "&6Nieuwe naam kiezen"),
                    List.of(text("rename-button-lore", "&7Deze aftakking moet een nieuwe naam en tag kiezen")))),
                    e -> openLater((Player) e.getWhoClicked(), new RenameGui(service, family)));
        }
        set(49, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        if (p < pages - 1) {
            set(51, GuiItems.item(Material.ARROW, text("next", "&eVolgende pagina"), List.of()),
                    e -> openLater((Player) e.getWhoClicked(), new EditorGui(service, family, (Player) e.getWhoClicked(), p + 1)));
        }
        fill();
    }

    /** Volledige afkomst: uit welke lijnen deze familie is ontstaan (in /family list staat hij niet). */
    private List<String> lineageLore() {
        List<String> lore = new ArrayList<>();
        if (!family.isBranch()) {
            lore.add(text("lineage-original", "&7Een oorspronkelijke lijn, door niemand voortgebracht."));
            return lore;
        }
        lore.add(text("lineage-header", "&7Dit huis is ontstaan uit:"));
        for (String line : family.getLineage()) {
            lore.add(text("lineage-entry", "&8 ┃ &e⚜ &f%line%").replace("%line%", line));
        }
        lore.add(text("lineage-footer", "&8 ┗ &6&l%name%").replace("%name%", family.getName()));
        return lore;
    }

    /** Klik op een banner in je eigen inventory = die banner wordt het familiewapen (je houdt hem). */
    @Override
    protected void handleBottom(InventoryClickEvent event) {
        if (editable && GuiItems.isBanner(event.getCurrentItem())) {
            changeCrest((Player) event.getWhoClicked(), event.getCurrentItem());
        }
    }

    private void changeCrest(Player player, ItemStack banner) {
        try {
            service.setCrest(player, family, banner);
            openLater(player, new EditorGui(service, family, player));
        } catch (FamilyException ex) {
            service.send(player, ex.getKey());
        }
    }

    private void clickCrest(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.isShiftClick()) {
            try {
                service.giveCrest(player, family);
            } catch (FamilyException ex) {
                service.send(player, ex.getKey());
            }
            return;
        }
        if (!editable) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (!GuiItems.isBanner(cursor)) {
            service.send(player, "crest-hint");
            return;
        }
        changeCrest(player, cursor);
    }
}
