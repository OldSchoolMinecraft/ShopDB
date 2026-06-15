package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Utils.uBlock;
import com.Acrobot.ChestShop.Utils.uSign;
import net.minecraft.server.*;
import org.bukkit.*;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftChest;
import org.bukkit.craftbukkit.block.CraftSign;
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

    private static final Location[] DEGUG_WATCH_POSITIONS = new Location[] {
            new Location(Bukkit.getWorld("world"), -42041, 64, -35486)
    };

    public static List<WrappedShop> getShopsInRegion(World world, int startX, int endX, int startZ, int endZ)
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
                    List<WrappedShop> chunkShops = collectUnloadedShops(world, chunkX, chunkZ);
                    shops.addAll(chunkShops);
                }

                chunksProcessed++;

                // Park between chunks to keep the server breathing.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        }

        List<WrappedShop> finalShopsList = getUnduplicatedList(shops);

        if (chunksProcessed > 0 && !finalShopsList.isEmpty())
            LOGGER.info("[ShopDB] Processed " + chunksProcessed + " chunks and found " + finalShopsList.size() + " shop(s)!");
        return finalShopsList;
    }

    /**
     * Reads chunk (and neighbors) NBT directly from the region file and returns any valid ChestShop
     * shops found within it, without ever loading the chunk into the world.
     */
    private static List<WrappedShop> collectUnloadedShops(World world, int chunkX, int chunkZ)
    {
        File regionDir = new File(world.getName(), "region");

        // primary chunk - if this doesn't exist, nothing to do.
        NBTTagCompound primaryLevel = readChunkLevel(regionDir, chunkX, chunkZ);
        if (primaryLevel == null)
        {
            if (new File("shopdb.debug").exists())
                System.err.println("[ShopDB] Attempted to index missing chunk: " + chunkX + "," + chunkZ);
            return Collections.emptyList();
        }

        // neighbors
        NBTTagCompound[] allLevels = new NBTTagCompound[]
        {
            primaryLevel,
            readChunkLevel(regionDir, chunkX + 1, chunkZ),
            readChunkLevel(regionDir, chunkX - 1, chunkZ),
            readChunkLevel(regionDir, chunkX, chunkZ + 1),
            readChunkLevel(regionDir, chunkX, chunkZ - 1)
        };

        // pass 1: bucket all sign and chest NBT compounds by x,y,z key
        Map<String, NBTTagCompound> signs = new LinkedHashMap<>();
        Map<String, NBTTagCompound> chests = new HashMap<>();

        for (NBTTagCompound level : allLevels)
        {
            if (level == null) continue;
            NBTTagList entList = level.l("TileEntities");
            if (entList == null) continue;

            for (int i = 0; i < entList.c(); i++)
            {
                NBTBase raw = entList.a(i);
                if (!(raw instanceof NBTTagCompound)) continue;
                NBTTagCompound te = (NBTTagCompound) raw;
                String id = te.getString("id");

                if (id.equals("Sign")  || id.equals("323")) signs.put(coordKey(te), te);
                if (id.equals("Chest") || id.equals("54"))  chests.put(coordKey(te), te);
            }
        }

        // pass 1.5: debug step to check if the watch positions have been found or not found
        for (Location loc : DEGUG_WATCH_POSITIONS)
        {
            int locChunkX = loc.getBlockX() >> 4;
            int locChunkZ = loc.getBlockZ() >> 4;
            if (!(locChunkX == chunkX && locChunkZ == chunkZ)) continue; // watch pos out of bounds for this chunk
            String key = coordKey(loc);
            boolean watchPosFound = signs.containsKey(key);
            System.err.println("[ShopDB] -- DEBUG -- " + key + " / WATCH POS FOUND? " + (watchPosFound ? "Yes" : "No"));
        }

        // pass 2: validate each sign and associate it with adjacent chests
        List<WrappedShop> shops = new ArrayList<>();

        for (NBTTagCompound sign : signs.values())
        {
            if (!uSign.isValid(new String[] {
                    sign.getString("Text1"),
                    sign.getString("Text2"),
                    sign.getString("Text3"),
                    sign.getString("Text4")
            })) continue;

            int sx = sign.e("x");
            int sy = sign.e("y");
            int sz = sign.e("z");

            NBTTagCompound primaryChest = findAdjacentChestNBT(chests, sx, sy, sz);
            if (primaryChest == null)
            {
                if (new File("shopdb.debug").exists())
                    System.err.println("[ShopDB] Found valid shop sign, but no adjacent chests @ " + coordKey(sign));
                continue;
            }

            List<NBTTagCompound> attachedChests = new ArrayList<>();
            attachedChests.add(primaryChest);

            int cx = primaryChest.e("x");
            int cy = primaryChest.e("y");
            int cz = primaryChest.e("z");
            String primaryKey = coordKey(cx, cy, cz);

            int[][] cardinalOffsets = {{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{0,1,0},{0,-1,0}};
            for (int[] d : cardinalOffsets)
            {
                String neighbourKey = coordKey(cx + d[0], cy + d[1], cz + d[2]);
                if (neighbourKey.equals(primaryKey)) continue;
                NBTTagCompound neighbour = chests.get(neighbourKey);
                if (neighbour != null)
                    attachedChests.add(neighbour);
            }

            shops.add(new WrappedShop(sign, world.getName(), attachedChests));
        }

        return shops;
    }

    private static NBTTagCompound readChunkLevel(File regionDir, int chunkX, int chunkZ)
    {
        // Open a fresh RegionFile each time - no cache
        File regionFile = new File(regionDir, "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mcr");
        if (!regionFile.exists()) return null;

        try
        {
            RegionFile rf = new RegionFile(regionFile);
            DataInputStream in = rf.a(chunkX & 31, chunkZ & 31); // getChunkDataInputStream
            if (in == null) return null;
            NBTTagCompound compound = CompressedStreamTools.a((DataInput) in);
            in.close();
            rf.b(); // close the RegionFile
            return compound.k("Level");
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // NBT coordinate helpers
    // -------------------------------------------------------------------------

    /** Returns a "x,y,z" key from a tile-entity NBTTagCompound. */
    private static String coordKey(NBTTagCompound te)
    {
        return te.e("x") + "," + te.e("y") + "," + te.e("z");
    }

    private static String coordKey(Location loc)
    {
        return coordKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Returns a "x,y,z" key from explicit coordinates. */
    private static String coordKey(int x, int y, int z)
    {
        return x + "," + y + "," + z;
    }

    /**
     * Replicates {@code uBlock.findChest}: check the block directly below the sign,
     * then all four cardinal neighbors + up/down
     */
    private static NBTTagCompound findAdjacentChestNBT(Map<String, NBTTagCompound> chestNbts, int sx, int sy, int sz)
    {
        // Block directly below the sign.
        NBTTagCompound below = chestNbts.get(coordKey(sx, sy - 1, sz));
        if (below != null) return below;

        // Four cardinal neighbors at sign Y.
        int[][] offsets = {{0,0,-1},{1,0,0},{0,0,1},{-1,0,0},{0,1,0},{0,-1,0}};
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