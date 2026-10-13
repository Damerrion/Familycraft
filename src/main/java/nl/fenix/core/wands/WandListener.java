package nl.fenix.core.wands;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Besturing zoals KingdomWands: rechtermuis = volgende spell selecteren, shift + rechtermuis = vorige,
 * linkermuis = casten. /bind en /unbind werken ook zonder /kwa zolang je een wand van deze addon vasthoudt
 * (of altijd, als geen andere plugin zoals KingdomWands /bind heeft).
 */
public final class WandListener implements Listener {

    /** Een klik kan meerdere events geven (bv. slaan buiten bereik + arm-zwaai): niet dubbel afhandelen. */
    private static final long DEBOUNCE_MS = 150;

    private final WandsModule plugin;
    private final KwaCommand command;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public WandListener(WandsModule plugin, KwaCommand command) {
        this.plugin = plugin;
        this.command = command;
    }

    /** Heeft de speler een bruikbare wand in de hoofdhand? Zonder kwa.use is het gewoon een normaal item. */
    private boolean holdsUsableWand(Player player) {
        WandType type = plugin.wand().getType(player.getInventory().getItemInMainHand());
        return type != null && plugin.canUse(player, type);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            // Rechtermuis met de wand: het item in je andere hand niet gebruiken (parel, blok, eten)
            if (holdsUsableWand(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !holdsUsableWand(player)) {
            return;
        }
        // Het echte item in de hand (geen kopie), zodat het geselecteerde slot op de wand wordt opgeslagen.
        ItemStack item = player.getInventory().getItemInMainHand();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            select(event.getPlayer(), item);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            cast(event.getPlayer(), item);
        }
    }

    /** Rechtermuis op een speler/entity: ook selecteren. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityClick(event);
    }

    /** Armor stands (en klikken op een precieze plek) hebben een eigen event; anders komt de wand op de stand. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityClick(event);
    }

    private void handleEntityClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!holdsUsableWand(player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            // de client stuurt vaak zowel INTERACT_AT als INTERACT: de debounce voorkomt dubbel wisselen
            select(player, player.getInventory().getItemInMainHand());
        }
    }

    /**
     * Linkermuis op een speler/entity: casten in plaats van slaan. PrePlayerAttackEntityEvent komt vóór elk
     * damage-event, dus ook als KingdomCraft het slaan van een teamgenoot/bondgenoot blokkeert, en Thorns-schade
     * (waarbij de drager zelf als "damager" telt) laat geen spell afgaan.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (!holdsUsableWand(player)) {
            return;
        }
        event.setCancelled(true);
        cast(player, player.getInventory().getItemInMainHand());
    }

    private boolean debounced(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastUse.put(player.getUniqueId(), now);
        return last != null && now - last < DEBOUNCE_MS;
    }

    private void select(Player player, ItemStack item) {
        if (debounced(player)) {
            return;
        }
        WandItem wand = plugin.wand();
        Spell next = wand.cycle(item, player.isSneaking());
        if (next == null) {
            // zoals KingdomWands: het effect speelt ook als er niets gebonden is
            wand.getEffect(item).play(player);
            plugin.say(player, "no-spells");
            return;
        }
        // eerst toon en effect, dan de melding in de chat ("[KWA] Scream Geselecteerd"), zoals KingdomWands.
        // Elke spell kan een eigen switch-toon hebben; anders het geluid van het select-effect.
        boolean ownTone = plugin.playSelectSound(player, next);
        wand.getEffect(item).play(player, !ownTone);
        plugin.say(player, "selected", Placeholder.unparsed("spell", next.displayName()),
                Placeholder.unparsed("slot", String.valueOf(wand.getSelectedSlot(item))));
    }

    private void cast(Player player, ItemStack item) {
        if (debounced(player)) {
            return;
        }
        WandType type = plugin.wand().getType(item);
        Spell spell = plugin.wand().getSelectedSpell(item);
        if (type == null || spell == null) {
            return; // niets gebonden: linkermuis doet niets (zoals KingdomWands); rechtermuis meldt het
        }
        plugin.cast(player, spell, type);
    }

    /**
     * /bind en /unbind zoals bij KingdomWands. Alleen als je een wand van deze addon vasthoudt, zodat het /bind
     * van KingdomWands zelf voor hun wands gewoon blijft werken.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String[] parts = message.substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (!label.equals("bind") && !label.equals("unbind")) {
            return;
        }
        Player player = event.getPlayer();
        // Met een wand van deze addon in de hand altijd zelf afhandelen. Zonder: alleen als geen andere plugin
        // (zoals KingdomWands) /bind heeft, dan met de melding dat je geen geldige wand vasthoudt.
        if (!plugin.wand().isWand(player.getInventory().getItemInMainHand())
                && plugin.getServer().getCommandMap().getCommand(label) != null) {
            return;
        }
        event.setCancelled(true);
        String[] args = new String[parts.length];
        args[0] = label;
        System.arraycopy(parts, 1, args, 1, parts.length - 1);
        command.run(player, "kwa", args);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** Wand stil bijwerken naar de huidige config (naam, lore) als je hem vastpakt. Geen melding. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (plugin.wand().isWand(item)) {
            plugin.wand().refresh(item);
        }
    }

    /** Met een wand in de hand breek je geen blokken (linkermuis is casten), ook niet in creative. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (holdsUsableWand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Ook de Crafter-blok (1.21) mag de wand niet vercraften. */
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter
                && Arrays.stream(crafter.getInventory().getContents()).anyMatch(plugin.wand()::isWand)) {
            event.setCancelled(true);
        }
    }

    /** Voorkom dat de wand ergens in gecraft wordt (bv. bot -> beenmeel). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (Arrays.stream(event.getInventory().getMatrix()).anyMatch(plugin.wand()::isWand)) {
            event.getInventory().setResult(null);
        }
    }
}
