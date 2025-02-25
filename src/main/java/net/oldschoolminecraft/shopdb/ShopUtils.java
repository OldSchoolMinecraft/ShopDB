package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Utils.uBlock;
import com.Acrobot.ChestShop.Utils.uSign;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ShopUtils
{
    private static final Logger LOGGER = Bukkit.getLogger();

    public static List<WrappedShop> getShopsInRegion(World world, int startX, int endX, int startZ, int endZ)
    {
        List<WrappedShop> shops = new ArrayList<>();
        int chunksProcessed = 0;

        // Ensure startX is less than or equal to endX and startZ is less than or equal to endZ
        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minZ = Math.min(startZ, endZ);
        int maxZ = Math.max(startZ, endZ);

        // Iterate through chunks in the specified region
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++)
        {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++)
            {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (!chunk.isLoaded())
                    chunk.load(true);

                chunksProcessed++;

                // Iterate through tile entities in the chunk
                List<WrappedShop> chunkShops = getValidShopsInChunk(chunk);
//                LOGGER.info("[ShopDB] Found " + chunkShops.size() + " shop(s) in chunk: " + chunkX + "," + chunkZ);
                shops.addAll(chunkShops);
            }
        }

        LOGGER.info("[ShopDB] Processed " + chunksProcessed + " chunks and found " + shops.size() + " shop(s)!");
        return shops;
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
            if (blockState.getType() == Material.CHEST)
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
                    } /*else {
                        LOGGER.info("[ShopDB] Invalid sign at: " + sign.getBlock().getLocation());
                    }*/
                } /*else {
                    LOGGER.info("[ShopDB] No sign found near chest at: " + chest.getBlock().getLocation());
                }*/
            }
        }
        return shops;
    }
}
