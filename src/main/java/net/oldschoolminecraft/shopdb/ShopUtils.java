package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Utils.uBlock;
import com.Acrobot.ChestShop.Utils.uSign;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

public class ShopUtils
{
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final BlockFace[] CHEST_FACES = new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    public static List<WrappedShop> getShopsInRegion(World world, int startX, int endX, int startZ, int endZ, boolean tryUnload)
    {
        List<WrappedShop> shops = new ArrayList<>();
        int chunksProcessed = 0;

        // Ensure startX is less than or equal to endX and startZ is less than or equal to endZ
        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minZ = Math.min(startZ, endZ);
        int maxZ = Math.max(startZ, endZ);

        // Iterate through chunks in the specified region
        for (int chunkX = (minX >> 4); chunkX < (maxX >> 4); chunkX++)
        {
            for (int chunkZ = (minZ >> 4); chunkZ < (maxZ >> 4); chunkZ++)
            {
                boolean chunkAlreadyLoaded = world.isChunkLoaded(chunkX, chunkZ);
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (chunk == null) continue; // for whatever reason it could be null, we must skip it.
                if (!chunkAlreadyLoaded)
                    chunk.load(false);

                chunksProcessed++;

                // Iterate through tile entities in the chunk
                List<WrappedShop> chunkShops = getValidShopsInChunk(chunk);
//                LOGGER.info("[ShopDB] Found " + chunkShops.size() + " shop(s) in chunk: " + chunkX + "," + chunkZ);
                shops.addAll(chunkShops);

                if (!chunkAlreadyLoaded && !isPlayerChunk(chunk) && tryUnload) chunk.unload(false);

                // park for 50 millis between chunks to give the CPU room to breathe.
                // we also want to make sure the server can catch up with all the loading and unloading.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
            }
        }

        List<WrappedShop> finalShopsList = getUnduplicatedList(shops);

        if (chunksProcessed > 0 && !finalShopsList.isEmpty())
            LOGGER.info("[ShopDB] Processed " + chunksProcessed + " chunks and found " + finalShopsList.size() + " shop(s)!");
        return finalShopsList;
    }

    private static boolean isPlayerChunk(Chunk chunk)
    {
        // if there are any Player's within the chunk itself, we can safely skip the view-distance check
        for (Entity ent : chunk.getEntities())
        {
            if (ent instanceof Player)
                return true;
        }

        int cx = chunk.getX();
        int cz = chunk.getZ();

        // check if any online players are within view distance of the given chunk
        for (Player player : Bukkit.getServer().getOnlinePlayers())
        {
            int pcx = player.getLocation().getBlockX() >> 4;
            int pcz = player.getLocation().getBlockZ() >> 4;

            // Beta 1.7.3 default view distance is 10 chunks, but we can grab the configured value
            if (Math.abs(pcx - cx) <= Bukkit.getViewDistance() && Math.abs(pcz - cz) <= Bukkit.getViewDistance())
                return true;
        }

        return false;
    }

    public static List<WrappedShop> getUnduplicatedList(List<WrappedShop> shops)
    {
        Set<String> seenHashes = new HashSet<>();
        List<WrappedShop> uniqueShops = new ArrayList<>();

        for (WrappedShop shop : shops)
        {
            String hash = shop.getHash();
            if (seenHashes.add(hash))
                uniqueShops.add(shop);
        }
        return uniqueShops;
    }

    public static List<WrappedShop> getValidShopsInChunk(Chunk chunk)
    {
        if (!chunk.isLoaded())
        {
            LOGGER.severe("[ShopDB] Shop search aborted for chunk @ " + chunk.getX() + ", " + chunk.getZ() + " because it wasn't loaded");
            return new ArrayList<>();
        }
        List<WrappedShop> shops = new ArrayList<>();
        BlockState[] tileEntities = chunk.getTileEntities();
//        if (tileEntities.length == 0)
//            LOGGER.warning("[ShopDB] There are no tile entities for chunk: " + chunk.getX() + ", " + chunk.getZ());
        for (BlockState blockState : chunk.getTileEntities())
        {
            // look for all signs
            if (blockState.getType() == Material.SIGN || blockState.getType() == Material.SIGN_POST || blockState.getType() == Material.WALL_SIGN)
            {
                Sign sign = (Sign) blockState;
                Chest chest = uBlock.findChest(sign);
                if (uSign.isValid(sign) && chest != null)
                {
                    List<Chest> attachedChests = new ArrayList<>();
                    attachedChests.add(chest);
                    for (BlockFace face : CHEST_FACES)
                    {
                        Block relativeBlock = chest.getBlock().getRelative(face);
                        if (relativeBlock.getType() == Material.CHEST)
                            attachedChests.add((Chest) relativeBlock.getState());
                    }
                    shops.add(new WrappedShop(sign, attachedChests.toArray(new Chest[] {})));
                }
            }

            /*if (blockState.getType() == Material.CHEST)
            {
                Chest chest = (Chest) blockState;
//                LOGGER.info("[ShopDB] Found chest at: " + chest.getBlock().getLocation());
                Sign sign = uBlock.findSign(chest.getBlock());
                if (sign != null)
                {
//                    LOGGER.info("[ShopDB] Found sign at: " + sign.getBlock().getLocation());
                    if (uSign.isValid(sign))
                    {
//                        LOGGER.info("[ShopDB] Valid sign at: " + sign.getBlock().getLocation());
                        shops.add(new WrappedShop(chest, sign));
                    } else {
                        LOGGER.info("[ShopDB] Invalid sign at: " + sign.getBlock().getLocation());
                    }
                } else {
                    LOGGER.info("[ShopDB] No sign found near chest at: " + chest.getBlock().getLocation());
                }
            }*/
        }
        return shops;
    }
}
