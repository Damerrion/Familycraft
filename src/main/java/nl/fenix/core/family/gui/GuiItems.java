package nl.fenix.core.family.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Items voor de familie-GUI's. */
public final class GuiItems {

    private GuiItems() {
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(lore.isEmpty() ? null : Text.lines(lore));
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        });
        return item;
    }

    public static ItemStack glow(ItemStack item) {
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return item;
    }

    /**
     * Spelershoofd op basis van alleen de (echte) UUID. Niet via de online speler: een disguise/skin-plugin
     * (bv. als RP-character) past diens profiel aan, en dan zou het hoofd de vermomming laten zien. Met alleen de
     * UUID haalt de client de echte skin zelf op.
     */
    public static ItemStack head(UUID player, String name, List<String> lore) {
        ItemStack item = item(Material.PLAYER_HEAD, name, lore);
        item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(player)));
        return item;
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    // ---- familiewapen (banner) ----

    public static boolean isBanner(ItemStack item) {
        return item != null && !item.getType().isAir() && Tag.ITEMS_BANNERS.isTagged(item.getType());
    }

    public static String encodeCrest(ItemStack banner) {
        return Base64.getEncoder().encodeToString(banner.asOne().serializeAsBytes());
    }

    /** Het familiewapen met naam en lore; zonder wapen een witte banner. */
    public static ItemStack crest(String encoded, String name, List<String> lore) {
        ItemStack banner = decodeCrest(encoded);
        if (!isBanner(banner)) {
            banner = new ItemStack(Material.WHITE_BANNER);
        }
        banner.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(lore.isEmpty() ? null : Text.lines(lore));
            // patronen van de banner niet als tekst onder de naam
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return banner;
    }

    /** Opgeslagen familiewapen terug naar een item; null als er geen is of hij kapot is. */
    public static ItemStack decodeCrest(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException ignored) {
            return null; // kapot of van een andere versie: standaard-banner
        }
    }
}
