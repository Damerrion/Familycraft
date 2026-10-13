package nl.fenix.core.wands;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Effect bij het selecteren van een spell, te wijzigen met /kwa effect &lt;effect&gt; (zoals /kw effect bij KingdomWands). */
public enum SelectEffect {
    TYGRON, BLOOD, DARK, SHADOW, FLAME, ICE, NOTHING;

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.3f);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.3f);

    public static SelectEffect fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Speel het effect af rond de speler (deeltjes + geluid). */
    public void play(Player player) {
        play(player, true);
    }

    /** @param withSound false als de spell al een eigen switch-toon speelt */
    public void play(Player player, boolean withSound) {
        if (this == NOTHING) {
            return;
        }
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1.0, 0);
        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * (Math.PI * 2 / points);
            Location p = center.clone().add(Math.cos(angle) * 0.8, 0, Math.sin(angle) * 0.8);
            switch (this) {
                case TYGRON -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, BLACK);
                case BLOOD -> world.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, RED);
                case DARK -> world.spawnParticle(Particle.SMOKE, p, 1, 0, 0, 0, 0);
                case SHADOW -> world.spawnParticle(Particle.PORTAL, p, 2, 0, 0, 0, 0.1);
                case FLAME -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0, 0, 0, 0);
                case ICE -> world.spawnParticle(Particle.SNOWFLAKE, p, 1, 0, 0, 0, 0);
                default -> { }
            }
        }
        if (!withSound) {
            if (this == TYGRON) {
                world.spawnParticle(Particle.SQUID_INK, center, 4, 0.2, 0.2, 0.2, 0.01);
            } else if (this == DARK) {
                world.spawnParticle(Particle.SOUL, center, 3, 0.3, 0.3, 0.3, 0.01);
            } else if (this == SHADOW) {
                world.spawnParticle(Particle.LARGE_SMOKE, center, 4, 0.3, 0.3, 0.3, 0.01);
            }
            return;
        }
        switch (this) {
            case TYGRON -> {
                world.spawnParticle(Particle.SQUID_INK, center, 4, 0.2, 0.2, 0.2, 0.01);
                world.playSound(center, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.PLAYERS, 0.8f, 0.5f);
            }
            case BLOOD -> world.playSound(center, Sound.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 0.8f, 0.5f);
            case DARK -> {
                world.spawnParticle(Particle.SOUL, center, 3, 0.3, 0.3, 0.3, 0.01);
                world.playSound(center, Sound.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1f, 0.7f);
            }
            case SHADOW -> {
                world.spawnParticle(Particle.LARGE_SMOKE, center, 4, 0.3, 0.3, 0.3, 0.01);
                world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 0.6f);
            }
            case FLAME -> world.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.5f, 1.2f);
            case ICE -> world.playSound(center, Sound.BLOCK_POWDER_SNOW_STEP, SoundCategory.PLAYERS, 1f, 0.8f);
            default -> { }
        }
    }
}
