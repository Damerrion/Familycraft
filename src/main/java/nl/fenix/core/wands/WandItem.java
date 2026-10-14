package nl.fenix.core.wands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Alle wands van de addon (uit {@code wands.<id>} in wands.yml). Herkenning gaat via de PersistentDataContainer,
 * nooit via de naam. Het wand-id, de bindingen (slot -> spell), het geselecteerde slot, het select-effect en een
 * eigen naam (/kwa create) staan op het item zelf, zodat ze meegaan als de wand dropt.
 */
public final class WandItem {

    /** Subcommando's van /kwa; een wand mag niet zo heten. */
    public static final Set<String> RESERVED = Set.of("bind", "unbind", "list", "spell", "spells", "spelllist",
            "effect", "create", "info", "dev", "check", "reload", "help", "get", "getwand", "menu");

    /**
     * Namespace van de PDC-keys. Vast op de oude pluginnaam (KingdomWandsAddon), niet op FenixCore: anders worden
     * wands die al in het spel zijn niet meer herkend.
     */
    private static final String NAMESPACE = "kingdomwandsaddon";

    private final WandsModule plugin;
    private final NamespacedKey wandKey;
    private final NamespacedKey bindsKey;
    private final NamespacedKey selectedKey;
    private final NamespacedKey effectKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey createdKey;
    private final Map<String, WandType> types = new LinkedHashMap<>();

    public WandItem(WandsModule plugin) {
        this.plugin = plugin;
        this.wandKey = key("wand");
        this.bindsKey = key("binds");
        this.selectedKey = key("selected");
        this.effectKey = key("effect");
        this.nameKey = key("name");
        this.createdKey = key("created");
        reload();
    }

    static NamespacedKey key(String key) {
        return java.util.Objects.requireNonNull(NamespacedKey.fromString(NAMESPACE + ":" + key));
    }

    /** (Her)laad de wands uit de config. */
    public void reload() {
        types.clear();
        ConfigurationSection section = plugin.getConfig().isSet("wands")
                ? plugin.getConfig().getConfigurationSection("wands") : null;
        if (section == null && plugin.getConfig().getDefaults() != null) {
            // wands.yml zonder (geldige) wands, bv. door een YAML-fout: de standaard-wands uit de jar gebruiken
            plugin.getLogger().warning("Geen geldige 'wands' in wands.yml: de standaard-wands worden gebruikt.");
            section = plugin.getConfig().getDefaults().getConfigurationSection("wands");
        }
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection wandSection = section.getConfigurationSection(id);
            String key = id.toLowerCase(Locale.ROOT);
            // geen subcommando-namen en geen getallen (/kwa 2 is een hulppagina)
            if (wandSection == null || RESERVED.contains(key) || key.contains(" ") || key.matches("\\d+")) {
                plugin.getLogger().warning("Wand '" + id + "' overgeslagen (ongeldige naam of geen sectie).");
                continue;
            }
            types.put(key, WandType.load(key, wandSection, plugin.getLogger()));
        }
    }

    public Collection<WandType> types() {
        return types.values();
    }

    /** @return de wand met dit id (hoofdletterongevoelig), of null. */
    public WandType type(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public int maxSlots() {
        return Math.max(1, Math.min(9, plugin.getConfig().getInt("slots", 9)));
    }

    public ItemStack create(WandType type) {
        ItemStack item = new ItemStack(type.material());
        item.editMeta(meta -> meta.getPersistentDataContainer().set(wandKey, PersistentDataType.STRING, type.id()));
        refresh(item);
        return item;
    }

    /**
     * /kwa create &lt;wand&gt; [naam]: maak van het item in je hand de gekozen wand.
     * Materiaal en model van het item blijven behouden. Naam: MiniMessage of &amp;-kleurcodes; zonder naam de wandnaam.
     */
    public void convert(ItemStack item, WandType type, String rawName) {
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.STRING, type.id());
            pdc.set(createdKey, PersistentDataType.BYTE, (byte) 1);
            pdc.remove(bindsKey);
            pdc.remove(selectedKey);
            if (rawName == null || rawName.isBlank()) {
                pdc.remove(nameKey);
            } else {
                pdc.set(nameKey, PersistentDataType.STRING, rawName);
            }
        });
        refresh(item);
    }

    /** Is dit een wand van deze addon (ook als de wand uit de config is verwijderd)? */
    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
    }

    /** @return het type van deze wand, of null als het geen wand is of de wand niet (meer) in de config staat. */
    public WandType getType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return type(item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.STRING));
    }

    /** @return slot (1-9) -> spell, gesorteerd op slot. */
    public TreeMap<Integer, Spell> getBinds(ItemStack wand) {
        TreeMap<Integer, Spell> binds = new TreeMap<>();
        String raw = wand.getItemMeta().getPersistentDataContainer().get(bindsKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return binds;
        }
        for (String entry : raw.split(",")) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                Spell spell = Spell.fromName(parts[1]);
                int slot = Integer.parseInt(parts[0]);
                // slots boven het maximum (bv. uit een oudere versie met meer slots) negeren
                if (spell != null && slot >= 1 && slot <= maxSlots()) {
                    binds.put(slot, spell);
                }
            } catch (NumberFormatException ignored) {
                // corrupte entry overslaan
            }
        }
        return binds;
    }

    public void setBinds(ItemStack wand, Map<Integer, Spell> binds) {
        StringBuilder raw = new StringBuilder();
        for (Map.Entry<Integer, Spell> entry : new TreeMap<>(binds).entrySet()) {
            if (!raw.isEmpty()) {
                raw.append(',');
            }
            raw.append(entry.getKey()).append(':').append(entry.getValue().id());
        }
        wand.editMeta(meta -> meta.getPersistentDataContainer().set(bindsKey, PersistentDataType.STRING, raw.toString()));
        // geselecteerd slot geldig houden
        if (!binds.containsKey(getSelectedSlot(wand))) {
            setSelectedSlot(wand, binds.isEmpty() ? 0 : new TreeMap<>(binds).firstKey());
        }
        refresh(wand);
    }

    public int getSelectedSlot(ItemStack wand) {
        PersistentDataContainer pdc = wand.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(selectedKey, PersistentDataType.INTEGER, 0);
    }

    public void setSelectedSlot(ItemStack wand, int slot) {
        wand.editMeta(meta -> meta.getPersistentDataContainer().set(selectedKey, PersistentDataType.INTEGER, slot));
        refresh(wand);
    }

    /** @return de geselecteerde spell, of null als er niets gebonden is. */
    public Spell getSelectedSpell(ItemStack wand) {
        return getBinds(wand).get(getSelectedSlot(wand));
    }

    /**
     * Ga naar het volgende (of vorige) gebonden slot.
     *
     * @return de nieuwe spell, of null als er niets gebonden is.
     */
    public Spell cycle(ItemStack wand, boolean backwards) {
        TreeMap<Integer, Spell> binds = getBinds(wand);
        if (binds.isEmpty()) {
            return null;
        }
        int current = getSelectedSlot(wand);
        Integer next = backwards ? binds.lowerKey(current) : binds.higherKey(current);
        if (next == null) {
            next = backwards ? binds.lastKey() : binds.firstKey();
        }
        setSelectedSlot(wand, next);
        return binds.get(next);
    }

    /** Select-effect van deze wand; standaard het {@code effect} van het wandtype. */
    public SelectEffect getEffect(ItemStack wand) {
        String raw = wand.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
        SelectEffect effect = SelectEffect.fromName(raw);
        if (effect != null) {
            return effect;
        }
        WandType type = getType(wand);
        return type == null ? SelectEffect.TYGRON : type.defaultEffect();
    }

    public void setEffect(ItemStack wand, SelectEffect effect) {
        wand.editMeta(meta -> meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect.name()));
        refresh(wand);
    }

    /** Naam, model, lore en stapelgrootte opnieuw opbouwen uit het wandtype in de config. */
    public void refresh(ItemStack wand) {
        WandType type = getType(wand);
        PersistentDataContainer data = wand.getItemMeta().getPersistentDataContainer();
        String customName = data.get(nameKey, PersistentDataType.STRING);
        // wands van de oude /kwa create <naam> hebben geen 'created'-markering, maar wel een naam en een eigen model
        boolean legacyCreated = customName != null && !data.has(createdKey, PersistentDataType.BYTE);
        boolean created = legacyCreated || data.has(createdKey, PersistentDataType.BYTE);
        wand.editMeta(meta -> {
            if (legacyCreated) {
                meta.getPersistentDataContainer().set(createdKey, PersistentDataType.BYTE, (byte) 1);
            }
            // een wand is uniek: nooit stapelen (anders bind je een hele stapel en laat Q-droppen een spell afgaan)
            meta.setMaxStackSize(1);
            if (type == null) {
                return; // wand staat niet meer in de config: item laten zoals het is
            }
            String name = customName != null ? customName : type.name();
            meta.displayName(parseName(name));
            if (!created) {
                // met /kwa create gemaakt: eigen model van het item behouden
                applyModel(meta, type.customModelData());
            }
            List<Component> lore = new ArrayList<>();
            for (String line : type.lore()) {
                lore.add(parseName(line));
            }
            meta.lore(lore.isEmpty() ? null : lore);
        });
    }

    /** &amp;-kleurcodes (zoals bij KingdomWands) of MiniMessage. */
    public static Component parseName(String raw) {
        Component name = raw.indexOf('&') >= 0 || raw.indexOf('§') >= 0
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(raw.replace('§', '&'))
                : MiniMessage.miniMessage().deserialize(raw);
        return name.decoration(TextDecoration.ITALIC, false);
    }

    /** Naam zonder opmaak (voor lijsten en hulpteksten). */
    public static String plainName(String raw) {
        return PlainTextComponentSerializer.plainText().serialize(parseName(raw));
    }

    @SuppressWarnings("deprecation")
    private static void applyModel(ItemMeta meta, int model) {
        meta.setCustomModelData(model > 0 ? model : null);
    }
}
