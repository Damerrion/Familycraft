package nl.fenix.core.family.gui;

import nl.fenix.core.family.FamilyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Basis voor de familie-GUI's: herkend via de holder (niet de titel), klik per slot. */
public abstract class Gui implements InventoryHolder {

    protected final FamilyService service;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Gui(FamilyService service, int rows, String title) {
        this.service = service;
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.item(title));
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    /** Lege plekken opvullen met grijze ruiten. */
    protected void fill() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, GuiItems.filler());
            }
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Een ander menu openen vanuit een klik: pas de volgende tick (niet tijdens het klik-event). */
    protected void openLater(Player player, Gui next) {
        Bukkit.getScheduler().runTask(service.module().plugin(), () -> next.open(player));
    }

    protected void closeLater(Player player) {
        Bukkit.getScheduler().runTask(service.module().plugin(), player::closeInventory);
    }

    /** Tekst uit {@code gui.<key>} in family.yml. */
    protected String text(String key, String def) {
        return service.raw("gui." + key, def);
    }

    void handle(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
