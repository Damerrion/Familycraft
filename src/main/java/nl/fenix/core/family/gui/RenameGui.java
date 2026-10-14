package nl.fenix.core.family.gui;

import net.kyori.adventure.text.event.ClickEvent;
import nl.fenix.core.family.FamilyService;
import nl.fenix.core.family.model.Family;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "Popup" voor een aftakking met te veel families in de naam (A-B-C-D): configureer de nieuwe familie.
 * Klikken op het naamkaartje zet /family rename klaar in de chat.
 */
public final class RenameGui extends Gui {

    public RenameGui(FamilyService service, Family family) {
        super(service, 3, service.raw("gui.rename-title", "&8Nieuwe familie configureren"));
        List<String> info = new ArrayList<>();
        info.add(text("rename-current", "&7Nu: &f%name% &7[%tag%]").replace("%name%", family.getName())
                .replace("%tag%", family.getTag()));
        info.addAll(service.config().getStringList("gui.rename-info"));
        set(11, GuiItems.crest(family.getCrest(), "&d" + family.getName(), info));
        set(13, GuiItems.glow(GuiItems.item(Material.NAME_TAG, text("rename-choose", "&6Kies een nieuwe naam en tag"),
                service.config().getStringList("gui.rename-choose-lore"))), e -> {
            Player player = (Player) e.getWhoClicked();
            closeLater(player);
            player.sendMessage(Text.legacy(service.message("rename-suggest"))
                    .clickEvent(ClickEvent.suggestCommand("/family rename ")));
        });
        set(15, GuiItems.item(Material.CLOCK, text("rename-later", "&7Later"),
                List.of(text("rename-later-lore", "&7Je krijgt dit opnieuw bij het inloggen; /family editor"))),
                e -> closeLater((Player) e.getWhoClicked()));
        fill();
    }
}
