package com.overworldcraterewards;

import com.overworldcraterewards.commands.OCRCommand;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.economy.EconomyManager;
import com.overworldcraterewards.features.farmerspouch.FarmersPouchListener;
import com.overworldcraterewards.features.harvesthoe.HarvestHoeListener;
import com.overworldcraterewards.features.minersfervor.MinersFervorListener;
import com.overworldcraterewards.features.soulsiphon.SoulSiphonListener;
import com.overworldcraterewards.features.jackohammer.JackoHammerListener;
import com.overworldcraterewards.features.anglerscharm.AnglersCharmListener;
import com.overworldcraterewards.features.lumberjacksmark.LumberjacksMarkListener;
import com.overworldcraterewards.features.melonnator.MelonNatorListener;
import com.overworldcraterewards.features.farmershand.FarmersHandListener;
import com.overworldcraterewards.features.vacuumhopper.VacuumHopperListener;
import com.overworldcraterewards.features.vacuumhopper.VacuumHopperManager;
import com.overworldcraterewards.hooks.EconomyShopGUIHook;
import com.overworldcraterewards.hooks.RoseStackerHook;
import com.overworldcraterewards.items.CustomItemManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for OverworldCrateRewards.
 * Provides QoL items for Skyblock crate rewards.
 */
public final class OverworldCrateRewardsPlugin extends JavaPlugin {

    private static OverworldCrateRewardsPlugin instance;

    private ConfigManager configManager;
    private EconomyManager economyManager;
    private CustomItemManager itemManager;
    private MinersFervorListener minersFervorListener;
    private FarmersHandListener farmersHandListener;
    private VacuumHopperManager vacuumHopperManager;
    private VacuumHopperListener vacuumHopperListener;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        configManager = new ConfigManager(this);

        // Initialize Vault economy
        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Vault economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize item manager
        itemManager = new CustomItemManager(this, configManager);

        // Initialize optional hooks
        EconomyShopGUIHook.init();
        RoseStackerHook.init();

        // Register all listeners
        registerListeners();

        // Register commands
        registerCommands();

        getLogger().info("OverworldCrateRewards v" + getDescription().getVersion() + " enabled!");
        getLogger().info("Economy provider: " + economyManager.getEconomyName());
    }

    @Override
    public void onDisable() {
        // Clean up Farmer's Hand Allays
        if (farmersHandListener != null) {
            farmersHandListener.removeAllAllays();
        }
        // Save and shutdown Vacuum Void Hopper data
        if (vacuumHopperManager != null) {
            vacuumHopperManager.shutdown();
        }
        getLogger().info("OverworldCrateRewards disabled.");
        instance = null;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Create Farmer's Pouch listener first (needed by Harvest Hoe for integration)
        FarmersPouchListener pouchListener = new FarmersPouchListener(this);

        // Feature listeners
        pm.registerEvents(new HarvestHoeListener(this, pouchListener), this);
        pm.registerEvents(pouchListener, this);
        pm.registerEvents(new SoulSiphonListener(this), this);
        minersFervorListener = new MinersFervorListener(this);
        pm.registerEvents(minersFervorListener, this);

        // New items
        pm.registerEvents(new JackoHammerListener(this), this);
        pm.registerEvents(new AnglersCharmListener(this), this);
        pm.registerEvents(new LumberjacksMarkListener(this), this);
        pm.registerEvents(new MelonNatorListener(this), this);
        farmersHandListener = new FarmersHandListener(this);
        pm.registerEvents(farmersHandListener, this);

        // Vacuum Void Hopper (manager + listener)
        vacuumHopperManager = new VacuumHopperManager(this);
        vacuumHopperListener = new VacuumHopperListener(this, vacuumHopperManager);
        pm.registerEvents(vacuumHopperListener, this);

        getLogger().info("Registered all feature listeners.");
    }

    private void registerCommands() {
        OCRCommand ocrCommand = new OCRCommand(this);
        getCommand("ocr").setExecutor(ocrCommand);
        getCommand("ocr").setTabCompleter(ocrCommand);

        getLogger().info("Registered commands.");
    }

    /**
     * Reload the plugin configuration.
     */
    public void reload() {
        configManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    // Getters

    public static OverworldCrateRewardsPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public CustomItemManager getItemManager() {
        return itemManager;
    }

    public MinersFervorListener getMinersFervorListener() {
        return minersFervorListener;
    }
}
