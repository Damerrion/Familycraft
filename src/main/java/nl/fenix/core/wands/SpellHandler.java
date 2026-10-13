package nl.fenix.core.wands;

import org.bukkit.entity.Player;

/** Wat een spell doet als hij gecast wordt. */
@FunctionalInterface
public interface SpellHandler {

    /** @return true als de spell gecast is (dan start de cooldown), false als er niets gebeurde */
    boolean cast(Player caster, WandType wand);
}
