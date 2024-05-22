/* TODO: Fix Pumpkin and melon growth, optimise where possible */

package me.mooseman06.oldmacdonald;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;



public class OldMacDonald extends JavaPlugin implements CommandExecutor, Listener {

    private final List<FarmLocation> farmLocations = new ArrayList<>(); // Store the locations of the farms
    private int defaultRadius = 24; // Default radius
    private int maxRadius = 50; // Maximum radius allowed
    private int maxMobs = 50; // Maximum mobs allowed
    private FileConfiguration config;
    private File configFile;
    private final Set<Location> mobSpawnerLocations = new HashSet<>();
    private static final int MIN_SPAWN_INTERVAL_TICKS = 200; // Example value, adjust as needed
    private BukkitTask checkSpawnersTask;
    private long lastSpawnTime = 0; // Initialize to 0
    private final long minDelay = 10000; // Minimum delay
    private final long maxDelay = 39950; // Maximum delay
    long delay = minDelay + (long) (Math.random() * (maxDelay - minDelay)); // Randomize delay for each spawn

    @Override
    public void onEnable() {
        // Create plugin data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            boolean result = getDataFolder().mkdirs();
            if (!result) {
                getLogger().severe("Could not create plugin data folder. Please check the file permissions.");
                // Optionally disable the plugin if its data folder is critical for its operation
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Register command executor
        Objects.requireNonNull(getCommand("OMD")).setExecutor(this);
        Objects.requireNonNull(getCommand("OMD")).setTabCompleter(new OldMacDonaldTabCompleter());

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("OldMacDonald loading farms...\n" +
                "  ______   _____   ______   _____    ____  \n"+
                " |  ____| |_   _| |  ____| |_   _|  / __ \\ \n"+
                " | |__      | |   | |__      | |   | |  | |\n"+
                " |  __|     | |   |  __|     | |   | |  | |\n"+
                " | |____   _| |_  | |____   _| |_  | |__| |\n"+
                " |______| |_____| |______| |_____|  \\____/ ");

        // Wait for all worlds to be loaded before loading configs and old farms
        getServer().getScheduler().runTask(this, () -> {
            // Load configuration
            configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                saveResource("config.yml", false);
            }
            config = YamlConfiguration.loadConfiguration(configFile);

            loadAll();
        });
    }

    @Override
    public void onDisable() {
        // Save farm locations
        saveFarmLocations();
        if (checkSpawnersTask != null) {
            checkSpawnersTask.cancel();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "addfarm":
                    if (args.length >= 2 && args[1].equalsIgnoreCase("current") && sender instanceof Player) {
                        // Set farm location to player's current location
                        Player player = (Player) sender;
                        if (args.length >= 4) { // Check if the radius is provided
                            String farmName = args[2];
                            try {
                                int radius = Integer.parseInt(args[3]);
                                addFarmLocation(player.getLocation(), player, farmName, radius);
                            } catch (NumberFormatException e) {
                                player.sendMessage(ChatColor.RED + "Invalid radius. Please provide a valid integer.");
                            }
                        } else if (args.length == 3) {
                            String farmName = args[2];
                            addFarmLocation(player.getLocation(), player, farmName, defaultRadius); // Default radius
                        } else {
                            player.sendMessage(ChatColor.RED + "Please provide a farm name along with the command.");
                        }
                    } else if (args.length >= 5) {
                        // Set farm location to specified coordinates
                        try {
                            double x = Double.parseDouble(args[1]);
                            double y = Double.parseDouble(args[2]);
                            double z = Double.parseDouble(args[3]);
                            Location location = new Location(getServer().getWorlds().get(0), x, y, z); // Assuming only one world
                            String farmName = args[4];
                            if (args.length >= 6) { // Check if the radius is provided
                                try {
                                    int radius = Integer.parseInt(args[5]);
                                    addFarmLocation(location, sender, farmName, radius);
                                } catch (NumberFormatException e) {
                                    sender.sendMessage(ChatColor.RED + "Invalid radius. Please provide a valid integer.");
                                }
                            } else {
                                addFarmLocation(location, sender, farmName, defaultRadius); // Default radius
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid coordinates. Please provide numeric values.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Command not recognised. Please use " + ChatColor.YELLOW + " '/omd help' for more information.");
                    }
                    return true;
                case "removefarm":
                    if (args.length == 2) {
                        String identifier = args[1];
                        removeFarmLocation(identifier, sender);
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Command not recognised. Please use " + ChatColor.YELLOW + " '/omd help' for more information.");
                    }
                    return true;
                case "listfarms":
                    listFarmLocations(sender);
                    return true;
                case "info":
                    sender.sendMessage(ChatColor.YELLOW + "OldMacDonald version " + ChatColor.GREEN + getDescription().getVersion() + ChatColor.YELLOW + " is active on this server!");
                    return true;
                case "feedback":
                    TextComponent.Builder feedbackMessageBuilder = Component.text()
                            .append(Component.text("If you encounter any bugs or have feedback, please report them ").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                            .append(Component.text("here").color(net.kyori.adventure.text.format.NamedTextColor.BLUE).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
                            .clickEvent(ClickEvent.openUrl("https://forms.gle/gCDUhkVA3mT5Qz8DA"))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to open feedback form")));

                    sender.sendMessage(feedbackMessageBuilder.build());
                    return true;
                case "setmaxradius":
                    if (sender.hasPermission("oldmacdonald.setmaxradius")) { // Check for permission
                        if (args.length == 2) {
                            try {
                                int radius = Integer.parseInt(args[1]);
                                setMaxRadius(sender, radius);
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ChatColor.RED + "Invalid radius. Please provide a valid integer.");
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Command not recognised. Please use " + ChatColor.YELLOW + " '/omd help' for more information.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                    return true;
                case "setdefaultradius":
                    if (sender.hasPermission("oldmacdonald.setdefaultradius")) { // Check for permission
                        if (args.length == 2) {
                            try {
                                int radius = Integer.parseInt(args[1]);
                                setDefaultRadius(sender, radius);
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ChatColor.RED + "Invalid radius. Please provide a valid integer.");
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Command not recognised. Please use " + ChatColor.YELLOW + " '/omd help' for more information.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                    return true;
                case "setmaxmobs":
                    if (sender.hasPermission("oldmacdonald.setmaxmobs")) { // Check for permission
                        if (args.length == 2) {
                            try {
                                int mobCount = Integer.parseInt(args[1]);
                                setMaxMobs(sender, mobCount);
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ChatColor.RED + "Invalid number. Please provide a valid integer.");
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "Command not recognised. Please use " + ChatColor.YELLOW + " '/omd help' for more information.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                case "help":
                    sendHelpMessage(sender);
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    private void loadAll(){
        //Load Customised Farms
        loadConfig();

        // Update config for old farms without radius
        updateConfigForOldFarms();

        // Load farm locations and spawner locations
        loadFarmLocations();
        loadSpawnerLocationsFromConfig();


        //Set villagers to not despawn
        protectVillagersInFarms();

        // Start simulating farm activity
        startFarmSimulation();
    }
    private void loadConfig() {
        // Load or create the configuration file
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load farm radius from config
        defaultRadius = config.getInt("settings.defaultRadius", 24); // Use the saved value or default to 24
        maxRadius = config.getInt("settings.maxRadius", 50); // Assuming 100 is your initial max value or any suitable default
        maxMobs = config.getInt("settings.maxMobs", 50); // Use saved value or default to 50
    }


    private void startFarmSimulation() {
        int initialDelay = 10;
        int interval = 1; //every tick after initial

        getServer().getScheduler().runTaskTimer(this, this::simulateFarmActivity, initialDelay, interval);
        // Schedule the task to check destroyed mob spawners
        checkSpawnersTask = getServer().getScheduler().runTaskTimer(this, this::checkDestroyedMobSpawners, MIN_SPAWN_INTERVAL_TICKS, MIN_SPAWN_INTERVAL_TICKS);
    }

    private void simulateFarmActivity() {
        for (FarmLocation farmLocation : farmLocations) {
            if (farmLocation == null || farmLocation.getLocation() == null) continue;

            // Check if any player is near this farm location
            if (!isPlayerNearFarm(farmLocation.getLocation(), farmLocation.getEnvironment())) {
                simulatePlantGrowth(farmLocation.getLocation(), farmLocation.getRadius()); // Simulate plant growth
                simulateMobSpawners(); // Simulate mob spawners
            }
        }
    }

    private boolean isPlayerNearFarm(Location farmLocation, World.Environment worldType) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment().equals(worldType) && player.getLocation().distanceSquared(farmLocation) <= 128 * 128) {
                return true;
            }
        }
        return false;
    }

    private void simulatePlantGrowth(Location farmLocation, int radius) {
        Random random = new Random();

        // Calculate the number of blocks to be selected based on the radius
        int numBlocksToSelect = calculateNumBlocksToSelect(radius);

        // Iterate over a random subset of blocks
        for (int i = 0; i < numBlocksToSelect; i++) {
            int randomX = farmLocation.getBlockX() - radius + random.nextInt(2 * radius + 1);
            int randomZ = farmLocation.getBlockZ() - radius + random.nextInt(2 * radius + 1);
            int randomY = farmLocation.getBlockY() - radius + random.nextInt(2 * radius + 1);

            Block block = farmLocation.getWorld().getBlockAt(randomX, randomY, randomZ);
            Material material = block.getType();
            if (block.getBlockData() instanceof Ageable){
                simulatePlantGrowth(block, material);
            }
        }
    }

    private int calculateNumBlocksToSelect(int radius) {
        // Calculate the diameter of the area (radius * 2) and take the ceiling division by 16
        int diameter = radius * 2;
        int volume = diameter * diameter * diameter;
        return (int) Math.ceil((volume / 4096.0) * 3);
    }

    private void simulatePlantGrowth(Block block, Material material) {
        Ageable ageable = (Ageable) block.getBlockData();
        switch (material) {
            case WHEAT:
            case POTATOES:
            case CARROTS:
            case BEETROOTS:
            case MELON_STEM:
            case PUMPKIN_STEM:
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    ageable.setAge(ageable.getAge() + 1);
                    block.setBlockData(ageable);
                } else if (material == Material.PUMPKIN_STEM) {
                    // Generate pumpkins randomly on adjacent vacant dirt-based blocks
                    generatePumpkins(block.getLocation());
                    // Reset age for the stem
                    // ageable.setAge(0);
                    block.setBlockData(ageable);
                } else if (material == Material.MELON_STEM) {
                    // Generate pumpkins randomly on adjacent vacant dirt-based blocks
                    generateMelons(block.getLocation());
                    // Reset age for the stem
                    ageable.setAge(0);
                    block.setBlockData(ageable);
                }
            case NETHER_WART:
                // Simulate growth by advancing growth stages
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    ageable.setAge(ageable.getAge() + 1);
                    block.setBlockData(ageable);
                }
                break;
            case COCOA:
                // Simulate cocoa bean growth by advancing growth stages
                Cocoa cocoa = (Cocoa) block.getBlockData();
                if (cocoa.getAge() < 2) { // Maximum age of cocoa is 2
                    cocoa.setAge(cocoa.getAge() + 1);
                    block.setBlockData(cocoa);
                }
                break;
            case SUGAR_CANE:
                // Simulate sugar cane growth
                ageable = (Ageable) block.getBlockData();
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    ageable.setAge(ageable.getAge() + 1);
                    block.setBlockData(ageable);
                } else {
                    // Attempt to grow sugar cane upward if there's space
                    if (block.getRelative(BlockFace.UP).getType() == Material.AIR &&
                            block.getRelative(BlockFace.DOWN, 2).getType() != Material.SUGAR_CANE) {
                        block.getRelative(BlockFace.UP).setType(Material.SUGAR_CANE);
                    }
                }
                break;
            default:
                // Handle other block types if needed
                break;
        }
    }

    private void generatePumpkins(Location stemLocation) {
        // Define blocks where pumpkins can generate
        Material[] validBlocks = {Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
                Material.FARMLAND, Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS};
        // Define directions around the stem where pumpkins can generate
        BlockFace[] directions = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        // Iterate over adjacent blocks
        for (BlockFace direction : directions) {
            Block adjacentBlock = stemLocation.getBlock().getRelative(direction);
            // Check if the block is valid for pumpkin generation
            if (Arrays.asList(validBlocks).contains(adjacentBlock.getType())) {
                // Check if the block above is air
                Block aboveBlock = adjacentBlock.getRelative(BlockFace.UP);
                if (aboveBlock.getType() == Material.AIR) {
                    // Random chance for a pumpkin block to generate
                    if (Math.random() < 0.25) { // Adjust probability as needed
                        aboveBlock.setType(Material.PUMPKIN);
                    }
                }
            }
        }
    }

    private void generateMelons(Location stemLocation) {
        // Define blocks where Melons can generate
        Material[] validBlocks = {Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
                Material.FARMLAND, Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS};
        // Define directions around the stem where Melons can generate
        BlockFace[] directions = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        // Iterate over adjacent blocks
        for (BlockFace direction : directions) {
            Block adjacentBlock = stemLocation.getBlock().getRelative(direction);
            // Check if the block is valid for Melon generation
            if (Arrays.asList(validBlocks).contains(adjacentBlock.getType())) {
                // Check if the block above is air
                Block aboveBlock = adjacentBlock.getRelative(BlockFace.UP);
                if (aboveBlock.getType() == Material.AIR) {
                    // Random chance for a Melon block to generate
                    if (Math.random() < 0.25) { // Adjust probability as needed
                        aboveBlock.setType(Material.MELON);
                    }
                }
            }
        }
    }

    private void simulateMobSpawners() {
        long currentTime = System.currentTimeMillis();

        // Check if the cooldown period has passed since the last spawn
        if (currentTime - lastSpawnTime < delay) {
            return; // Cooldown period not elapsed yet
        }

        for (Location spawnerLocation : mobSpawnerLocations) {
            if (spawnerLocation.getWorld() == null) continue;

            World world = spawnerLocation.getWorld();
            Block spawnerBlock = spawnerLocation.getBlock();
            if (!(spawnerBlock.getState() instanceof CreatureSpawner)) {
                continue;
            }

            CreatureSpawner spawner = (CreatureSpawner) spawnerBlock.getState();
            EntityType spawnType = spawner.getSpawnedType();

            // Check mob count for this type
            if (isOverMobLimit(world, spawnType)) {
                continue; // Skip spawning if over limit
            }

            for (int i = 0; i < 4; i++) {
                double spawnX = spawnerLocation.getX() + (Math.random() * 9 - 4);
                double spawnY = spawnerLocation.getY() + (Math.random() * 3);
                double spawnZ = spawnerLocation.getZ() + (Math.random() * 9 - 4);

                Location spawnLocation = new Location(world, spawnX, spawnY, spawnZ);

                // Check for direct line of sight from spawner to spawn location
                if (isValidSpawnLocation(spawnLocation) && hasLineOfSight(spawnerLocation, spawnLocation)) {
                    assert spawnType != null;

                    // Spawn the entity
                    LivingEntity spawnedEntity = spawnConstantlyLoadedEntity(spawnLocation, spawnType);
                    if (spawnedEntity != null) {
                        lastSpawnTime = currentTime; // Update the last spawn time
                        delay = minDelay + (long) (Math.random() * (maxDelay - minDelay)); // Randomize delay for each spawn
                        break; // Exit the loop after successful spawn to avoid overcrowding
                    }
                }
            }
        }
    }

    private boolean hasLineOfSight(Location start, Location end) {
        World world = start.getWorld();
        if (!end.getWorld().equals(world)) {
            return false; // Start and end locations are not in the same world
        }

        int points = (int) start.distance(end);
        for (int i = 0; i <= points; i++) {
            double x = start.getX() + (end.getX() - start.getX()) * i / points;
            double y = start.getY() + (end.getY() - start.getY()) * i / points;
            double z = start.getZ() + (end.getZ() - start.getZ()) * i / points;

            // Check if the current point is not air (indicating an obstruction)
            if (!world.getBlockAt(new Location(world, x, y, z)).getType().isAir()) {
                return false; // Obstruction found, no line of sight
            }
        }
        return true; // No obstructions, line of sight is clear
    }

    private boolean isValidSpawnLocation(Location spawnLocation) {
        // Check if the spawn location and perhaps the block above it are not solid.
        // This is a simplistic way to ensure there's likely room for the mob to spawn without suffocation.

        return spawnLocation.getBlock().getType().isAir() &&
                spawnLocation.clone().add(0, 1, 0).getBlock().getType().isAir();
    }

    private boolean isOverMobLimit(World world, EntityType entityType) {
        int count = 0;

        for (Entity entity : world.getEntities()) {
            if (entity.getType() == entityType) {
                count++;
            }
        }

        return count >= maxMobs;
    }

    private LivingEntity spawnConstantlyLoadedEntity(Location location, EntityType entityType) {
        if (location.getBlock().getType().isAir()) {
            LivingEntity spawnedEntity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
            // Set the entity as persistent
            spawnedEntity.getPersistentDataContainer().set(
                    Objects.requireNonNull(NamespacedKey.fromString("persistent_spawned_entity")),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            spawnedEntity.setRemoveWhenFarAway(false);
            return spawnedEntity;
        } else {
            return null;
        }
    }

    private void checkDestroyedMobSpawners() {
        Set<Location> spawnersToRemove = new HashSet<>();
        for (Location spawnerLocation : mobSpawnerLocations) {
            Block spawnerBlock = spawnerLocation.getBlock();
            if (spawnerBlock.getType() != Material.SPAWNER) {
                // The block at the spawner location is not a mob spawner anymore
                spawnersToRemove.add(spawnerLocation);
                getLogger().warning("A mob spawner at " + spawnerLocation + " has been destroyed!");
                // Remove destroyed mob spawners from the list
                mobSpawnerLocations.removeAll(spawnersToRemove);
                saveSpawnerLocationToConfig();
            }
        }
    }

    private void updateMobSpawnerLocations(Location location, int radius) {
        World world = location.getWorld();
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();

        boolean foundSpawner = false;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int blockX = centerX + x;
                    int blockY = centerY + y;
                    int blockZ = centerZ + z;

                    if (world.getBlockAt(blockX, blockY, blockZ).getType() == Material.SPAWNER) {
                        mobSpawnerLocations.add(new Location(world, blockX, blockY, blockZ));
                        foundSpawner = true;
                    }
                }
            }
        }
        if (foundSpawner) {
            saveSpawnerLocationToConfig();
        }
    }

    private void saveSpawnerLocationToConfig() {
        // Save spawner location to config for persistence
        config.set("spawnerLocations", null);
        config.set("spawnerLocations", mobSpawnerLocations.stream().map(this::locationToString).collect(Collectors.toList()));
        saveConfig();
    }

    private String locationToString(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void loadSpawnerLocationsFromConfig() {
        List<String> spawnerLocations = config.getStringList("spawnerLocations");
        for (String spawnerString : spawnerLocations) {
            String[] parts = spawnerString.split(",");
            if (parts.length == 4) {
                World world = getServer().getWorld(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if (world != null) {
                    mobSpawnerLocations.add(new Location(world, x, y, z));
                }
            }
        }
    }

    private void updateMobSpawnerLocationsAfterRemoval() {
        mobSpawnerLocations.clear();
        loadSpawnerLocationsFromConfig(); // Reload spawner locations from config
        // Iterate over all farms and add their mob spawner locations to the list
        for (FarmLocation farmLocation : farmLocations) {
            Location location = farmLocation.getLocation();
            int radius = farmLocation.getRadius();
            updateMobSpawnerLocations(location, radius);
        }
    }

    private void removeMobSpawnerLocations(Location location, int radius) {
        mobSpawnerLocations.removeIf(spawnerLocation -> isLocationWithinFarmRadius(spawnerLocation, location, radius));

        // Update the configuration after removing mob spawner locations
        saveSpawnerLocationToConfig();
    }

    private void addFarmLocation(Location location, CommandSender sender, String farmName, int radius) {
        World.Environment environment = location.getWorld().getEnvironment();

        // Check if the farm name already exists
        if (farmName != null && farmNameExists(farmName)) {
            sender.sendMessage(ChatColor.RED + "A farm with the name " + ChatColor.GOLD + "'" + farmName + "'" + ChatColor.RED + " already exists.");
            return;
        }

        // Ensure that the radius is within the allowed range
        radius = Math.min(radius, maxRadius); // Use the smaller value between provided radius and max radius

        // Check if there's another farm within the radius
        if (isFarmRadiusOverlapping(location, radius, environment)) {
            sender.sendMessage(ChatColor.RED + "Adding another farm here will cause farm overlaps. Please either increase the radius of the existing farm or move further away from other farms.");
            return;
        }

        // Round the coordinates to the nearest whole number
        int roundedX = (int) Math.round(location.getX());
        int roundedY = (int) Math.round(location.getY());
        int roundedZ = (int) Math.round(location.getZ());

        // Create a new Location object with rounded coordinates and same world
        Location roundedLocation = new Location(location.getWorld(), roundedX, roundedY, roundedZ);
        FarmLocation newFarm = new FarmLocation(roundedLocation, farmName, radius, environment); // Pass environment enum

        farmLocations.add(newFarm);
        String addedLocation = ChatColor.GREEN + "Farm location added" + (farmName != null ? " as " + farmName : "") + " at: "
                + roundedX + " " + roundedY + " " + roundedZ + " with radius " + radius + " in the " + worldNameFromEnvironment(environment);
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage(addedLocation);
        } else {
            getLogger().info(addedLocation);
        }

        scanAndProtectVillagers(newFarm);
        updateMobSpawnerLocations(location, radius);
        saveFarmLocations();
    }


    private void protectVillagersInFarms() {
        for (FarmLocation farmLocation : farmLocations) {
            if (farmLocation == null || farmLocation.getLocation() == null) continue;
            scanAndProtectVillagers(farmLocation);
        }
    }

    private boolean isFarmRadiusOverlapping(Location location, int radius, World.Environment environment) {
        for (FarmLocation farmLocation : farmLocations) {
            // Check if farm locations are in the same world type
            if (farmLocation.getEnvironment().equals(environment)) {
                // Check if the locations are in the same world
                if (!farmLocation.getLocation().getWorld().getName().equals(location.getWorld().getName())) {
                    continue; // Skip if different worlds
                }

                double distanceSquared = farmLocation.getLocation().distanceSquared(location);
                int combinedRadius = farmLocation.getRadius() + radius;
                if (distanceSquared <= (combinedRadius * combinedRadius)) {
                    return true; // Overlapping radii
                }
            }
        }
        return false; // No overlapping radii or different world types
    }

    private boolean farmNameExists(String farmName) {
        for (FarmLocation farmLocation : farmLocations) {
            if (farmName.equalsIgnoreCase(farmLocation.getFarmName())) {
                return true;
            }
        }
        return false;
    }

    private void scanAndProtectVillagers(FarmLocation farmLocation) {
        if (farmLocation == null || farmLocation.getLocation() == null) {
            getLogger().warning("Invalid farm location or world. Unable to protect villagers.");
            return;
        }

        Location location = farmLocation.getLocation();
        World world = location.getWorld(); // Directly get the World object from Location
        if (world == null) {
            getLogger().warning("World is not loaded for protecting villagers.");
            return;
        }

        int radius = farmLocation.getRadius();

        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity.getType() == EntityType.VILLAGER) {
                Villager villager = (Villager) entity;
                // Set the persistent flag to true to prevent despawning
                villager.getPersistentDataContainer().set(
                        Objects.requireNonNull(NamespacedKey.fromString("protect_villager")),
                        PersistentDataType.BYTE,
                        (byte) 1
                );
                villager.setRemoveWhenFarAway(false);
                getLogger().info(ChatColor.YELLOW + "Villager at " + entity.getLocation() + " set to be persistent, will not unload when in loaded chunk.");
            }
        }
    }



    private boolean isLocationWithinFarmRadius(Location location, Location farmLocation, int radius) {
        return location.getWorld().equals(farmLocation.getWorld()) &&
                location.distanceSquared(farmLocation) <= radius * radius;
    }



    private void removeFarmLocation(String identifier, CommandSender sender) {
        FarmLocation removedFarm = null;
        try {
            // Try to parse the identifier as an integer index
            int index = Integer.parseInt(identifier);
            if (index >= 0 && index < farmLocations.size()) {
                removedFarm = farmLocations.remove(index);
                removeMobSpawnerLocations(removedFarm.getLocation(), removedFarm.getRadius());
                updateMobSpawnerLocationsAfterRemoval();
            }
        } catch (NumberFormatException ignored) {
            // If parsing as an integer fails, consider it as a farm name
            Iterator<FarmLocation> iterator = farmLocations.iterator();
            while (iterator.hasNext()) {
                FarmLocation farmLocation = iterator.next();
                if (farmLocation.getFarmName() != null && farmLocation.getFarmName().equalsIgnoreCase(identifier)) {
                    removedFarm = farmLocation;
                    iterator.remove();
                    removeMobSpawnerLocations(removedFarm.getLocation(), removedFarm.getRadius());
                    break;
                }
            }
        }

        if (removedFarm != null) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Farm location at " + removedFarm.getLocation().getBlockX() + " " +
                        removedFarm.getLocation().getBlockY() + " " +
                        removedFarm.getLocation().getBlockZ() + " removed.");
            } else {
                getLogger().info(ChatColor.LIGHT_PURPLE + "Farm location at " + removedFarm.getLocation().getBlockX() + " " +
                        removedFarm.getLocation().getBlockY() + " " +
                        removedFarm.getLocation().getBlockZ() + " removed.");
            }
        } else {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(ChatColor.RED + "No farm location found with the identifier: " + identifier);
            } else {
                getLogger().info(ChatColor.RED + "No farm location found with the identifier: " + identifier);
            }
        }
        saveFarmLocations();
    }

    private void listFarmLocations(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "Active Farms:");
        for (int i = 0; i < farmLocations.size(); i++) {
            FarmLocation farmLocation = farmLocations.get(i);
            sender.sendMessage( i + ": " +
                    ChatColor.BLUE + farmLocation.getLocation().getBlockX() + " "
                    + ChatColor.BLUE + farmLocation.getLocation().getBlockY() + " "
                    + ChatColor.BLUE + farmLocation.getLocation().getBlockZ()
                    + (farmLocation.getFarmName() != null ? ChatColor.BLUE + " '" + farmLocation.getFarmName() + "'"
                    + " (" + farmLocation.getRadius() + " block radius) in the " + worldNameFromEnvironment(farmLocation.getEnvironment()): ""));
        }
        sender.sendMessage(ChatColor.YELLOW + "To remove farm, use /omd removefarm <farmName>/<farm#>");
    }

    private void loadFarmLocations() {
        if (config.contains("farmLocations")) {
            for (String key : Objects.requireNonNull(config.getConfigurationSection("farmLocations")).getKeys(false)) {
                double x = config.getDouble("farmLocations." + key + ".location.x");
                double y = config.getDouble("farmLocations." + key + ".location.y");
                double z = config.getDouble("farmLocations." + key + ".location.z");
                String worldName = config.getString("farmLocations." + key + ".location.world");

                assert worldName != null;
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    getLogger().warning("World '" + worldName + "' not found for farm location key: " + key);
                    continue; // Skip this iteration if the world is not found
                }

                String worldEnvironment = config.getString("farmLocations." + key + ".worldEnvironment");
                World.Environment environment;
                try {
                    assert worldEnvironment != null;
                    environment = World.Environment.valueOf(worldEnvironment.toUpperCase());
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid world environment '" + worldEnvironment + "' for farm location key: " + key);
                    continue; // Skip this iteration if the environment is invalid
                }

                int radius = config.getInt("farmLocations." + key + ".radius", defaultRadius);
                String farmName = config.getString("farmLocations." + key + ".farmName", null);
                Location location = new Location(world, x, y, z); // Pass the correct world here

                farmLocations.add(new FarmLocation(location, farmName, radius, environment)); // Pass environment correctly
            }
        }
    }


    private void saveFarmLocations() {
        // Clear the existing farmLocations section before saving the new state
        config.set("farmLocations", null); // This effectively clears the section

        // Re-save farm locations
        for (int i = 0; i < farmLocations.size(); i++) {
            FarmLocation farmLocation = farmLocations.get(i);
            Location location = farmLocation.getLocation();
            if (location == null) {
                getLogger().warning("Farm location at index " + i + " has a null location. Skipping.");
                continue;
            }

            // Serialize location manually
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            String worldName = location.getWorld() != null ? location.getWorld().getName() : null;

            config.set("farmLocations." + i + ".location.x", x);
            config.set("farmLocations." + i + ".location.y", y);
            config.set("farmLocations." + i + ".location.z", z);
            config.set("farmLocations." + i + ".location.world", worldName);

            World world = location.getWorld();
            if (world != null) {
                config.set("farmLocations." + i + ".worldEnvironment", world.getEnvironment().toString());
            } else {
                getLogger().warning("World reference is null for farm location at index " + i + ". Skipping.");
            }

            String farmName = farmLocation.getFarmName();
            if (farmName != null) {
                config.set("farmLocations." + i + ".farmName", farmName);
            }
            config.set("farmLocations." + i + ".radius", farmLocation.getRadius());
        }

        // Attempt to save the config
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().warning(ChatColor.RED + "Failed to save farm locations and settings to config.yml!");
        }
    }

    private void setMaxRadius(CommandSender sender, int radius) {
        if (sender.hasPermission("oldmacdonald.setmaxradius")) {
            if (radius > 0) {
                maxRadius = radius;
                config.set("settings.maxRadius", maxRadius);
                sender.sendMessage(ChatColor.GREEN + "Maximum radius set to " + maxRadius + " blocks.");
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    getLogger().warning(ChatColor.RED + "Failed to save farm locations and settings to config.yml!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Radius must be a positive integer.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
    }

    private void setDefaultRadius(CommandSender sender, int radius) {
        if (sender.hasPermission("oldmacdonald.setdefaultradius")) {
            if (radius > 0) {
                defaultRadius = radius;
                config.set("settings.defaultRadius", defaultRadius);
                sender.sendMessage(ChatColor.GREEN + "Default radius set to " + defaultRadius + " blocks.");
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    getLogger().warning(ChatColor.RED + "Failed to save farm locations and settings to config.yml!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Radius must be a positive integer less than the Maximum Radius.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
    }

    private void setMaxMobs(CommandSender sender, int mobCount) {
        if (sender.hasPermission("oldmacdonald.setmaxmobs")) {
            if (mobCount > 0) {
                maxMobs = mobCount;
                config.set("settings.maxMobs", maxMobs);
                sender.sendMessage(ChatColor.GREEN + "Maximum mobs per farm set to " + maxMobs + ".");
                try {
                    config.save(configFile);
                } catch (IOException e) {
                    getLogger().warning(ChatColor.RED + "Failed to save farm locations and settings to config.yml!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Mob count must be a positive integer.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== OldMacDonald Help ===");
        sender.sendMessage(ChatColor.YELLOW + "/omd addfarm current <farmName> [radius]" + ChatColor.WHITE + " - Add a farm at your current location.");
        sender.sendMessage(ChatColor.YELLOW + "/omd addfarm <x> <y> <z> [farmName] [radius]" + ChatColor.WHITE + " - Add a farm at specified coordinates.");
        sender.sendMessage(ChatColor.YELLOW + "/omd removefarm <identifier>" + ChatColor.WHITE + " - Remove a farm by name or index.");
        sender.sendMessage(ChatColor.YELLOW + "/omd listfarms" + ChatColor.WHITE + " - List all active farms.");
        sender.sendMessage(ChatColor.YELLOW + "/omd info" + ChatColor.WHITE + " - Get plugin information.");
        if(sender.hasPermission("oldmacdonald.setmaxradius")) {
            sender.sendMessage(ChatColor.YELLOW + "/omd setmaxradius <radius>" + ChatColor.WHITE + " - Set the maximum radius for farms.");
        }
        if (sender.hasPermission("oldmacdonald.setdefaultradius")) {
            sender.sendMessage(ChatColor.YELLOW + "/omd setdefaultradius <radius>" + ChatColor.WHITE + " - Set the default radius for new farms.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Send a welcome message to the player when they join
        Player player = event.getPlayer();
        TextComponent.Builder messageBuilder = Component.text()
                .append(Component.text("Welcome ").color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(Component.text(player.getName()).color(net.kyori.adventure.text.format.NamedTextColor.GREEN))
                .append(Component.text("! To get started with OldMacDonald, use /omd. Remember to only use OldMacDonald in always loaded chunks ")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(Component.text("(We recommend KeepChunks, but any chunk loader should work)").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));

        // Prompt players to report bugs or give feedback
        TextComponent.Builder feedbackMessageBuilder = Component.text()
                .append(Component.text("If you encounter any bugs or have feedback, please report them ").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(Component.text("here").color(net.kyori.adventure.text.format.NamedTextColor.BLUE).decorate(net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
                .clickEvent(ClickEvent.openUrl("https://forms.gle/gCDUhkVA3mT5Qz8DA"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open feedback form")));

        player.sendMessage(messageBuilder.build());
        player.sendMessage(feedbackMessageBuilder.build());
    }

    private void updateConfigForOldFarms() {
        if (config.contains("farmLocations")) {
            ConfigurationSection farmLocationsSection = config.getConfigurationSection("farmLocations");
            if (farmLocationsSection == null) {
                getLogger().warning("Farm locations section is missing in the config.");
                return;
            }

            for (String key : farmLocationsSection.getKeys(false)) {
                String farmKey = "farmLocations." + key;
                // Check if the environment name is present and valid
                String worldEnvironment = config.getString(farmKey + ".worldEnvironment");
                if (worldEnvironment == null) {
                    getLogger().warning("Missing world environment for farm location key: " + key);
                    continue; // Skip this iteration
                }

                World.Environment environment;
                try {
                    environment = World.Environment.valueOf(worldEnvironment);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid world environment '" + worldEnvironment + "' for farm location key: " + key);
                    continue; // Skip this iteration
                }

                // Update configuration with environment name
                config.set(farmKey + ".worldEnvironment", environment.toString());

                if (config.contains(farmKey + ".x") && config.contains(farmKey + ".y") && config.contains(farmKey + ".z")) {
                    double x = config.getDouble(farmKey + ".x");
                    double y = config.getDouble(farmKey + ".y");
                    double z = config.getDouble(farmKey + ".z");
                    World world = getServer().getWorlds().get(0);
                    Location location = new Location(world, x, y, z);

                    // Check and assign default radius if missing
                    if (!config.contains(farmKey + ".radius")) {
                        config.set(farmKey + ".radius", defaultRadius);
                        getLogger().info("Updated farm location at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + " with default radius and environment: " + environment);
                    }

                    // Update spawner locations for old farms
                    int radius = config.getInt(farmKey + ".radius", defaultRadius);
                    updateMobSpawnerLocations(location, radius);
                }
            }
            // Save the updated config
            saveConfig();
        }
    }


    private String worldNameFromEnvironment(World.Environment environment) {
        switch (environment) {
            case NORMAL:
                return "Overworld";
            case NETHER:
                return "Nether";
            case THE_END:
                return "End";
            default:
                return "Unknown"; // Handle other environments if needed
        }
    }
}