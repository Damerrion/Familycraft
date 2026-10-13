package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import nl.fenix.core.family.model.FamilyException;
import nl.fenix.core.family.model.FamilyRank;
import nl.fenix.core.family.model.Genealogy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Eén lid bewerken: rang geven, koppelen in de stamboom, uit de familie zetten. */
public final class MemberGui extends Gui {

    private static final Material[] RANK_ICONS = {Material.NETHER_STAR, Material.GOLDEN_HELMET, Material.IRON_HELMET,
            Material.PINK_DYE, Material.MAGENTA_DYE, Material.WHEAT_SEEDS};

    private final Family family;
    private final UUID member;

    public MemberGui(FamilyService service, Family family, UUID member) {
        super(service, 5, service.raw("gui.member-title", "&8Lid: %player%").replace("%player%", service.nameOf(member)));
        this.family = family;
        this.member = member;
        String name = service.nameOf(member);

        set(4, GuiItems.head(member, "&f" + name, Lore.member(service, family, member)));

        // rangen
        FamilyRank current = family.getRank(member);
        FamilyRank[] ranks = FamilyRank.values();
        for (int i = 0; i < ranks.length; i++) {
            FamilyRank rank = ranks[i];
            boolean head = rank == FamilyRank.HEAD;
            String lore = rank == current ? text("rank-current", "&aHuidige rang")
                    : head ? text("rank-head-lore", "&cShift-klik om het familiehoofd over te dragen")
                    : text("rank-lore", "&eKlik om deze rang te geven");
            var icon = GuiItems.item(RANK_ICONS[i], "&6" + service.rankName(rank), List.of(lore));
            set(10 + i + (i >= 3 ? 1 : 0), rank == current ? GuiItems.glow(icon) : icon, e -> {
                if (rank == current || (head && !e.isShiftClick())) {
                    return;
                }
                run(e, p -> service.setRank(p, family, member, rank));
            });
        }

        // stamboom
        set(28, GuiItems.item(Material.OAK_LOG, text("link-parent", "&aOuder koppelen"),
                List.of(text("link-parent-lore", "&7Kies wie de ouder van %player% is").replace("%player%", name))),
                e -> pick(e, text("pick-parent", "&8Ouder van %player%"), other ->
                        service.link(p(e), family, Genealogy.Relation.PARENT, other, member)));
        set(29, GuiItems.item(Material.OAK_SAPLING, text("link-child", "&aKind koppelen"),
                List.of(text("link-child-lore", "&7Kies een kind van %player%").replace("%player%", name))),
                e -> pick(e, text("pick-child", "&8Kind van %player%"), other ->
                        service.link(p(e), family, Genealogy.Relation.PARENT, member, other)));
        set(30, GuiItems.item(Material.POPPY, text("link-partner", "&dPartner koppelen"),
                List.of(text("link-partner-lore", "&7Kies de partner van %player%").replace("%player%", name))),
                e -> pick(e, text("pick-partner", "&8Partner van %player%"), other ->
                        service.link(p(e), family, Genealogy.Relation.PARTNER, member, other)));
        set(31, GuiItems.item(Material.LEAD, text("link-sibling", "&bBroer/zus koppelen"),
                List.of(text("link-sibling-lore", "&7Kies een broer of zus van %player%").replace("%player%", name))),
                e -> pick(e, text("pick-sibling", "&8Broer/zus van %player%"), other ->
                        service.link(p(e), family, Genealogy.Relation.SIBLING, member, other)));
        set(32, GuiItems.item(Material.SHEARS, text("unlink", "&cOntkoppelen"),
                List.of(text("unlink-lore", "&7Haal een relatie van %player% weg").replace("%player%", name))),
                e -> pick(e, text("pick-unlink", "&8Ontkoppelen van %player%"), other ->
                        service.unlink(p(e), family, member, other)));

        if (!family.isOwner(member)) {
            set(34, GuiItems.item(Material.TNT, text("kick", "&cUit de familie zetten"),
                    List.of(text("kick-lore", "&7Shift-klik om %player% te kicken").replace("%player%", name))), e -> {
                if (e.isShiftClick()) {
                    closeLater(p(e));
                    String command = family.isOwner(p(e).getUniqueId()) ? "family kick " + name
                            : "family kick " + family.getTag() + " " + name;
                    Bukkit.getScheduler().runTask(service.module().plugin(), () -> p(e).performCommand(command));
                }
            });
        }

        set(36, GuiItems.item(Material.ARROW, text("back", "&eTerug"), List.of()),
                e -> openLater(p(e), new EditorGui(service, family, p(e))));
        set(44, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()), e -> closeLater(p(e)));
        fill();
    }

    private static Player p(InventoryClickEvent e) {
        return (Player) e.getWhoClicked();
    }

    @FunctionalInterface
    private interface Action<T> {
        void run(T value) throws FamilyException;
    }

    /** Actie uitvoeren en het lid-menu opnieuw tonen (fout = bericht in de chat). */
    private void run(InventoryClickEvent e, Action<Player> action) {
        Player player = p(e);
        try {
            action.run(player);
        } catch (FamilyException ex) {
            service.send(player, ex.getKey());
        }
        openLater(player, family.isMember(member) ? new MemberGui(service, family, member) : new EditorGui(service, family, player));
    }

    private void pick(InventoryClickEvent e, String title, Action<UUID> onPick) {
        Set<UUID> exclude = new HashSet<>();
        exclude.add(member);
        openLater(p(e), new PickMemberGui(service, family, title.replace("%player%", service.nameOf(member)), exclude,
                other -> {
                    try {
                        onPick.run(other);
                    } catch (FamilyException ex) {
                        service.send(p(e), ex.getKey());
                    }
                    openLater(p(e), new MemberGui(service, family, member));
                },
                () -> openLater(p(e), new MemberGui(service, family, member))));
    }
}
