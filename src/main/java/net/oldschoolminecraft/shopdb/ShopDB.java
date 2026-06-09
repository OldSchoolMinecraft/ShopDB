package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.ChestShop;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ShopDB extends JavaPlugin
{
    private Gson gsonMin = new Gson();
    private Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
    private ShopDBConfig config;
    private static final String LMK_FILE_PATH = "landmarks.v2.json";

    public void onEnable()
    {
        config = new ShopDBConfig(new File(getDataFolder(), "config.yml"));
        ChestShop chestShop = (ChestShop) getServer().getPluginManager().getPlugin("ChestShop");
        if (chestShop == null)
            System.out.println("[ShopDB] ChestShop appears to not have been loaded yet. The first sweep will be delayed by 3 minutes.");

        // run shop index once per hour with a 3-minute initial delay from startup
        getServer().getScheduler().scheduleAsyncRepeatingTask(this, () -> runShopIndex(null), (20 * 60) * 3, (20 * 60) * 60);

        System.out.println("ShopDB enabled");
    }

    private synchronized void runShopIndex(CommandSender sender)
    {
        long startMs = System.currentTimeMillis();
        ArrayList<SearchRegion> searchRegions = config.getSearchRegions();
        searchRegions.addAll(getLandmarkSearchRegions());
        ArrayList<WrappedShop> shops = new ArrayList<>();
        for (SearchRegion region : searchRegions)
        {
            World world = Bukkit.getWorld(region.worldName);
            List<WrappedShop> foundShops = ShopUtils.getShopsInRegion(world, region.startX, region.endX, region.startZ, region.endZ, config.getBoolean("tryUnloadChunks", true));
            if (sender != null) sender.sendMessage(ChatColor.GRAY + "Found " + foundShops.size() + " shop(s) in region: " + region.regionName);
            shops.addAll(foundShops);
            // park to give the CPU room to breathe.
            // this function SHOULD be run asynchronously, so it shouldn't be an issue.
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }

        List<ShopDataModel> serializable = new ArrayList<>();
        for (WrappedShop shop : shops)
            serializable.add(shop.getSerializable());

        try (FileWriter writer = new FileWriter(config.getString("dataExportFile")))
        {
            gsonMin.toJson(serializable, writer);
        } catch (IOException e) {
            System.err.println("Failed to save shop data!");
            e.printStackTrace(System.err);
        }

        File formattedExportFile = new File(config.getString("dataExportFile"));
        String rawPath = formattedExportFile.getName().replace(".json", "");
        try (FileWriter writer = new FileWriter(rawPath + "-formatted.json"))
        {
            gsonPretty.toJson(serializable, writer);
        } catch (IOException e) {
            System.err.println("Failed to save shop data!");
            e.printStackTrace(System.err);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        String msg = "Finished shop DB update in " + elapsedMs + "ms";
        System.out.println("[ShopDB] " + msg);
        if (sender != null) sender.sendMessage(ChatColor.GREEN + msg);
    }

    private ArrayList<SearchRegion> getLandmarkSearchRegions()
    {
        ArrayList<SearchRegion> regions = new ArrayList<>();
        ArrayList<LandmarkData> landmarks = null;

        File file = new File(LMK_FILE_PATH);
        if (!file.exists())
        {
            System.err.println("[ShopDB] Failed to get search regions from landmarks! (landmarks plugin is likely not installed, or data hasn't been generated yet)");
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(file))
        {
            Gson gson = new Gson();
            Type listType = new TypeToken<ArrayList<LandmarkData>>() {}.getType();
            landmarks = gson.fromJson(reader, listType);
        } catch (IOException e) {
            System.err.println("Failed to reload landmarks: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        if (landmarks == null)
        {
            System.err.println("[ShopDB] Failed to parse landmarks file, they will not be indexed! (the file is likely corrupt or contains invalid syntax)");
            return new ArrayList<>();
        }

        for (LandmarkData lmk : landmarks)
        {
            int defaultSearchRadius = config.getInt("defaultSearchRadius", 100);
            int lmkSearchRadius = config.getInt("lmk." + lmk.name + ".radius", defaultSearchRadius);
            regions.add(createSearchRegion(lmk.name, lmk.worldName, lmk.getBlockX(), lmk.getBlockZ(), lmkSearchRadius));
        }

        return regions;
    }

    private SearchRegion createSearchRegion(
            String regionName,
            String worldName,
            int x,
            int z,
            int radius)
    {
        int startX = x - radius;
        int endX = x + radius;

        int startZ = z - radius;
        int endZ = z + radius;

        return new SearchRegion(
                regionName,
                worldName,
                startX,
                endX,
                startZ,
                endZ
        );
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (label.equalsIgnoreCase("runshopindex"))
        {
            if (!(sender.hasPermission("shopdb.runindex") || sender.isOp()))
            {
                sender.sendMessage(ChatColor.RED + "You don't have permission to run this command!");
                return true;
            }

            sender.sendMessage(ChatColor.GRAY + "Running shop index...");
            Bukkit.getScheduler().scheduleAsyncDelayedTask(this, () -> runShopIndex(sender), 0L);
            return true;
        }

        if (label.equalsIgnoreCase("sdbr"))
        {
            if (!(sender.hasPermission("shopdb.reload") || sender.isOp()))
            {
                sender.sendMessage(ChatColor.RED + "You don't have permission to run this command!");
                return true;
            }

            config.reload();
            sender.sendMessage(ChatColor.GREEN + "Reloaded ShopDB configuration");
        }

        if (label.equalsIgnoreCase("testchunkindex"))
        {
            if (!(sender.hasPermission("shopdb.runindex") || sender.isOp()))
            {
                sender.sendMessage(ChatColor.RED + "You don't have permission to run this command!");
                return true;
            }

            if (!(sender instanceof Player))
            {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }

            Player ply = (Player) sender;
            int x = ply.getLocation().getBlockX() >> 4;
            int z = ply.getLocation().getBlockZ() >> 4;
            List<WrappedShop> shops = ShopUtils.getValidShopsInChunk(ply.getWorld().getChunkAt(x, z));
            ply.sendMessage(ChatColor.GREEN + "Found " + shops.size() + " in your current chunk");
        }

        return false;
    }

    public void onDisable()
    {
        System.out.println("ShopDB disabled");
    }
}
