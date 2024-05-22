import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class OldMacDonald extends JavaPlugin implements CommandExecutor {
    
    private Location farmLocation; // Store the location of the farm
    private int radius = 128; // Default radius

    @Override
    public void onEnable() {
        // Register command executor
        getCommand("oldMacDonald").setExecutor(this);
        
        // Start simulating farm activity (e.g., plant growth and mob spawning) at random intervals
        startFarmSimulation();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        if (args.length == 2 && args[0].equalsIgnoreCase("setFarmLocation")) {
            if (args[1].equalsIgnoreCase("current")) {
                // Set farm location to player's current location
                farmLocation = player.getLocation();
                player.sendMessage("Farm location set to your current location: " + farmLocation.toString());
            } else if (args.length == 4) {
                // Set farm location to specified coordinates
                try {
                    double x = Double.parseDouble(args[1]);
                    double y = Double.parseDouble(args[2]);
                    double z = Double.parseDouble(args[3]);
                    farmLocation = new Location(player.getWorld(), x, y, z);
                    player.sendMessage("Farm location set to: " + farmLocation.toString());
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid coordinates. Please provide numeric values.");
                }
            } else {
                player.sendMessage("Usage: /oldMacDonald setFarmLocation current");
                player.sendMessage("Usage: /oldMacDonald setFarmLocation <x> <y> <z>");
            }
            return true;
        } else {
            return false;
        }
    }
    
    private void startFarmSimulation() {
        // Schedule the simulation with a random delay and repeat at random intervals
        Random random = new Random();
        int initialDelay = random.nextInt(20 * 60 * 10); // Random delay within 10 minutes
        int interval = (random.nextInt(9) + 1) * 20 * 60; // Random interval between 1 and 10 minutes

        getServer().getScheduler().runTaskTimer(this, this::simulateFarmActivity, initialDelay, interval);
    }

    private void simulateFarmActivity() {
        if (farmLocation == null) return;

        simulatePlantGrowth();
        simulateMobSpawning();
    }

    private void simulatePlantGrowth() {
        // Define the list of plant materials to simulate growth for
        List<Material> plantMaterials = Arrays.asList(
            Material.WHEAT, Material.POTATOES, Material.CARROTS, 
            Material.BEETROOTS, Material.MELON_STEM, Material.PUMPKIN_STEM,
            Material.NETHER_WART, Material.COCOA, Material.SUGAR_CANE
            // Add more plant materials as needed
        );

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y < 256; y++) { // Iterate over all possible y values (height)
                    Block block = farmLocation.getWorld().getBlockAt(farmLocation.getBlockX() + x, y, farmLocation.getBlockZ() + z);
                    Material material = block.getType();
                    if (plantMaterials.contains(material)) {
                        // If it is a plant material, simulate growth if possible
                        simulatePlantGrowth(block, material);
                    }
                }
            }
        }
    }

    private void simulatePlantGrowth(Block block, Material material) {
        switch (material) {
            case WHEAT:
            case POTATOES:
            case CARROTS:
            case BEETROOTS:
            case MELON_STEM:
            case PUMPKIN_STEM:
            case NETHER_WART:
                // Simulate growth by advancing growth stages if applicable
                if (block.getBlockData() instanceof Ageable) {
                    Ageable ageable = (Ageable) block.getBlockData();
                    if (ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(ageable.getAge() + 1);
                        block.setBlockData(ageable);
                    }
                }
                break;
            case COCOA:
                // Simulate cocoa bean growth by advancing growth stages if applicable
                Cocoa cocoa = (Cocoa) block.getBlockData();
                if (cocoa.getAge() < cocoa.getMaximumAge()) {
                    cocoa.setAge(cocoa.getAge() + 1);
                    block.setBlockData(cocoa);
                }
                break;
            case SUGAR_CANE:
                // Grow sugarcane if conditions allow
                Block blockAbove = block.getRelative(BlockFace.UP);
                if (blockAbove.getType() == Material.AIR) {
                    blockAbove.setType(Material.SUGAR_CANE);
                }
                break;
            default:
                // Handle other plant materials if needed
                break;
        }
    }

    private void simulateMobSpawning() {
        // Define the list of mob types to simulate spawning for
        List<EntityType> mobTypes = Arrays.asList(
            EntityType.SHEEP, EntityType.COW, EntityType.PIG,
            EntityType.CHICKEN, EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH
            // Add more mob types as needed
        );

        Random random = new Random();

        for (int i = 0; i < 5; i++) { // Spawn up to 5 mobs each simulation
            int x = farmLocation.getBlockX() + random.nextInt(radius * 2) - radius;
            int y = random.nextInt(256);
            int z = farmLocation.getBlockZ() + random.nextInt(radius * 2) - radius;

            Location spawnLocation = new Location(farmLocation.getWorld(), x, y, z);

            if (spawnLocation.getBlock().getType() == Material.AIR) {
                EntityType entityType = mobTypes.get(random.nextInt(mobTypes.size()));
                spawnLocation.getWorld().spawnEntity(spawnLocation, entityType);
            }
        }
    }
}
