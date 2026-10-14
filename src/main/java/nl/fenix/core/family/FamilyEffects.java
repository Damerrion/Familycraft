package nl.fenix.core.family;

import net.kyori.adventure.title.Title;
import nl.fenix.core.family.gui.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Het "epische" deel: titels, geluiden, deeltjes en aankondigingen bij de grote momenten van een familie
 * (gesticht, lid geworden, rang, nieuw hoofd, verloofd, getrouwd, wapen, gevallen). Alles uit te zetten in
 * family.yml onder {@code effects}; titels staan onder {@code messages.title-<moment>} / {@code subtitle-<moment>}.
 */
public final class FamilyEffects {

    /** Momenten; de naam is de sleutel in family.yml (effects.sounds.&lt;moment&gt;, messages.title-&lt;moment&gt;). */
    public enum Moment {
        CREATED("created", Particle.TOTEM_OF_UNDYING),
        JOINED("joined", Particle.HAPPY_VILLAGER),
        RANK("rank", Particle.END_ROD),
        NEW_HEAD("new-head", Particle.TOTEM_OF_UNDYING),
        ENGAGED("engaged", Particle.HEART),
        WEDDING("wedding", Particle.HEART),
        CREST("crest", Particle.ENCHANT),
        DISBANDED("disbanded", Particle.LARGE_SMOKE);

        private final String key;
        private final Particle particle;

        Moment(String key, Particle particle) {
            this.key = key;
            this.particle = particle;
        }
    }

    private final FamilyService service;

    FamilyEffects(FamilyService service) {
        this.service = service;
    }

    private FileConfiguration config() {
        return service.config();
    }

    private boolean enabled() {
        return config().getBoolean("effects.enabled", true);
    }

    /** Titel + geluid + deeltjes voor één speler. */
    public void play(Player player, Moment moment, String... replacements) {
        if (player == null || !player.isOnline() || !enabled()) {
            return;
        }
        title(player, moment, replacements);
        sound(player, "effects.sounds." + moment.key);
        if (config().getBoolean("effects.particles", true)) {
            Location at = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(moment.particle, at, moment == Moment.CREATED || moment == Moment.NEW_HEAD ? 60 : 25,
                    0.6, 0.8, 0.6, moment == Moment.CREATED || moment == Moment.NEW_HEAD ? 0.4 : 0.05);
            if (moment == Moment.WEDDING) {
                player.getWorld().spawnParticle(Particle.FIREWORK, at.clone().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.15);
            }
        }
    }

    /** Alleen het geluid (bv. bij het openen van een menu). */
    public void sound(Player player, String path) {
        if (player == null || !enabled()) {
            return;
        }
        String sound = config().getString(path, "");
        if (sound == null || sound.isBlank()) {
            return;
        }
        for (String key : sound.split(",")) {
            if (key.isBlank()) {
                continue;
            }
            try {
                player.playSound(player.getLocation(), key.trim(), SoundCategory.PLAYERS, 1f, 1f);
            } catch (RuntimeException ignored) {
                // ongeldige key: dit geluid overslaan
            }
        }
    }

    private void title(Player player, Moment moment, String... replacements) {
        String title = service.raw("messages.title-" + moment.key, "");
        String subtitle = service.raw("messages.subtitle-" + moment.key, "");
        if (title.isEmpty() && subtitle.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            title = title.replace(replacements[i], replacements[i + 1]);
            subtitle = subtitle.replace(replacements[i], replacements[i + 1]);
        }
        player.showTitle(Title.title(Text.legacy(title), Text.legacy(subtitle),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))));
    }

    /** Bericht aan de hele server (effects.announce-&lt;moment&gt;), bv. een nieuw huis of een huwelijk. */
    public void announce(Moment moment, String... replacements) {
        if (!config().getBoolean("effects.announce-" + moment.key, false)) {
            return;
        }
        String line = service.message("announce-" + moment.key, replacements);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(line);
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
