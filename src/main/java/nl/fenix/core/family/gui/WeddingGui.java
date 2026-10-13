package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyManager;
import nl.fenix.core.family.model.FamilyManager.MarriageOption;
import nl.fenix.core.family.model.FamilyManager.MarriageType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trouwmenu van een verloofd paar (/enlope): bruidegom/bruid wisselen, wie kiest (A), de keuze van A, en de knop
 * waarmee het hoofd van de familie van de bruid de trouwerij start.
 */
public final class WeddingGui extends Gui {

    private static final Material[] OPTION_ICONS = {Material.HOPPER, Material.ENDER_PEARL, Material.NETHER_STAR,
            Material.OAK_SAPLING};

    private final FamilyManager.Engagement engagement;

    public WeddingGui(FamilyService service, FamilyManager.Engagement engagement) {
        super(service, 5, service.raw("gui.wedding-title", "&8Trouwmenu"));
        this.engagement = engagement;
        FamilyManager manager = service.manager();
        UUID groom = engagement.getGroom();
        UUID bride = engagement.getBride();
        Family groomFamily = manager.getFamily(groom);
        Family brideFamily = manager.getFamily(bride);

        set(11, person(groom, groomFamily, text("wedding-groom", "&bBruidegom"), engagement.getChooser().equals(groom)));
        set(15, person(bride, brideFamily, text("wedding-bride", "&dBruid"), engagement.getChooser().equals(bride)));
        set(13, GuiItems.item(Material.PINK_STAINED_GLASS_PANE, text("wedding-swap", "&d❤ Wissel bruidegom en bruid"),
                List.of(text("wedding-swap-lore", "&7Alleen het bruidspaar"))), e -> act(e, p -> service.swapRoles(p, engagement)));

        MarriageType type = manager.typeOf(engagement);
        if (type == MarriageType.CHOICE) {
            set(22, GuiItems.item(Material.COMPASS, text("wedding-chooser", "&eKeuze bij: &f%player%")
                            .replace("%player%", service.nameOf(engagement.getChooser())),
                    List.of(text("wedding-chooser-lore", "&7Klik om te wisselen wie kiest (alleen het bruidspaar)"))),
                    e -> act(e, p -> service.toggleChooser(p, engagement)));
            MarriageOption[] options = MarriageOption.values();
            int[] slots = {28, 30, 32, 34};
            for (int i = 0; i < options.length; i++) {
                MarriageOption option = options[i];
                String key = "wedding-option-" + option.name().toLowerCase().replace('_', '-');
                List<String> lore = new ArrayList<>();
                for (String line : service.config().getStringList("gui." + key + "-lore")) {
                    lore.add(fill(line, groomFamily, brideFamily));
                }
                boolean allowed = manager.isAllowed(engagement, option);
                lore.add("");
                lore.add(option == engagement.getOption() ? text("wedding-option-selected", "&aGekozen")
                        : allowed ? text("wedding-option-click", "&eKlik om te kiezen (alleen %player%)")
                        .replace("%player%", service.nameOf(engagement.getChooser()))
                        : text("wedding-option-locked", "&cAlleen als %player% hoofd of Opvolger is")
                        .replace("%player%", service.nameOf(engagement.getChooser())));
                var icon = GuiItems.item(allowed ? OPTION_ICONS[i] : Material.GRAY_DYE,
                        fill(text(key, option.name()), groomFamily, brideFamily), lore);
                set(slots[i], option == engagement.getOption() ? GuiItems.glow(icon) : icon,
                        e -> act(e, p -> service.chooseOption(p, engagement, option)));
            }
        } else {
            String key = "wedding-auto-" + type.name().toLowerCase().replace('_', '-');
            List<String> lore = new ArrayList<>();
            for (String line : service.config().getStringList("gui." + key + "-lore")) {
                lore.add(fill(line, groomFamily, brideFamily));
            }
            set(31, GuiItems.glow(GuiItems.item(Material.NETHER_STAR, text(key, type.name()), lore)));
        }

        String headName = brideFamily == null ? "?" : service.nameOf(brideFamily.getOwner());
        set(40, GuiItems.glow(GuiItems.item(Material.BELL, text("wedding-start", "&6&lStart de trouwerij"),
                List.of(text("wedding-start-lore", "&7Alleen het hoofd van de familie van de bruid (%player%)")
                        .replace("%player%", headName)))), e -> {
            Player player = (Player) e.getWhoClicked();
            closeLater(player);
            try {
                service.startWedding(player, engagement);
            } catch (FamilyException ex) {
                service.send(player, ex.getKey());
            }
        });
        set(36, GuiItems.item(Material.BARRIER, text("wedding-cancel", "&cVerloving verbreken"),
                List.of(text("wedding-cancel-lore", "&7Shift-klik (alleen het bruidspaar)"))), e -> {
            Player player = (Player) e.getWhoClicked();
            if (!e.isShiftClick() || !engagement.involves(player.getUniqueId())) {
                return;
            }
            closeLater(player);
            try {
                service.cancelEngagement(player);
            } catch (FamilyException ex) {
                service.send(player, ex.getKey());
            }
        });
        set(44, GuiItems.item(Material.OAK_DOOR, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        fill();
    }

    private org.bukkit.inventory.ItemStack person(UUID player, Family family, String role, boolean chooser) {
        List<String> lore = new ArrayList<>();
        lore.add(role);
        lore.add(text("wedding-family", "&7Familie: &f%name%").replace("%name%", family == null ? "-" : family.getName()));
        if (family != null) {
            lore.add(text("member-rank", "&7Rang: &6%value%").replace("%value%", service.rankName(family.getRank(player))));
        }
        if (chooser && service.manager().typeOf(engagement) == MarriageType.CHOICE) {
            lore.add(text("wedding-is-chooser", "&eKiest hoe de lijnen verder gaan"));
        }
        return GuiItems.head(player, "&f" + service.nameOf(player), lore);
    }

    /** %a% = lijn van de kiezer (A), %b% = lijn van de partner (B), %groom%/%bride% = families. */
    private String fill(String line, Family groomFamily, Family brideFamily) {
        Family a = service.manager().getFamily(engagement.getChooser());
        Family b = service.manager().getFamily(engagement.partnerOf(engagement.getChooser()));
        return line.replace("%a%", a == null ? "-" : a.getName()).replace("%b%", b == null ? "-" : b.getName())
                .replace("%player%", service.nameOf(engagement.getChooser()))
                .replace("%groomfamily%", groomFamily == null ? "-" : groomFamily.getName())
                .replace("%bridefamily%", brideFamily == null ? "-" : brideFamily.getName());
    }

    @FunctionalInterface
    private interface Action {
        void run(Player player) throws FamilyException;
    }

    private void act(InventoryClickEvent e, Action action) {
        Player player = (Player) e.getWhoClicked();
        try {
            action.run(player);
        } catch (FamilyException ex) {
            service.send(player, ex.getKey());
        }
        if (service.manager().getEngagement(engagement.getGroom()) == engagement) {
            openLater(player, new WeddingGui(service, engagement));
        }
    }
}
