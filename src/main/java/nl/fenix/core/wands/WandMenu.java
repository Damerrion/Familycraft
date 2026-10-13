package nl.fenix.core.wands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wand-menu (/kwa get), opgezet zoals /kw getwand van KingdomWands: 36 slots, rode glasruiten,
 * wands op vaste plekken, "Informatie" in het midden en "Sluit Menu" onderaan.
 * Herkend via de holder (niet de titel); alleen klikken in het menu zelf tellen.
 */
public final class WandMenu implements InventoryHolder {

    /** Plekken voor de wands, zoals in KingdomWands; daarna de overige vrije plekken. */
    private static final int[] WAND_SLOTS = {1, 3, 5, 7, 9, 11, 15, 17, 21, 23, 29, 33,
            19, 25, 27, 35, 0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34};
    private static final int INFO_SLOT = 13;
    private static final int CLOSE_SLOT = 31;

    private final Inventory inventory;
    private final Map<Integer, WandType> wandsBySlot = new HashMap<>();

    private WandMenu(WandsModule plugin) {
        MiniMessage mm = MiniMessage.miniMessage();
        this.inventory = plugin.getServer().createInventory(this, 36,
                mm.deserialize("<gold><bold>FenixCore Wands"));
        ItemStack pane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, pane);
        }
        int index = 0;
        for (WandType type : plugin.wand().types()) {
            if (index >= WAND_SLOTS.length) {
                break;
            }
            int slot = WAND_SLOTS[index++];
            Component name = WandItem.parseName(type.name());
            inventory.setItem(slot, icon(type.material(), name, mm.deserialize(
                    "<yellow>Door hierop te klikken ontvang je de <wand>", Placeholder.component("wand", name))));
            wandsBySlot.put(slot, type);
        }
        inventory.setItem(INFO_SLOT, icon(Material.REDSTONE_BLOCK, mm.deserialize("<gold>Informatie"),
                mm.deserialize("<yellow>Klik hierop voor informatie over de plugin")));
        inventory.setItem(CLOSE_SLOT, icon(Material.BARRIER, mm.deserialize("<gold>Sluit Menu"),
                mm.deserialize("<yellow>Door hierop te klikken sluit je dit menu")));
    }

    public static void open(WandsModule plugin, Player player) {
        player.openInventory(new WandMenu(plugin).getInventory());
    }

    private static ItemStack icon(Material material, Component name, Component lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(lore.decoration(TextDecoration.ITALIC, false)));
        });
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Klikken in het menu afhandelen. */
    public static final class Clicks implements Listener {

        private final WandsModule plugin;
        private final KwaCommand command;

        public Clicks(WandsModule plugin, KwaCommand command) {
            this.plugin = plugin;
            this.command = command;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder(false) instanceof WandMenu menu)) {
                return;
            }
            // niets uit of in het menu slepen, ook niet met shift-klik vanuit je eigen inventory
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.inventory) {
                return;
            }
            int slot = event.getRawSlot();
            WandType type = menu.wandsBySlot.get(slot);
            if (slot == INFO_SLOT) {
                player.closeInventory();
                command.info(player);
            } else if (type != null) {
                if (!player.hasPermission(type.permission())) {
                    plugin.sayPlain(player, "no-wand-permission");
                    return;
                }
                player.closeInventory();
                command.give(player, player, type);
            } else {
                player.closeInventory();
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder(false) instanceof WandMenu) {
                event.setCancelled(true);
            }
        }
    }
}
