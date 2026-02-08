package com.overworldcraterewards.economy;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Manages integration with Vault economy.
 */
public class EconomyManager {

    private final OverworldCrateRewardsPlugin plugin;
    private Economy economy;

    public EconomyManager(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Set up the Vault economy hook.
     * @return true if economy was found and set up successfully
     */
    public boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault plugin not found!");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            plugin.getLogger().severe("No economy provider found! Make sure you have an economy plugin installed.");
            return false;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Hooked into economy: " + economy.getName());
        return true;
    }

    /**
     * Deposit money to a player's account.
     * @param player The player to pay
     * @param amount The amount to deposit
     * @return true if successful
     */
    public boolean deposit(Player player, double amount) {
        if (economy == null) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Withdraw money from a player's account.
     * @param player The player to charge
     * @param amount The amount to withdraw
     * @return true if successful
     */
    public boolean withdraw(Player player, double amount) {
        if (economy == null) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Get a player's balance.
     * @param player The player
     * @return Their balance
     */
    public double getBalance(Player player) {
        if (economy == null) {
            return 0;
        }
        return economy.getBalance(player);
    }

    /**
     * Check if economy is available.
     * @return true if economy is set up
     */
    public boolean isEnabled() {
        return economy != null;
    }

    /**
     * Get the economy provider name.
     * @return The name of the economy plugin
     */
    public String getEconomyName() {
        return economy != null ? economy.getName() : "None";
    }
}
