package nl.fenix.core.wands.spell;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.fenix.core.wands.WandsModule;
import nl.fenix.core.wands.SpellHandler;
import nl.fenix.core.wands.WandType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mark: kiest willekeurig een aantal vijanden binnen de radius en geeft ze Glowing + Blindness.
 */
public final class MarkSpell implements SpellHandler {

    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.4f);

    private final WandsModule plugin;

    public MarkSpell(WandsModule plugin) {
        this.plugin = plugin;
    }

    /** @return false als er geen doelen waren (dan geen cooldown). */
    @Override
    public boolean cast(Player caster, WandType type) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("mark");
        double radius = cfg == null ? 50 : cfg.getDouble("radius", 50);
        int maxTargets = cfg == null ? 5 : cfg.getInt("targets", 5);
        // aparte duur per effect; zonder eigen standaardwaarde, zodat een oude wands.yml de waarden uit de jar gebruikt
        int glowingTicks = Math.max(0, plugin.getConfig().getInt("mark.glowing-seconds")) * 20;
        int blindnessTicks = Math.max(0, plugin.getConfig().getInt("mark.blindness-seconds")) * 20;
        boolean blindness = cfg == null || cfg.getBoolean("blindness", true);
        boolean glowing = cfg == null || cfg.getBoolean("glowing", true);

        Location origin = caster.getLocation();
        double radiusSq = radius * radius;
        List<LivingEntity> candidates = new ArrayList<>();
        // normaal alleen spelers; in dev mode (dev.hit-mobs) ook mobs
        Collection<? extends LivingEntity> nearby = plugin.dev(caster, "hit-mobs")
                ? caster.getWorld().getNearbyLivingEntities(origin, radius)
                : caster.getWorld().getPlayers();
        for (LivingEntity target : nearby) {
            if (target.getLocation().distanceSquared(origin) <= radiusSq && plugin.affects(caster, target, type)) {
                candidates.add(target);
            }
        }
        plugin.devLog(caster, "Mark: " + candidates.size() + " mogelijke doelwitten binnen " + (int) radius + " blokken");
        if (candidates.isEmpty()) {
            plugin.msg(caster, "<gray>Geen doelwitten binnen " + (int) radius + " blokken.");
            return false;
        }
        Collections.shuffle(candidates);
        List<LivingEntity> targets = candidates.subList(0, Math.min(maxTargets, candidates.size()));

        caster.getWorld().playSound(origin, Sound.ENTITY_PHANTOM_AMBIENT, SoundCategory.PLAYERS, 2f, 0.5f);
        for (LivingEntity target : targets) {
            if (glowing && glowingTicks > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowingTicks, 0, false, false, true));
            }
            if (blindness && blindnessTicks > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, false, true));
            }
            Location at = target.getLocation().add(0, 1, 0);
            target.getWorld().spawnParticle(Particle.DUST, at, 40, 0.4, 0.8, 0.4, 0, BLACK);
            target.getWorld().spawnParticle(Particle.SQUID_INK, at.add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.02);
            if (target instanceof Player player) {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.5f, 0.6f);
                plugin.msg(player, "<dark_red>Je bent gemarkeerd door de dodenfluit.");
            }
        }
        // namen escapen: mobs kunnen een eigen naam (naamkaartje) met MiniMessage-tags hebben
        String names = targets.stream().map(e -> MiniMessage.miniMessage().escapeTags(e.getName()))
                .collect(Collectors.joining(", "));
        plugin.msg(caster, "<dark_red>Gemarkeerd (" + targets.size() + "): <red>" + names);
        return true;
    }
}
