package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Utils.uBlock;
import com.Acrobot.ChestShop.Utils.uSign;
import net.minecraft.server.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ShopUtils
{
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final BlockFace[] CHEST_FACES = new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /**
     * ChestShop sign validation pattern (line index 2, i.e. the 3rd line).
     * Valid examples: "B 64 S", "B 10:5 S", "S 1 B", "B 64:32 S", etc.
     * Mirrors the core check performed by uSign.isValid().
     */
    private static final Pattern CHESTSHOP_PRICE_PATTERN = Pattern.compile("^[BS]\\s[\\d: ]+[BS]$");

    public static List<WrappedShop> getShopsInRegion(World world, int startX, int endX, int startZ, int endZ, boolean tryUnload)
    {
        List<WrappedShop> shops = new ArrayList<>();
        int chunksProcessed = 0;

        int minX = Math.min(startX, endX);
        int maxX = Math.max(startX, endX);
        int minZ = Math.min(startZ, endZ);
        int maxZ = Math.max(startZ, endZ);

        for (int chunkX = (minX >> 4); chunkX < (maxX >> 4); chunkX++)
        {
            for (int chunkZ = (minZ >> 4); chunkZ < (maxZ >> 4); chunkZ++)
            {
                boolean chunkAlreadyLoaded = world.isChunkLoaded(chunkX, chunkZ);

                if (chunkAlreadyLoaded)
                {
                    // Chunk is live - use the fast Bukkit tile-entity path.
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    if (chunk == null) continue;

                    List<WrappedShop> chunkShops = getValidShopsInChunk(chunk);
                    shops.addAll(chunkShops);
                } else {
                    // Chunk is not loaded - read directly from the region file so we
                    // never force a chunk load just to scan for shops.
                    try
                    {
                        List<WrappedShop> chunkShops = collectShopsViaNBT(world, chunkX, chunkZ);
                        shops.addAll(chunkShops);
                    } catch (IOException e) {
                        LOGGER.warning("[ShopDB] Failed to read NBT for chunk " + chunkX + "," + chunkZ + ": " + e.getMessage());
                    }
                }

                chunksProcessed++;

                // Park between chunks to keep the server breathing.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }

        List<WrappedShop> finalShopsList = getUnduplicatedList(shops);

        if (chunksProcessed > 0 && !finalShopsList.isEmpty())
            LOGGER.info("[ShopDB] Processed " + chunksProcessed + " chunks and found " + finalShopsList.size() + " shop(s)!");
        return finalShopsList;
    }

    /**
     * Reads chunk NBT directly from the region file and returns any valid ChestShop
     * shops found within it, without ever loading the chunk into the world.
     */
    private static List<WrappedShop> collectShopsViaNBT(World world, int chunkX, int chunkZ) throws IOException
    {
        DataInputStream in = RegionFileCache.c(new File(world.getName()), chunkX, chunkZ);
        if (in == null) return Collections.emptyList(); // chunk has never been generated

        NBTTagCompound chunkNbt = CompressedStreamTools.a((DataInput) in);
        NBTTagCompound level = chunkNbt.k("Level");
        NBTTagList tileEntities = level.l("TileEntities");

        // --- Pass 1: bucket all sign and chest NBT compounds by "x,y,z" key. ---

        // LinkedHashMap preserves insertion order for deterministic results.
        Map<String, NBTTagCompound> signNbts = new LinkedHashMap<>();
        Map<String, NBTTagCompound> chestNbts = new HashMap<>();

        for (int i = 0; i < tileEntities.c(); i++)
        {
            NBTTagCompound te = (NBTTagCompound) tileEntities.a(i);
            String id = te.getString("id");

            if ("Sign".equals(id))
                signNbts.put(coordKey(te), te);
            else if ("Chest".equals(id))
                chestNbts.put(coordKey(te), te);
        }

        if (signNbts.isEmpty() || chestNbts.isEmpty()) return Collections.emptyList();

        // --- Pass 2: validate each sign and associate it with adjacent chests. ---

        List<WrappedShop> shops = new ArrayList<>();

        for (NBTTagCompound signNbt : signNbts.values())
        {
            // Replicate uSign.isValid(): line 3 (Text3) must match the price pattern.
            String priceLine = signNbt.getString("Text3");
            if (priceLine == null || !CHESTSHOP_PRICE_PATTERN.matcher(priceLine.trim()).matches())
                continue;

            int sx = signNbt.e("x");
            int sy = signNbt.e("y");
            int sz = signNbt.e("z");

            // Replicate uBlock.findChest(): block below first, then cardinal neighbors.
            NBTTagCompound primaryChest = findAdjacentChestNBT(chestNbts, sx, sy, sz);
            if (primaryChest == null) continue;

            // Collect the primary chest and any double-chest partner.
            List<NBTTagCompound> attachedChests = new ArrayList<>();
            attachedChests.add(primaryChest);

            int cx = primaryChest.e("x");
            int cy = primaryChest.e("y");
            int cz = primaryChest.e("z");

            // Replicate the face-loop in getValidShopsInChunk (N, E, S, W offsets).
            int[][] cardinalOffsets = {{0,0,-1},{1,0,0},{0,0,1},{-1,0,0}};
            for (int[] d : cardinalOffsets)
            {
                NBTTagCompound neighbour = chestNbts.get(coordKey(cx + d[0], cy + d[1], cz + d[2]));
                if (neighbour != null)
                    attachedChests.add(neighbour);
            }

            shops.add(new WrappedShop(signNbt, world.getName(), attachedChests));
        }

        return shops;
    }

    // -------------------------------------------------------------------------
    // NBT coordinate helpers
    // -------------------------------------------------------------------------

    /** Returns a "x,y,z" key from a tile-entity NBTTagCompound. */
    private static String coordKey(NBTTagCompound te)
    {
        return te.e("x") + "," + te.e("y") + "," + te.e("z");
    }

    /** Returns a "x,y,z" key from explicit coordinates. */
    private static String coordKey(int x, int y, int z)
    {
        return x + "," + y + "," + z;
    }

    /**
     * Replicates {@code uBlock.findChest}: check the block directly below the sign,
     * then all four cardinal neighbors at the same Y level.
     */
    private static NBTTagCompound findAdjacentChestNBT(Map<String, NBTTagCompound> chestNbts,
                                                       int sx, int sy, int sz)
    {
        // Block directly below the sign.
        NBTTagCompound below = chestNbts.get(coordKey(sx, sy - 1, sz));
        if (below != null) return below;

        // Four cardinal neighbors at sign Y.
        int[][] offsets = {{0,0,-1},{1,0,0},{0,0,1},{-1,0,0}};
        for (int[] d : offsets)
        {
            NBTTagCompound neighbour = chestNbts.get(coordKey(sx + d[0], sy + d[1], sz + d[2]));
            if (neighbour != null) return neighbour;
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Remaining unchanged methods
    // -------------------------------------------------------------------------

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
        for (BlockState blockState : chunk.getTileEntities())
        {
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
        }
        return shops;
    }
}