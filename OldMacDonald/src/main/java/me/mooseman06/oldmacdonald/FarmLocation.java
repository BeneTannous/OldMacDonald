package me.mooseman06.oldmacdonald;
import org.bukkit.Location;
import org.bukkit.World;

public class FarmLocation {
    private final Location location;
    private final String farmName;
    private final int radius;
    private final World.Environment environment; // Use World.Environment enum

    public FarmLocation(Location location, String farmName, int radius, World.Environment environment) {
        this.location = location;
        this.farmName = farmName;
        this.radius = radius;
        this.environment = environment;
    }

    public Location getLocation() {
        return location;
    }

    public String getFarmName() {
        return farmName;
    }

    public int getRadius() {
        return radius;
    }

    public World.Environment getEnvironment() {
        return environment;
    }
}
