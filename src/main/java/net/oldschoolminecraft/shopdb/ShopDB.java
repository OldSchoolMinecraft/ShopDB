package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.ChestShop;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ShopDB extends JavaPlugin
{
    private Gson gson = new Gson();
    private ShopDBConfig config;

    public void onEnable()
    {
        config = new ShopDBConfig(new File(getDataFolder(), "config.yml"));
        ChestShop chestShop = (ChestShop) getServer().getPluginManager().getPlugin("ChestShop");
        if (chestShop == null)
            System.out.println("[ShopDB] ChestShop appears to not have been loaded yet. The first sweep will be delayed by 3 minutes.");

        getServer().getScheduler().scheduleAsyncRepeatingTask(this, () ->
        {
            Instant start = Instant.now();
            ArrayList<SearchRegion> searchRegions = config.getSearchRegions();
            for (SearchRegion region : searchRegions)
            {
                World world = Bukkit.getWorld(region.worldName);
                List<WrappedShop> shops = ShopUtils.getShopsInRegion(world, region.startX, region.endX, region.startZ, region.endZ);
                List<ShopDataModel> serializable = new ArrayList<>();
                for (WrappedShop shop : shops)
                    serializable.add(shop.getSerializable());
                try (FileWriter writer = new FileWriter(config.getString("dataExportFile")))
                {
                    gson.toJson(serializable, writer);
                } catch (IOException e) {
                    System.err.println("Failed to save shop data!");
                    e.printStackTrace(System.err);
                }
            }
            Instant duration = Instant.now().minus(start.getNano(), ChronoUnit.NANOS);
            System.out.println("[ShopDB] Finished shop DB update in " + TimeUnit.NANOSECONDS.toSeconds(duration.getNano()) + " seconds");
        }, (chestShop == null) ? (20 * 60) * 3 : 0L, (20 * 60) * 60); // once per hour with a 3-minute initial delay from startup if chest shop hasn't been loaded yet

        System.out.println("ShopDB enabled");
    }

    public void onDisable()
    {
        System.out.println("ShopDB disabled");
    }
}
