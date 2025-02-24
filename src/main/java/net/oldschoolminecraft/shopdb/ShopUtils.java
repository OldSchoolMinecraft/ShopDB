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
        List<WrappedShop> shops = new ArrayList<>();

        // Iterate through chunks in the specified region
        for (int x = (startX >> 4); x <= (endX >> 4); x++)
        {
            for (int z = (startZ >> 4); z <= (endZ >> 4); z++)
            {
                Chunk chunk = world.getChunkAt(x, z);
                if (!chunk.isLoaded())
                    chunk.load();

                // Iterate through tile entities in the chunk
                for (BlockState blockState : chunk.getTileEntities())
                {
                    if (blockState.getType() == Material.CHEST)
                    {
                        Chest chest = (Chest) blockState;
                        Sign sign = uBlock.findSign(chest.getBlock());
                        if (sign != null && uSign.isValid(sign))
                        {
                            shops.add(new WrappedShop(chest, sign));
                        } else {
                            System.out.println("[ShopDB] Chest is missing sign or sign is invalid @ " + chest.getBlock().getLocation());
                        }
                    }
                }
            }
        }

        return shops;
    }
}
