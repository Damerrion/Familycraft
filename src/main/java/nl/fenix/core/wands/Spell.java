package nl.fenix.core.wands;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Een spell uit de registry. Nieuwe spell = een {@link SpellHandler} + {@link #register} (zie spell/Spells).
 * Een wand kiest in wands.yml welke spells hij heeft.
 */
public final class Spell {

    private static final Map<String, Spell> REGISTRY = new LinkedHashMap<>();

    private final String id;
    private final String displayName;
    private final String configKey;
    private final long defaultCooldownSeconds;
    private final SpellHandler handler;
    private final Function<FileConfiguration, String> description;

    private Spell(String displayName, String configKey, long defaultCooldownSeconds, SpellHandler handler,
                  Function<FileConfiguration, String> description) {
        this.id = normalize(displayName);
        this.displayName = displayName;
        this.configKey = configKey;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
        this.handler = handler;
        this.description = description;
    }

    /**
     * Registreer een spell.
     *
     * @param displayName naam zoals in /bind en de chat, bv. "Empire Spark"
     * @param configKey   sectie in wands.yml (bv. "scream" of "spells.empire-spark")
     */
    public static Spell register(String displayName, String configKey, long defaultCooldownSeconds,
                                 SpellHandler handler, Function<FileConfiguration, String> description) {
        Spell spell = new Spell(displayName, configKey, defaultCooldownSeconds, handler, description);
        REGISTRY.put(spell.id, spell);
        return spell;
    }

    public static void clear() {
        REGISTRY.clear();
    }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Zoek op naam: hoofdletters, spaties en streepjes maken niet uit ("EmpireSpark" = "empire spark" = "EMPIRE_SPARK"). */
    public static Spell fromName(String name) {
        return name == null ? null : REGISTRY.get(normalize(name));
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Vaste id, opgeslagen op de wand (bindingen). */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Sectie in wands.yml. */
    public String configKey() {
        return configKey;
    }

    public long defaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    public SpellHandler handler() {
        return handler;
    }

    public String description(FileConfiguration config) {
        return description.apply(config);
    }

    /** Zelfde opbouw als KingdomWands (kingdomwands.spells.EmpireExplosive): kwa.spells.Scream, kwa.spells.EmpireSpark. */
    public String permission() {
        return "kwa.spells." + displayName.replace(" ", "");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
