package nl.fenix.core.wands.spell;

import nl.fenix.core.wands.WandsModule;
import nl.fenix.core.wands.Spell;

/**
 * Alle spells van de addon. Nieuwe spell: een class die {@link nl.fenix.core.wands.SpellHandler} implementeert en hier
 * registreren. Permissie kwa.spells.&lt;Naam&gt; wordt automatisch aangemaakt.
 */
public final class Spells {

    private Spells() {
    }

    public static void registerAll(WandsModule plugin) {
        Spell.clear();

        // Tygron Deathwhistle
        Spell.register("Scream", "scream", 90, new ScreamSpell(plugin), config ->
                "Fear (Slowness + Weakness) op niet-bondgenoten binnen "
                        + config.getInt("scream.effect-radius", 30) + " blokken");
        Spell.register("Mark", "mark", 150, new MarkSpell(plugin), config ->
                "Glowing (" + config.getInt("mark.glowing-seconds") + "s) + Blindness ("
                        + config.getInt("mark.blindness-seconds") + "s) op " + config.getInt("mark.targets", 5)
                        + " willekeurige niet-bondgenoten binnen " + config.getInt("mark.radius", 50) + " blokken");
    }
}
