package nl.fenix.core.family;

import nl.fenix.core.family.gui.GuiItems;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verboden familiewapens (bv. hakenkruizen) in family.yml onder {@code banned.crests}. Een wapen wordt herkend aan
 * zijn patronen in volgorde, met de kleuren als "kleur 1, kleur 2, ..." (op volgorde van voorkomen, de
 * achtergrond eerst). Zo is hetzelfde teken in andere kleuren ook verboden, maar een ander teken niet.
 */
public final class CrestBans {

    public static final String PATH = "banned.crests";

    private final FamilyModule module;

    public CrestBans(FamilyModule module) {
        this.module = module;
    }

    /** Vingerafdruk van een banner, of null als het geen banner is of hij geen patronen heeft. */
    public static String fingerprint(ItemStack banner) {
        if (!GuiItems.isBanner(banner) || !(banner.getItemMeta() instanceof BannerMeta meta)
                || meta.getPatterns().isEmpty()) {
            return null;
        }
        Map<String, Integer> colors = new LinkedHashMap<>();
        String base = banner.getType().name().replace("_BANNER", "");
        colors.put(base, 0);
        StringBuilder sb = new StringBuilder("0");
        for (Pattern pattern : meta.getPatterns()) {
            String color = pattern.getColor().name();
            int index = colors.computeIfAbsent(color, c -> colors.size());
            sb.append('|').append(index).append('/').append(patternKey(pattern));
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static String patternKey(Pattern pattern) {
        try {
            return pattern.getPattern().getKey().getKey().toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError ex) {
            return String.valueOf(pattern.getPattern()).toLowerCase(Locale.ROOT);
        }
    }

    public List<String> list() {
        return new ArrayList<>(module.getConfig().getStringList(PATH));
    }

    public boolean isBanned(ItemStack banner) {
        String fingerprint = fingerprint(banner);
        return fingerprint != null && list().contains(fingerprint);
    }

    /** Opgeslagen wapen (base64) verboden? */
    public boolean isBannedCrest(String encoded) {
        return encoded != null && isBanned(GuiItems.decodeCrest(encoded));
    }

    /** @return false als hij er al op stond */
    public boolean add(String fingerprint) {
        List<String> list = list();
        if (list.contains(fingerprint)) {
            return false;
        }
        list.add(fingerprint);
        module.setAndSave(PATH, list);
        return true;
    }

    /** @return false als hij er niet op stond */
    public boolean remove(String fingerprint) {
        List<String> list = list();
        if (!list.remove(fingerprint)) {
            return false;
        }
        module.setAndSave(PATH, list);
        return true;
    }
}
