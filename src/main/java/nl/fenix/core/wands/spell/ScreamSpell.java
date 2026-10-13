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
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scream: de dodenfluit schreeuwt. Zwarte deeltjes draaien in een cirkel hoog de lucht in (scream.particle-height),
 * ontploffen boven het slagveld en verspreiden zich als golf. Vijanden die de golf raakt krijgen "Fear".
 */
public final class ScreamSpell implements SpellHandler {

    // groot genoeg om ook van ver (boven een slagveld) te zien
    private static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 2.5f);
    private static final Particle.DustOptions DARK_RED = new Particle.DustOptions(Color.fromRGB(90, 0, 0), 2.0f);

    /** Blokken per tick dat de spiraal stijgt (20 blokken = 2 seconden). */
    private static final double RISE_SPEED = 0.5;
    /** Blokken per tick dat de golf uitzet. */
    private static final double WAVE_SPEED = 1.5;
    /** Hoogte boven de grond waar de golf aan de rand eindigt. */
    private static final double WAVE_END_HEIGHT = 2.0;

    private final WandsModule plugin;

    public ScreamSpell(WandsModule plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean cast(Player caster, WandType type) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("scream");
        double hearRadius = cfg == null ? 100 : cfg.getDouble("hear-radius", 100);
        double effectRadius = cfg == null ? 30 : cfg.getDouble("effect-radius", 30);
        // zonder eigen standaardwaarde: een oude wands.yml gebruikt dan de waarde uit de jar
        double height = Math.max(3, plugin.getConfig().getDouble("scream.particle-height"));
        int riseTicks = (int) Math.ceil(height / RISE_SPEED);

        Location origin = caster.getLocation();
        playScream(origin, hearRadius);
        plugin.msg(caster, "<dark_red>De dodenfluit schreeuwt...");

        new BukkitRunnable() {
            private int tick = 0;
            private double waveRadius = 0;
            private final Set<UUID> hit = new HashSet<>();
            private final List<String> hitNames = new ArrayList<>();

            @Override
            public void run() {
                World world = origin.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }
                if (tick < riseTicks) {
                    spiral(origin, tick, riseTicks, height);
                } else if (tick == riseTicks) {
                    explode(origin.clone().add(0, height, 0));
                } else {
                    waveRadius += WAVE_SPEED;
                    wave(origin, waveRadius, effectRadius, height);
                    applyFearInRing(caster, type, origin, waveRadius, hit, hitNames);
                    if (waveRadius >= effectRadius) {
                        plugin.devLog(caster, "Scream raakte " + hitNames.size()
                                + (hitNames.isEmpty() ? "" : ": <white>" + String.join(", ", hitNames)));
                        cancel();
                        return;
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin.plugin(), 0L, 1L);
        return true;
    }

    /** Hoog volume, per speler binnen hear-radius, lage pitch. Mix van warden, ghast en phantom. */
    private void playScream(Location origin, double hearRadius) {
        World world = origin.getWorld();
        double radiusSq = hearRadius * hearRadius;
        for (Player listener : world.getPlayers()) {
            double distSq = listener.getLocation().distanceSquared(origin);
            if (distSq > radiusSq) {
                continue;
            }
            // Geluid op de positie van de luisteraar, richting de caster, zodat het tot hear-radius hoorbaar is
            // (normaal is een geluid maar 16 * volume blokken hoorbaar).
            Location at = listener.getLocation();
            double dist = Math.sqrt(distSq);
            if (dist > 8) {
                at = listener.getLocation().add(origin.toVector().subtract(listener.getLocation().toVector())
                        .normalize().multiply(8));
            }
            float volume = (float) Math.max(0.35, 1.0 - dist / hearRadius) * 2.0f;
            listener.playSound(at, Sound.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, volume, 0.6f);
            listener.playSound(at, Sound.ENTITY_GHAST_SCREAM, SoundCategory.PLAYERS, volume, 0.5f);
            listener.playSound(at, Sound.ENTITY_PHANTOM_AMBIENT, SoundCategory.PLAYERS, volume, 0.5f);
        }
    }

    /**
     * Deeltjes met force = true: Minecraft stuurt ze dan naar spelers tot 512 blokken ver en de client toont ze
     * ook buiten de normale 32 blokken. Zo is de schreeuw boven een slagveld te zien.
     */
    private static void far(World world, Particle particle, Location at, int count, double spread, double extra,
                            Object data) {
        world.spawnParticle(particle, at, count, spread, spread, spread, extra, data, true);
    }

    /** Zwarte deeltjes in een cirkel die hoog de lucht in draaien en spinnen. */
    private void spiral(Location origin, int tick, int riseTicks, double height) {
        World world = origin.getWorld();
        double progress = tick / (double) riseTicks;
        double y = progress * height + 0.2;
        double radius = 2.0 - progress * 1.2;
        double spin = tick * 0.45;
        int arms = 6;
        for (int i = 0; i < arms; i++) {
            double angle = spin + i * (Math.PI * 2 / arms);
            Location p = origin.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            far(world, Particle.DUST, p, 2, 0.05, 0, BLACK);
            far(world, Particle.LARGE_SMOKE, p, 1, 0, 0.01, null);
        }
        if (tick % 2 == 0) {
            far(world, Particle.SQUID_INK, origin.clone().add(0, y, 0), 4, 0.25, 0.02, null);
        }
    }

    /** Grote ontploffing hoog in de lucht, zonder schade. */
    private void explode(Location at) {
        World world = at.getWorld();
        far(world, Particle.EXPLOSION_EMITTER, at, 1, 0, 0, null);
        far(world, Particle.SQUID_INK, at, 200, 2.0, 0.35, null);
        far(world, Particle.DUST, at, 300, 3.0, 0, BLACK);
        far(world, Particle.LARGE_SMOKE, at, 80, 2.0, 0.08, null);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4f, 0.5f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4f, 0.6f);
    }

    /** De golf verspreidt zich vanaf de ontploffing als een koepel die naar de rand toe zakt. */
    private void wave(Location origin, double radius, double effectRadius, double height) {
        World world = origin.getWorld();
        int points = (int) Math.min(200, Math.max(32, radius * 7));
        double fraction = Math.min(1, radius / effectRadius);
        double y = height - (height - WAVE_END_HEIGHT) * fraction;
        for (int i = 0; i < points; i++) {
            double angle = i * (Math.PI * 2 / points);
            Location p = origin.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            far(world, Particle.DUST, p, 1, 0.15, 0, i % 4 == 0 ? DARK_RED : BLACK);
        }
    }

    private void applyFearInRing(Player caster, WandType type, Location origin, double radius, Set<UUID> hit,
                                 List<String> hitNames) {
        if (!caster.isOnline()) {
            return;
        }
        double radiusSq = radius * radius;
        List<LivingEntity> newlyHit = new ArrayList<>();
        // normaal alleen spelers; in dev mode (dev.hit-mobs) ook mobs
        Collection<? extends LivingEntity> candidates = plugin.dev(caster, "hit-mobs")
                ? origin.getWorld().getNearbyLivingEntities(origin, radius)
                : origin.getWorld().getPlayers();
        for (LivingEntity target : candidates) {
            if (hit.contains(target.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distanceSquared(origin) > radiusSq) {
                continue;
            }
            if (!plugin.affects(caster, target, type)) {
                continue;
            }
            hit.add(target.getUniqueId());
            // escapen: mobs kunnen een eigen naam (naamkaartje) met MiniMessage-tags hebben
            hitNames.add(MiniMessage.miniMessage().escapeTags(target.getName()));
            newlyHit.add(target);
        }
        for (LivingEntity target : newlyHit) {
            applyFear(target);
        }
    }

    /** "Fear": Slowness + Weakness, en bij spelers een heftige Nausea (geen schud-effect meer). */
    private void applyFear(LivingEntity target) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("scream");
        // zonder eigen standaardwaarde: een oude wands.yml gebruikt dan de waarde uit de jar
        int seconds = Math.max(0, plugin.getConfig().getInt("scream.fear-duration-seconds"));
        int ticks = seconds * 20;
        int slowness = cfg == null ? 1 : cfg.getInt("slowness-amplifier", 1);
        int weakness = cfg == null ? 0 : cfg.getInt("weakness-amplifier", 0);
        // zonder eigen standaardwaarde: een oude wands.yml gebruikt dan de waarde uit de jar
        int nauseaSeconds = Math.max(0, plugin.getConfig().getInt("scream.nausea-seconds"));

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, slowness, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, weakness, false, true, true));
        target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1.8, 0), 15, 0.3, 0.3, 0.3, 0.02);
        if (!(target instanceof Player player)) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1f, 0.8f);
        plugin.msg(player, "<dark_red>Je verstijft van angst...");

        if (nauseaSeconds > 0) {
            // Nausea bouwt in de client langzaam op tot volle sterkte (het level maakt niet uit)
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaSeconds * 20, 0, false, false, true));
        }
    }
}
