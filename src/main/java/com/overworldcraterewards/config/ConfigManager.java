package com.overworldcraterewards.config;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.items.CustomItemType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all plugin configuration.
 */
public class ConfigManager {

    private final OverworldCrateRewardsPlugin plugin;
    private FileConfiguration config;

    // Cached values
    private String messagePrefix;

    // Harvest Hoe
    private int harvestHoeRadius;
    private long harvestHoeCooldownMs;
    private List<Long> harvestHoeMilestoneThresholds;
    private List<Double> harvestHoeMilestoneBonuses;
    private int harvestHoeUpgradedRadius;

    // Farmer's Pouch
    private Map<Material, Double> cropPrices;
    private double farmersPouchUpgradeThreshold;
    private double farmersPouchUpgradedSellMultiplier;
    private double farmersPouchLuckyChance;

    // Soul Siphon
    private double soulSiphonBonus;
    private long soulSiphonMultiKillWindowTicks;
    private long soulSiphonUpgradeThreshold;
    private double soulSiphonUpgradedBonus;
    private double soulSiphonUpgradedDoubleSoulsChance;
    private double soulSiphonUpgradedFeedChance;

    // Miner's Fervor
    private double minersFervorBonusPerBlock;
    private double minersFervorShopPriceMultiplier;
    private double minersFervorDecayRate;
    private long minersFervorDecayIntervalTicks;
    private double minersFervorSpeedMultiplierPerPoint;
    private int minersFervorDecayFloor;
    private long minersFervorUpgradeThreshold;

    // Jack'o'Hammer
    private double jackoHammerBonusSeedChance;
    private int jackoHammerMinSeeds;
    private int jackoHammerMaxSeeds;
    private long jackoHammerUpgradeThreshold;
    private double jackoHammerUpgradedSeedChance;
    private int jackoHammerUpgradedMinSeeds;
    private int jackoHammerUpgradedMaxSeeds;
    private double jackoHammerCarvedPumpkinChance;

    // Angler's Charm
    private Map<Material, Double> fishPrices;
    private double anglersCharmDoubleCatchChance;
    private long anglersCharmUpgradeThreshold;
    private double anglersCharmUpgradedSellMultiplier;
    private double anglersCharmUpgradedDoubleCatchChance;
    private double anglersCharmPrismarineShardChance;

    // Lumberjack's Mark
    private double lumberjacksMarkBonusChance;
    private double lumberjacksMarkFlatBonus;
    private long lumberjacksMarkUpgradeThreshold;
    private double lumberjacksMarkUpgradedBonusChance;
    private double lumberjacksMarkUpgradedFlatBonus;
    private double lumberjacksMarkAppleChance;

    // Melon-nator
    private double melonNatorBaseGlisteringChance;
    private double melonNatorGrowthIncrement;
    private double melonNatorGrowthMultiplier;
    private double melonNatorGlisteringCap;
    private double melonNatorXpBottleChance;
    private double melonNatorJuicyChance;
    private int melonNatorMinGlistering;
    private int melonNatorMaxGlistering;
    private double melonNatorUpgradedGrowthSpeedMultiplier;

    // Farmer's Hand
    private int farmersHandMagnetRadius;
    private int farmersHandUpgradedRadius;
    private int farmersHandScarecrowMinutes;
    private int farmersHandFarmlandThreshold;
    private long farmersHandMagnetTaskInterval;
    private long farmersHandAllayFollowInterval;
    private long farmersHandScarecrowCheckInterval;
    private int farmersHandAllayFollowDistance;
    private double farmersHandItemPullThreshold;
    private double farmersHandPullSpeedBase;
    private double farmersHandPullSpeedPerBlock;
    private double farmersHandPullSpeedMax;
    private int farmersHandWheatSearchRadiusXZ;
    private int farmersHandWheatSearchRadiusY;
    private int farmersHandLoreUpdateInterval;

    // Vacuum Void Hopper
    private int vacuumHopperRadius;
    private int vacuumHopperTransferRate;
    private int vacuumHopperMaxLinks;
    private int vacuumHopperTickInterval;

    public ConfigManager(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        loadGeneralSettings();
        loadHarvestHoeSettings();
        loadFarmersPouchSettings();
        loadSoulSiphonSettings();
        loadMinersFervorSettings();
        loadJackoHammerSettings();
        loadAnglersCharmSettings();
        loadLumberjacksMarkSettings();
        loadMelonNatorSettings();
        loadFarmersHandSettings();
        loadVacuumHopperSettings();
    }

    private void loadGeneralSettings() {
        messagePrefix = config.getString("messages.prefix", "&8[&6OCR&8] &r");
    }

    private void loadHarvestHoeSettings() {
        harvestHoeRadius = config.getInt("harvest-hoe.radius", 1);
        harvestHoeCooldownMs = config.getLong("harvest-hoe.cooldown-ms", 250);

        List<Long> defaultThresholds = Arrays.asList(10_000L, 100_000L, 250_000L, 500_000L, 1_000_000L, 2_500_000L, 5_000_000L, 10_000_000L);
        List<Long> thresholds = config.getLongList("harvest-hoe.milestone-thresholds");
        harvestHoeMilestoneThresholds = thresholds.isEmpty() ? defaultThresholds : thresholds;

        List<Double> defaultBonuses = Arrays.asList(1.0, 1.0, 1.0, 2.0, 5.0, 5.0, 5.0, 15.0);
        List<Double> bonuses = config.getDoubleList("harvest-hoe.milestone-bonuses");
        harvestHoeMilestoneBonuses = bonuses.isEmpty() ? defaultBonuses : bonuses;

        harvestHoeUpgradedRadius = config.getInt("harvest-hoe.upgraded.radius", 2);
    }

    private void loadFarmersPouchSettings() {
        cropPrices = new HashMap<>();
        ConfigurationSection pricesSection = config.getConfigurationSection("farmers-pouch.prices");
        if (pricesSection != null) {
            for (String key : pricesSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    double price = pricesSection.getDouble(key);
                    cropPrices.put(material, price);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in crop prices: " + key);
                }
            }
        }

        // Default prices if none configured
        if (cropPrices.isEmpty()) {
            cropPrices.put(Material.WHEAT, 1.0);
            cropPrices.put(Material.CARROT, 0.75);
            cropPrices.put(Material.POTATO, 0.75);
            cropPrices.put(Material.BEETROOT, 1.25);
            cropPrices.put(Material.NETHER_WART, 2.0);
            cropPrices.put(Material.COCOA_BEANS, 1.5);
            cropPrices.put(Material.SUGAR_CANE, 0.5);
            cropPrices.put(Material.MELON_SLICE, 0.25);
            cropPrices.put(Material.PUMPKIN, 3.0);
            cropPrices.put(Material.POISONOUS_POTATO, 0.1);
            cropPrices.put(Material.WHEAT_SEEDS, 0.1);
            cropPrices.put(Material.BEETROOT_SEEDS, 0.1);
            cropPrices.put(Material.PUMPKIN_SEEDS, 0.2);
            cropPrices.put(Material.MELON_SEEDS, 0.2);
        }

        farmersPouchUpgradeThreshold = config.getDouble("farmers-pouch.upgraded.threshold", 1_000_000_000.0);
        farmersPouchUpgradedSellMultiplier = config.getDouble("farmers-pouch.upgraded.sell-multiplier", 1.15);
        farmersPouchLuckyChance = config.getDouble("farmers-pouch.upgraded.lucky-chance", 0.03);
    }

    private void loadSoulSiphonSettings() {
        soulSiphonBonus = config.getDouble("soul-siphon.bonus-per-kill", 1.0);
        soulSiphonMultiKillWindowTicks = config.getLong("soul-siphon.multi-kill-window-ticks", 10);
        soulSiphonUpgradeThreshold = config.getLong("soul-siphon.upgraded.threshold", 6_666_666);
        soulSiphonUpgradedBonus = config.getDouble("soul-siphon.upgraded.bonus-per-kill", 4.0);
        soulSiphonUpgradedDoubleSoulsChance = config.getDouble("soul-siphon.upgraded.double-souls-chance", 0.10);
        soulSiphonUpgradedFeedChance = config.getDouble("soul-siphon.upgraded.feed-chance", 0.10);
    }

    private void loadMinersFervorSettings() {
        minersFervorBonusPerBlock = config.getDouble("miners-fervor.bonus-per-block", 0.10);
        minersFervorShopPriceMultiplier = config.getDouble("miners-fervor.shop-price-multiplier", 0.05);
        minersFervorDecayRate = config.getDouble("miners-fervor.decay-rate", 0.10);
        minersFervorDecayIntervalTicks = config.getLong("miners-fervor.decay-interval-ticks", 200);
        minersFervorSpeedMultiplierPerPoint = config.getDouble("miners-fervor.speed-multiplier-per-point", 0.001);
        minersFervorDecayFloor = config.getInt("miners-fervor.decay-floor", 10);
        minersFervorUpgradeThreshold = config.getLong("miners-fervor.upgraded.threshold", 1_000_000);
    }

    private void loadJackoHammerSettings() {
        jackoHammerBonusSeedChance = config.getDouble("jacko-hammer.bonus-seed-chance", 0.25);
        jackoHammerMinSeeds = config.getInt("jacko-hammer.min-seeds", 1);
        jackoHammerMaxSeeds = config.getInt("jacko-hammer.max-seeds", 4);
        jackoHammerUpgradeThreshold = config.getLong("jacko-hammer.upgraded.threshold", 500_000);
        jackoHammerUpgradedSeedChance = config.getDouble("jacko-hammer.upgraded.bonus-seed-chance", 0.50);
        jackoHammerUpgradedMinSeeds = config.getInt("jacko-hammer.upgraded.min-seeds", 2);
        jackoHammerUpgradedMaxSeeds = config.getInt("jacko-hammer.upgraded.max-seeds", 6);
        jackoHammerCarvedPumpkinChance = config.getDouble("jacko-hammer.upgraded.carved-pumpkin-chance", 0.05);
    }

    private void loadAnglersCharmSettings() {
        fishPrices = new HashMap<>();
        ConfigurationSection pricesSection = config.getConfigurationSection("anglers-charm.prices");
        if (pricesSection != null) {
            for (String key : pricesSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    double price = pricesSection.getDouble(key);
                    fishPrices.put(material, price);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in fish prices: " + key);
                }
            }
        }

        // Default prices if none configured
        if (fishPrices.isEmpty()) {
            fishPrices.put(Material.COD, 2.0);
            fishPrices.put(Material.SALMON, 3.0);
            fishPrices.put(Material.TROPICAL_FISH, 5.0);
            fishPrices.put(Material.PUFFERFISH, 4.0);
        }

        anglersCharmDoubleCatchChance = config.getDouble("anglers-charm.double-catch-chance", 0.10);
        anglersCharmUpgradeThreshold = config.getLong("anglers-charm.upgraded.threshold", 100_000);
        anglersCharmUpgradedSellMultiplier = config.getDouble("anglers-charm.upgraded.sell-multiplier", 1.10);
        anglersCharmUpgradedDoubleCatchChance = config.getDouble("anglers-charm.upgraded.double-catch-chance", 0.20);
        anglersCharmPrismarineShardChance = config.getDouble("anglers-charm.upgraded.prismarine-shard-chance", 0.03);
    }

    private void loadLumberjacksMarkSettings() {
        lumberjacksMarkBonusChance = config.getDouble("lumberjacks-mark.bonus-chance", 0.15);
        lumberjacksMarkFlatBonus = config.getDouble("lumberjacks-mark.flat-bonus", 0.25);
        lumberjacksMarkUpgradeThreshold = config.getLong("lumberjacks-mark.upgraded.threshold", 250_000);
        lumberjacksMarkUpgradedBonusChance = config.getDouble("lumberjacks-mark.upgraded.bonus-chance", 0.25);
        lumberjacksMarkUpgradedFlatBonus = config.getDouble("lumberjacks-mark.upgraded.flat-bonus", 0.50);
        lumberjacksMarkAppleChance = config.getDouble("lumberjacks-mark.upgraded.apple-chance", 0.05);
    }

    private void loadMelonNatorSettings() {
        melonNatorBaseGlisteringChance = config.getDouble("melon-nator.base-glistering-chance", 0.0001);
        melonNatorGrowthIncrement = config.getDouble("melon-nator.growth-increment", 0.000001);
        melonNatorGrowthMultiplier = config.getDouble("melon-nator.growth-multiplier", 1.0001);
        melonNatorGlisteringCap = config.getDouble("melon-nator.glistering-cap", 0.10);
        melonNatorXpBottleChance = config.getDouble("melon-nator.xp-bottle-chance", 0.05);
        melonNatorJuicyChance = config.getDouble("melon-nator.juicy-chance", 0.02);
        melonNatorMinGlistering = config.getInt("melon-nator.min-glistering", 1);
        melonNatorMaxGlistering = config.getInt("melon-nator.max-glistering", 3);
        melonNatorUpgradedGrowthSpeedMultiplier = config.getDouble("melon-nator.upgraded.growth-speed-multiplier", 2.0);
    }

    private void loadFarmersHandSettings() {
        farmersHandMagnetRadius = config.getInt("farmers-hand.magnet-radius", 8);
        farmersHandUpgradedRadius = config.getInt("farmers-hand.upgraded-radius", 16);
        farmersHandScarecrowMinutes = config.getInt("farmers-hand.scarecrow-minutes", 5);
        farmersHandFarmlandThreshold = config.getInt("farmers-hand.farmland-threshold", 8);
        farmersHandMagnetTaskInterval = config.getLong("farmers-hand.magnet-task-interval", 4);
        farmersHandAllayFollowInterval = config.getLong("farmers-hand.allay-follow-interval", 10);
        farmersHandScarecrowCheckInterval = config.getLong("farmers-hand.scarecrow-check-interval", 20);
        farmersHandAllayFollowDistance = config.getInt("farmers-hand.allay-follow-distance", 16);
        farmersHandItemPullThreshold = config.getDouble("farmers-hand.item-pull-threshold", 1.5);
        farmersHandPullSpeedBase = config.getDouble("farmers-hand.pull-speed-base", 0.3);
        farmersHandPullSpeedPerBlock = config.getDouble("farmers-hand.pull-speed-per-block", 0.05);
        farmersHandPullSpeedMax = config.getDouble("farmers-hand.pull-speed-max", 0.8);
        farmersHandWheatSearchRadiusXZ = config.getInt("farmers-hand.wheat-search-radius-xz", 2);
        farmersHandWheatSearchRadiusY = config.getInt("farmers-hand.wheat-search-radius-y", 1);
        farmersHandLoreUpdateInterval = config.getInt("farmers-hand.lore-update-interval", 50);
    }

    private void loadVacuumHopperSettings() {
        vacuumHopperRadius = config.getInt("vacuum-void-hopper.vacuum-radius", 8);
        vacuumHopperTransferRate = config.getInt("vacuum-void-hopper.transfer-rate", 64);
        vacuumHopperMaxLinks = config.getInt("vacuum-void-hopper.max-links", 8);
        vacuumHopperTickInterval = config.getInt("vacuum-void-hopper.tick-interval", 8);
    }

    // Item display configuration
    public String getItemDisplayName(CustomItemType type) {
        return config.getString("items." + type.getId().replace("_", "-") + ".name", type.getDefaultDisplayName());
    }

    public List<String> getItemLore(CustomItemType type) {
        return config.getStringList("items." + type.getId().replace("_", "-") + ".lore");
    }

    // === General Getters ===
    public String getMessagePrefix() {
        return messagePrefix;
    }

    // === Harvest Hoe Getters ===
    public int getHarvestHoeRadius() {
        return harvestHoeRadius;
    }

    public long getHarvestHoeCooldownMs() {
        return harvestHoeCooldownMs;
    }

    public List<Long> getHarvestHoeMilestoneThresholds() {
        return harvestHoeMilestoneThresholds;
    }

    public List<Double> getHarvestHoeMilestoneBonuses() {
        return harvestHoeMilestoneBonuses;
    }

    public int getHarvestHoeUpgradedRadius() {
        return harvestHoeUpgradedRadius;
    }

    // === Farmer's Pouch Getters ===
    public Map<Material, Double> getCropPrices() {
        return cropPrices;
    }

    public Double getCropPrice(Material material) {
        return cropPrices.get(material);
    }

    public double getFarmersPouchUpgradeThreshold() {
        return farmersPouchUpgradeThreshold;
    }

    public double getFarmersPouchUpgradedSellMultiplier() {
        return farmersPouchUpgradedSellMultiplier;
    }

    public double getFarmersPouchLuckyChance() {
        return farmersPouchLuckyChance;
    }

    // === Soul Siphon Getters ===
    public double getSoulSiphonBonus() {
        return soulSiphonBonus;
    }

    public long getSoulSiphonMultiKillWindowTicks() {
        return soulSiphonMultiKillWindowTicks;
    }

    public long getSoulSiphonUpgradeThreshold() {
        return soulSiphonUpgradeThreshold;
    }

    public double getSoulSiphonUpgradedBonus() {
        return soulSiphonUpgradedBonus;
    }

    public double getSoulSiphonUpgradedDoubleSoulsChance() {
        return soulSiphonUpgradedDoubleSoulsChance;
    }

    public double getSoulSiphonUpgradedFeedChance() {
        return soulSiphonUpgradedFeedChance;
    }

    // === Miner's Fervor Getters ===
    public double getMinersFervorBonusPerBlock() {
        return minersFervorBonusPerBlock;
    }

    public double getMinersFervorShopPriceMultiplier() {
        return minersFervorShopPriceMultiplier;
    }

    public double getMinersFervorDecayRate() {
        return minersFervorDecayRate;
    }

    public long getMinersFervorDecayIntervalTicks() {
        return minersFervorDecayIntervalTicks;
    }

    public double getMinersFervorSpeedMultiplierPerPoint() {
        return minersFervorSpeedMultiplierPerPoint;
    }

    public int getMinersFervorDecayFloor() {
        return minersFervorDecayFloor;
    }

    public long getMinersFervorUpgradeThreshold() {
        return minersFervorUpgradeThreshold;
    }

    // === Jack'o'Hammer Getters ===
    public double getJackoHammerBonusSeedChance() {
        return jackoHammerBonusSeedChance;
    }

    public int getJackoHammerMinSeeds() {
        return jackoHammerMinSeeds;
    }

    public int getJackoHammerMaxSeeds() {
        return jackoHammerMaxSeeds;
    }

    public long getJackoHammerUpgradeThreshold() {
        return jackoHammerUpgradeThreshold;
    }

    public double getJackoHammerUpgradedSeedChance() {
        return jackoHammerUpgradedSeedChance;
    }

    public int getJackoHammerUpgradedMinSeeds() {
        return jackoHammerUpgradedMinSeeds;
    }

    public int getJackoHammerUpgradedMaxSeeds() {
        return jackoHammerUpgradedMaxSeeds;
    }

    public double getJackoHammerCarvedPumpkinChance() {
        return jackoHammerCarvedPumpkinChance;
    }

    // === Angler's Charm Getters ===
    public Map<Material, Double> getFishPrices() {
        return fishPrices;
    }

    public Double getFishPrice(Material material) {
        return fishPrices.get(material);
    }

    public double getAnglersCharmDoubleCatchChance() {
        return anglersCharmDoubleCatchChance;
    }

    public long getAnglersCharmUpgradeThreshold() {
        return anglersCharmUpgradeThreshold;
    }

    public double getAnglersCharmUpgradedSellMultiplier() {
        return anglersCharmUpgradedSellMultiplier;
    }

    public double getAnglersCharmUpgradedDoubleCatchChance() {
        return anglersCharmUpgradedDoubleCatchChance;
    }

    public double getAnglersCharmPrismarineShardChance() {
        return anglersCharmPrismarineShardChance;
    }

    // === Lumberjack's Mark Getters ===
    public double getLumberjacksMarkBonusChance() {
        return lumberjacksMarkBonusChance;
    }

    public double getLumberjacksMarkFlatBonus() {
        return lumberjacksMarkFlatBonus;
    }

    public long getLumberjacksMarkUpgradeThreshold() {
        return lumberjacksMarkUpgradeThreshold;
    }

    public double getLumberjacksMarkUpgradedBonusChance() {
        return lumberjacksMarkUpgradedBonusChance;
    }

    public double getLumberjacksMarkUpgradedFlatBonus() {
        return lumberjacksMarkUpgradedFlatBonus;
    }

    public double getLumberjacksMarkAppleChance() {
        return lumberjacksMarkAppleChance;
    }

    // === Melon-nator Getters ===
    public double getMelonNatorBaseGlisteringChance() {
        return melonNatorBaseGlisteringChance;
    }

    public double getMelonNatorGrowthIncrement() {
        return melonNatorGrowthIncrement;
    }

    public double getMelonNatorGrowthMultiplier() {
        return melonNatorGrowthMultiplier;
    }

    public double getMelonNatorGlisteringCap() {
        return melonNatorGlisteringCap;
    }

    public double getMelonNatorXpBottleChance() {
        return melonNatorXpBottleChance;
    }

    public double getMelonNatorJuicyChance() {
        return melonNatorJuicyChance;
    }

    public int getMelonNatorMinGlistering() {
        return melonNatorMinGlistering;
    }

    public int getMelonNatorMaxGlistering() {
        return melonNatorMaxGlistering;
    }

    public double getMelonNatorUpgradedGrowthSpeedMultiplier() {
        return melonNatorUpgradedGrowthSpeedMultiplier;
    }

    // === Farmer's Hand Getters ===
    public int getFarmersHandMagnetRadius() {
        return farmersHandMagnetRadius;
    }

    public int getFarmersHandUpgradedRadius() {
        return farmersHandUpgradedRadius;
    }

    public int getFarmersHandScarecrowMinutes() {
        return farmersHandScarecrowMinutes;
    }

    public int getFarmersHandFarmlandThreshold() {
        return farmersHandFarmlandThreshold;
    }

    public long getFarmersHandMagnetTaskInterval() {
        return farmersHandMagnetTaskInterval;
    }

    public long getFarmersHandAllayFollowInterval() {
        return farmersHandAllayFollowInterval;
    }

    public long getFarmersHandScarecrowCheckInterval() {
        return farmersHandScarecrowCheckInterval;
    }

    public int getFarmersHandAllayFollowDistance() {
        return farmersHandAllayFollowDistance;
    }

    public double getFarmersHandItemPullThreshold() {
        return farmersHandItemPullThreshold;
    }

    public double getFarmersHandPullSpeedBase() {
        return farmersHandPullSpeedBase;
    }

    public double getFarmersHandPullSpeedPerBlock() {
        return farmersHandPullSpeedPerBlock;
    }

    public double getFarmersHandPullSpeedMax() {
        return farmersHandPullSpeedMax;
    }

    public int getFarmersHandWheatSearchRadiusXZ() {
        return farmersHandWheatSearchRadiusXZ;
    }

    public int getFarmersHandWheatSearchRadiusY() {
        return farmersHandWheatSearchRadiusY;
    }

    public int getFarmersHandLoreUpdateInterval() {
        return farmersHandLoreUpdateInterval;
    }

    // === Vacuum Void Hopper Getters ===
    public int getVacuumHopperRadius() {
        return vacuumHopperRadius;
    }

    public int getVacuumHopperTransferRate() {
        return vacuumHopperTransferRate;
    }

    public int getVacuumHopperMaxLinks() {
        return vacuumHopperMaxLinks;
    }

    public int getVacuumHopperTickInterval() {
        return vacuumHopperTickInterval;
    }
}
