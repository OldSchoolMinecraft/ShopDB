package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Items.Items;
import com.Acrobot.ChestShop.Utils.uInventory;
import com.Acrobot.ChestShop.Utils.uLongName;
import com.Acrobot.ChestShop.Utils.uSign;
import com.LRFLEW.register.payment.forChestShop.methods.EE17;
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

    public String getHash()
    {
        return shopDataHash;
    }

    private String generateHash()
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String data = owner + buyPrice + sellPrice + stockedMaterial.name() + unit + availableStock + "@" + sign.getBlock().getLocation().toString();
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes)
            {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 Algorithm not found", e);
        }
    }

    public ShopDataModel getSerializable()
    {
        Location signLoc = sign.getBlock().getLocation();
        double playerBalance = new EE17.EEcoAccount(owner).balance();
        return new ShopDataModel(owner, playerBalance, availableStock, stockedMaterial.getId(), getDurability(), canBuy(), canSell(), buyPrice, sellPrice, unit, getHash(), new ShopDataModel.ShopLocation(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ()));
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
