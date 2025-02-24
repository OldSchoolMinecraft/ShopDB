package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Utils.uBlock;
import com.Acrobot.ChestShop.Utils.uSign;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;

import java.util.ArrayList;
import java.util.List;

public class ShopUtils
{
    public static List<WrappedShop> getShopsInRegion(World world, int startX, int endX, int startZ, int endZ)
    {
        // get all loaded chunks inside the specified region
        List<Chunk> chunks = new ArrayList<>();
        for (int x = startX; x < endX; x++)
        {
            for (int z = startZ; z < endZ; z++)
            {
                Chunk chunk = world.getChunkAt(x, z);
                if (!chunk.isLoaded()) chunk.load(true);
                chunks.add(chunk);
            }
        }

        // get all valid chest shops from each chunk inside the specified region
        List<WrappedShop> shops = new ArrayList<>();
        for (Chunk chunk : chunks)
        {
            if (!chunk.isLoaded())
            {
                System.out.println("[ShopDB] Could not process chunk as it was unloaded @ " + chunk.getX() + ", " + chunk.getZ());
                continue;
            }
            List<WrappedShop> chestsFromChunk = getShopsInChunk(world, chunk.getX(), chunk.getZ());
            if (!chestsFromChunk.isEmpty()) shops.addAll(chestsFromChunk);
        }
        return shops;
    }

    public static List<WrappedShop> getShopsInChunk(World world, int x, int z)
    {
        List<WrappedShop> shops = new ArrayList<>();
        for (BlockState blockState : world.getChunkAt(x, z).getTileEntities())
        {
            if (blockState.getType() != Material.CHEST) continue;
            Chest chest = (Chest) blockState;
            Sign sign = uBlock.findSign(chest.getBlock());
            if (sign != null && uSign.isValid(sign)) shops.add(new WrappedShop(chest, sign));
        }
        return shops;
    }
}
