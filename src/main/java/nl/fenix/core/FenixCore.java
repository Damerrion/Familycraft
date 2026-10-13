package nl.fenix.core;

import nl.fenix.core.family.FamilyModule;
import nl.fenix.core.wands.WandsModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

/**
 * FenixCore: de FenixKingdom-addon voor KingdomCraft. Bundelt de modules
 * <ul>
 *     <li>wands ({@link WandsModule}, voorheen KingdomWandsAddon): /kwa, /bind, /unbind, config in wands.yml</li>
 *     <li>family ({@link FamilyModule}, voorheen FamilyCraft): /family, config in family.yml, data in families.yml</li>
 * </ul>
 * Aan/uit per module in config.yml. Commando's en permissies zijn hetzelfde gebleven als in de losse plugins.
 */
public final class FenixCore extends JavaPlugin {

    private WandsModule wands;
    private FamilyModule family;

    @Override
    public void onEnable() {
        // instellingen en data van de losse plugins overnemen (alleen de eerste keer: bestaande bestanden blijven)
        adopt("KingdomWandsAddon", "config.yml", WandsModule.CONFIG_FILE);
        adopt("FamilyCraft", "config.yml", FamilyModule.CONFIG_FILE);
        adopt("FamilyCraft", FamilyModule.DATA_FILE, FamilyModule.DATA_FILE);

        saveDefaultConfig();

        if (moduleEnabled("wands", "KingdomWandsAddon")) {
            wands = new WandsModule(this);
            wands.enable();
            getLogger().info("Wands-module aan.");
        } else {
            disabledCommand("kwa", "wands");
        }

        if (moduleEnabled("family", "FamilyCraft")) {
            // pas na het opstarten van alle plugins, zodat KingdomCraft zeker geladen is
            getServer().getScheduler().runTask(this, this::enableFamily);
        } else {
            disabledCommand("family", "family");
        }
    }

    private void enableFamily() {
        try {
            Class.forName("com.gufli.kingdomcraft.api.KingdomCraftProvider", false, getClassLoader());
            FamilyModule module = new FamilyModule(this);
            module.enable();
            family = module;
        } catch (ClassNotFoundException | LinkageError e) {
            getLogger().severe("Family-module uit: KingdomCraft niet gevonden (" + e + ").");
            disabledCommand("family", "family");
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Family-module uit: starten mislukt (staat KingdomCraft aan?)", e);
            disabledCommand("family", "family");
        }
    }

    @Override
    public void onDisable() {
        if (family != null) {
            family.disable();
            family = null;
        }
        if (wands != null) {
            wands.disable();
            wands = null;
        }
    }

    /** Module aan in config.yml, en niet ook nog als losse (oude) plugin geinstalleerd. */
    private boolean moduleEnabled(String module, String oldPlugin) {
        if (!getConfig().getBoolean("modules." + module, true)) {
            getLogger().info(capitalize(module) + "-module staat uit in config.yml.");
            return false;
        }
        if (getServer().getPluginManager().getPlugin(oldPlugin) != null) {
            getLogger().severe(capitalize(module) + "-module NIET gestart: de oude plugin " + oldPlugin
                    + " staat nog in plugins/. Verwijder " + oldPlugin + ".jar (FenixCore vervangt hem) en herstart.");
            return false;
        }
        return true;
    }

    /** Kopieer plugins/&lt;oldPlugin&gt;/&lt;oldFile&gt; naar plugins/FenixCore/&lt;newFile&gt; als die nog niet bestaat. */
    private void adopt(String oldPlugin, String oldFile, String newFile) {
        File target = new File(getDataFolder(), newFile);
        File source = new File(new File(getDataFolder().getParentFile(), oldPlugin), oldFile);
        if (target.exists() || !source.isFile()) {
            return;
        }
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.copy(source.toPath(), target.toPath());
            getLogger().info("Overgenomen: plugins/" + oldPlugin + "/" + oldFile + " -> plugins/" + getName() + "/" + newFile);
        } catch (IOException e) {
            getLogger().severe("Kon plugins/" + oldPlugin + "/" + oldFile + " niet overnemen: " + e);
        }
    }

    /** Commando van een module die uit staat: duidelijke melding in plaats van de usage-regel. */
    private void disabledCommand(String name, String module) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                sender.sendRichMessage("<red>De " + module + "-module van FenixCore staat uit (zie de console bij het opstarten).");
                return true;
            });
            command.setTabCompleter((sender, cmd, label, args) -> List.of());
        }
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
