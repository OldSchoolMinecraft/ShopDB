package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.ChestShop;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        // run shop index once per hour with a 3-minute initial delay from startup if chest shop hasn't been loaded yet
        getServer().getScheduler().scheduleAsyncRepeatingTask(this, () -> runShopIndex(null), (chestShop == null) ? (20 * 60) * 3 : 0L, (20 * 60) * 60);

        System.out.println("ShopDB enabled");
    }

    private synchronized void runShopIndex(CommandSender sender)
    {
        Instant start = Instant.now();
        ArrayList<SearchRegion> searchRegions = config.getSearchRegions();
        ArrayList<WrappedShop> shops = new ArrayList<>();
        for (SearchRegion region : searchRegions)
        {
            World world = Bukkit.getWorld(region.worldName);
            List<WrappedShop> foundShops = ShopUtils.getShopsInRegion(world, region.startX, region.endX, region.startZ, region.endZ);
            if (sender != null) sender.sendMessage(ChatColor.GRAY + "Found " + foundShops.size() + " shop(s) in region: " + region.regionName);
            shops.addAll(foundShops);
        }

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

        Instant duration = Instant.now().minus(start.getNano(), ChronoUnit.NANOS);
        String msg = "Finished shop DB update in " + TimeUnit.NANOSECONDS.toSeconds(duration.getNano()) + " seconds";
        System.out.println("[ShopDB] " + msg);
        if (sender != null) sender.sendMessage(ChatColor.GREEN + msg);
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
            runShopIndex(sender);
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
