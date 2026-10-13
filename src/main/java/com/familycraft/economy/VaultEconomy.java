package com.familycraft.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Thin wrapper around Vault. Only loaded when Vault is installed. */
public class VaultEconomy {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /** @return the economy or null when Vault / an economy plugin is missing */
    public static VaultEconomy hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new VaultEconomy(rsp.getProvider());
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }

    public String format(double amount) {
        return economy.format(amount);
    }
}
