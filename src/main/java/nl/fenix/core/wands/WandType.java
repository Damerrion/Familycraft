package nl.fenix.core.wands;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Een wand uit wands.yml ({@code wands.<id>}). Nieuwe wands toevoegen = een nieuw blok in de config;
 * alleen nieuwe spells vragen om code (zie {@link Spell}).
 */
public final class WandType {

    private final String id;
    private final String name;
    private final List<String> lore;
    private final Material material;
    private final int customModelData;
    private final String kingdom;
    private final SelectEffect effect;
    private final List<Spell> spells;

    private WandType(String id, String name, List<String> lore, Material material, int customModelData,
                     String kingdom, SelectEffect effect, List<Spell> spells) {
        this.id = id;
        this.name = name;
        this.lore = lore;
        this.material = material;
        this.customModelData = customModelData;
        this.kingdom = kingdom;
        this.effect = effect;
        this.spells = spells;
    }

    /** Lees een wand uit de config. Ongeldige waarden worden gemeld en vervangen door een standaardwaarde. */
    public static WandType load(String id, ConfigurationSection section, Logger logger) {
        String key = id.toLowerCase(Locale.ROOT);
        Material material = Material.matchMaterial(section.getString("material", "BONE"));
        if (material == null || !material.isItem() || material.isAir()) {
            logger.warning("Wand " + key + ": ongeldig material, BONE wordt gebruikt.");
            material = Material.BONE;
        }
        SelectEffect effect = SelectEffect.fromName(section.getString("effect", "TYGRON"));
        if (effect == null) {
            logger.warning("Wand " + key + ": onbekend effect, TYGRON wordt gebruikt.");
            effect = SelectEffect.TYGRON;
        }
        List<Spell> spells = new ArrayList<>();
        for (String raw : section.getStringList("spells")) {
            Spell spell = Spell.fromName(raw);
            if (spell == null) {
                logger.warning("Wand " + key + ": onbekende spell '" + raw + "' overgeslagen.");
            } else if (!spells.contains(spell)) {
                spells.add(spell);
            }
        }
        if (spells.isEmpty()) {
            logger.warning("Wand " + key + ": heeft geen (geldige) spells.");
        }
        String kingdom = section.getString("kingdom", "");
        return new WandType(key, section.getString("name", key), List.copyOf(section.getStringList("lore")),
                material, section.getInt("custom-model-data", 0), kingdom == null ? "" : kingdom.trim(),
                effect, List.copyOf(spells));
    }

    /** Id zoals in /kwa &lt;id&gt; en kwa.wand.&lt;id&gt;. */
    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public Material material() {
        return material;
    }

    public int customModelData() {
        return customModelData;
    }

    /** Leden en bondgenoten van dit kingdom worden niet geraakt. Leeg = iedereen wordt geraakt. */
    public String kingdom() {
        return kingdom;
    }

    public SelectEffect defaultEffect() {
        return effect;
    }

    public List<Spell> spells() {
        return spells;
    }

    /** Zoals kingdomwands.wand.&lt;wand&gt;: de wand krijgen (/kwa &lt;id&gt;, /kwa create &lt;id&gt;). */
    public String permission() {
        return "kwa.wand." + id;
    }

    /** De wand gebruiken (selecteren en casten). kwa.use geeft alle wands. */
    public String usePermission() {
        return "kwa.use." + id;
    }
}
