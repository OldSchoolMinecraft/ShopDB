package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Items.Items;
import com.Acrobot.ChestShop.Utils.uInventory;
import com.Acrobot.ChestShop.Utils.uLongName;
import com.Acrobot.ChestShop.Utils.uSign;
import com.LRFLEW.register.payment.forChestShop.methods.EE17;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.NBTTagList;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WrappedShop
{
    private List<Chest> chests;
    private Sign sign;
    private String owner;
    private float buyPrice, sellPrice;
    private Material stockedMaterial;
    private int unit;
    private int availableStock;
    private int durability;
    private String shopDataHash;

    // Used only by the NBT constructor - stores sign location for getSerializable().
    // Null when constructed from the live Bukkit path.
    private int nbtSignX, nbtSignY, nbtSignZ;
    private String nbtWorldName;

    // -------------------------------------------------------------------------
    // Live constructor (loaded chunks - Bukkit tile-entity path)
    // -------------------------------------------------------------------------

    public WrappedShop(Sign sign, Chest... chests)
    {
        this.chests = new ArrayList<>();
        this.chests.addAll(Arrays.asList(chests));
        this.sign = sign;
        this.owner = uLongName.getName(sign.getLine(0));
        this.buyPrice = uSign.buyPrice(sign.getLine(2));
        this.sellPrice = uSign.sellPrice(sign.getLine(2));
        ItemStack stock = Items.getItemStack(sign.getLine(3));
        this.stockedMaterial = stock.getType();
        this.durability = stock.getDurability();
        this.unit = uSign.itemAmount(sign.getLine(1));
        for (Chest chest : chests)
            this.availableStock += uInventory.amount(chest.getInventory(), stock, stock.getDurability());
        shopDataHash = generateHash();
    }

    // -------------------------------------------------------------------------
    // NBT constructor (unloaded chunks - region-file path)
    //
    // signNbt    : the raw NBTTagCompound for the Sign tile entity.
    //              Must contain Text1–Text4 for sign lines and x/y/z for position.
    // chestNbts  : one NBTTagCompound per chest (primary + double-chest partner).
    //              Each must contain an "Items" NBTTagList with slot compounds
    //              holding "id" (short), "Damage" (short), and "Count" (byte).
    // worldName  : the world name, used to produce a stable location hash string
    //              that is consistent with the live path's generateHash() output.
    // -------------------------------------------------------------------------

    public WrappedShop(NBTTagCompound signNbt, String worldName, List<NBTTagCompound> chestNbts)
    {
        this.chests = null; // not available from NBT path
        this.sign = null; // not available from NBT path

        // Sign lines - CB1060 stores them as Text1..Text4
        String line0 = signNbt.getString("Text1");
        String line1 = signNbt.getString("Text2");
        String line2 = signNbt.getString("Text3");
        String line3 = signNbt.getString("Text4");

        this.owner = uLongName.getName(line0);
        this.buyPrice = uSign.buyPrice(line2);
        this.sellPrice = uSign.sellPrice(line2);

        ItemStack stock = Items.getItemStack(line3);
        this.stockedMaterial = stock.getType();
        this.durability = stock.getDurability();
        this.unit = uSign.itemAmount(line1);

        // Sign coordinates - needed for hashing and getSerializable().
        this.nbtSignX = signNbt.e("x");
        this.nbtSignY = signNbt.e("y");
        this.nbtSignZ = signNbt.e("z");
        this.nbtWorldName = worldName;

        // Count matching items across all chest NBT compounds.
        this.availableStock = countStockInNBT(chestNbts, stock.getTypeId(), stock.getDurability());

        shopDataHash = generateHash();
    }

    // -------------------------------------------------------------------------
    // NBT inventory counting
    //
    // Replicates uInventory.amount(inventory, itemStack, durability) without
    // needing a live Bukkit inventory. Each slot compound in the "Items" list
    // has: "id" (short = item type ID), "Damage" (short = durability/data value),
    // "Count" (byte = stack size), "Slot" (byte, ignored here).
    // -------------------------------------------------------------------------

    private static int countStockInNBT(List<NBTTagCompound> chestNbts, int targetId, short targetDamage)
    {
        int total = 0;
        for (NBTTagCompound chestNbt : chestNbts)
        {
            NBTTagList items = chestNbt.l("Items"); // l() returns NBTTagList in CB1060
            if (items == null) continue;

            for (int i = 0; i < items.c(); i++) // c() = size()
            {
                NBTTagCompound slot = (NBTTagCompound) items.a(i); // a(i) = get(i)
                short id = slot.d("id");
                short damage = slot.d("Damage");
                byte count = slot.c("Count");

                if (id == targetId && damage == targetDamage)
                    total += count & 0xFF; // byte is signed; mask to treat as unsigned
            }
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Hash generation
    //
    // The live path uses sign.getBlock().getLocation().toString(), which in
    // CraftBukkit produces a string like:
    //   "CraftWorld{name=world}:12,64,-30"
    //
    // The NBT path cannot call sign.getBlock() (no live chunk), so we build the
    // same format from the world name and raw NBT coordinates. Both paths must
    // produce identical strings for the same physical sign so that
    // getUnduplicatedList() correctly deduplicates across both code paths if a
    // chunk happens to be loaded during one scan and unloaded during another.
    // -------------------------------------------------------------------------

    private String generateHash()
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String locationStr = buildLocationString();
            String data = owner + buyPrice + sellPrice + stockedMaterial.name() + unit + availableStock + "@" + locationStr;
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes)
                hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 Algorithm not found", e);
        }
    }

    /**
     * Builds the location string used in hash generation.
     * <p>
     * Live path: delegates to Bukkit's {@code Location.toString()}, which
     * produces {@code "CraftWorld{name=<world>}:<x>,<y>,<z>"}.
     * NBT path: replicates that format using raw coordinates from sign NBT,
     * so hashes remain comparable across both paths.
     */
    private String buildLocationString()
    {
        if (sign != null)
        {
            // Live path - use Bukkit's own toString() exactly as before.
            return sign.getBlock().getLocation().toString();
        } else {
            // NBT path - replicate CraftBukkit's Location.toString() format.
            return "CraftWorld{name=" + nbtWorldName + "}:" + nbtSignX + "," + nbtSignY + "," + nbtSignZ;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getHash()
    {
        return shopDataHash;
    }

    public ShopDataModel getSerializable()
    {
        Location signLoc;
        if (sign != null)
        {
            signLoc = sign.getBlock().getLocation();
        } else {
            // NBT path - construct a minimal location from stored coordinates.
            // World reference is null; callers using ShopLocation only need x/y/z.
            signLoc = new Location(null, nbtSignX, nbtSignY, nbtSignZ);
        }

        double playerBalance = new EE17.EEcoAccount(owner).balance();
        return new ShopDataModel(
                owner, playerBalance, availableStock,
                stockedMaterial.getId(), getDurability(),
                canBuy(), canSell(),
                buyPrice, sellPrice, unit, getHash(),
                new ShopDataModel.ShopLocation(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ())
        );
    }

    public int getDurability()
    {
        return durability;
    }

    public Material getStockedMaterial()
    {
        return stockedMaterial;
    }

    public int getUnit()
    {
        return unit;
    }

    public String getOwner()
    {
        return owner;
    }

    public boolean canBuy()
    {
        return buyPrice != -1.0f;
    }

    public boolean canSell()
    {
        return sellPrice != -1.0f;
    }

    public float getBuyPrice()
    {
        return buyPrice;
    }

    public float getSellPrice()
    {
        return sellPrice;
    }
}