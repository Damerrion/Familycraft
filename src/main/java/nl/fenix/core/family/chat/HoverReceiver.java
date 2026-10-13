package nl.fenix.core.family.chat;

import com.gufli.kingdomcraft.api.domain.User;
import com.gufli.kingdomcraft.api.entity.PlatformLocation;
import com.gufli.kingdomcraft.api.entity.PlatformPlayer;
import com.gufli.kingdomcraft.api.gui.Inventory;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** A chat receiver that delegates everything to the real player, but sends chat lines with the family hover. */
class HoverReceiver implements PlatformPlayer {

    private final PlatformPlayer delegate;
    private final String prefix;
    private final HoverEvent hover;
    private final ClickEvent click;

    HoverReceiver(PlatformPlayer delegate, String prefix, HoverEvent hover, ClickEvent click) {
        this.delegate = delegate;
        this.prefix = prefix;
        this.hover = hover;
        this.click = click;
    }

    @Override
    public void sendMessage(String message) {
        Player player = Bukkit.getPlayer(delegate.getUniqueId());
        BaseComponent[] components = player == null || message == null ? null
                : FamilyHover.build(message, prefix, hover, click);
        if (components == null) {
            delegate.sendMessage(message);
            return;
        }
        player.spigot().sendMessage(components);
    }

    // ---- plain delegation ----

    @Override
    public boolean hasPermission(String permission) {
        return delegate.hasPermission(permission);
    }

    @Override
    public UUID getUniqueId() {
        return delegate.getUniqueId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void teleport(PlatformLocation location) {
        delegate.teleport(location);
    }

    @Override
    public PlatformLocation getLocation() {
        return delegate.getLocation();
    }

    @Override
    public User getUser() {
        return delegate.getUser();
    }

    @Override
    public Inventory<?, ?> getInventory() {
        return delegate.getInventory();
    }

    @Override
    public void openInventory(Inventory<?, ?> inventory) {
        delegate.openInventory(inventory);
    }

    @Override
    public void closeInventory() {
        delegate.closeInventory();
    }

    @Override
    public boolean isAdmin() {
        return delegate.isAdmin();
    }

    @Override
    public void setAdmin(boolean admin) {
        delegate.setAdmin(admin);
    }

    @Override
    public boolean isSocialSpyEnabled() {
        return delegate.isSocialSpyEnabled();
    }

    @Override
    public void setSocialSpyEnabled(boolean enabled) {
        delegate.setSocialSpyEnabled(enabled);
    }

    @Override
    public void set(String key, Object value) {
        delegate.set(key, value);
    }

    @Override
    public boolean has(String key) {
        return delegate.has(key);
    }

    @Override
    public void remove(String key) {
        delegate.remove(key);
    }

    @Override
    public Object get(String key) {
        return delegate.get(key);
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        return delegate.get(key, clazz);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlatformPlayer && ((PlatformPlayer) o).getUniqueId().equals(getUniqueId());
    }

    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }
}
