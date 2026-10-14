package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Kies een lid van de familie (voor het koppelen in de stamboom). */
public final class PickMemberGui extends Gui {

    public PickMemberGui(FamilyService service, Family family, String title, Set<UUID> exclude,
                         Consumer<UUID> onPick, Runnable onBack) {
        this(service, family, title, without(family.getMembers().keySet(), exclude), onPick, onBack);
    }

    private static List<UUID> without(java.util.Collection<UUID> all, Set<UUID> exclude) {
        List<UUID> list = new ArrayList<>(all);
        list.removeAll(exclude);
        return list;
    }

    /** Kies uit deze spelers (mogen ook in een andere familie zitten, bv. bij ontkoppelen). */
    public PickMemberGui(FamilyService service, Family family, String title, List<UUID> candidates,
                         Consumer<UUID> onPick, Runnable onBack) {
        super(service, 6, title);
        List<UUID> members = new ArrayList<>(candidates);
        members.sort(Comparator.comparing((UUID u) -> family.getRank(u).ordinal())
                .thenComparing(u -> service.nameOf(u).toLowerCase()));
        for (int i = 0; i < 45 && i < members.size(); i++) {
            UUID member = members.get(i);
            set(i, Lore.icon(service, family, member, "&f" + service.nameOf(member), Lore.member(service, family, member)),
                    e -> onPick.accept(member));
        }
        if (members.isEmpty()) {
            set(22, GuiItems.item(Material.BARRIER, text("pick-empty", "&7Geen andere leden"), List.of()));
        }
        set(45, GuiItems.item(Material.ARROW, text("back", "&eTerug"), List.of()), e -> onBack.run());
        set(49, GuiItems.item(Material.BARRIER, text("close", "&cSluiten"), List.of()),
                e -> closeLater((Player) e.getWhoClicked()));
        fill();
    }
}
