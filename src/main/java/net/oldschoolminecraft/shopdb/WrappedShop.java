package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Items.Items;
import com.Acrobot.ChestShop.Utils.uInventory;
import com.Acrobot.ChestShop.Utils.uSign;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WrappedShop
{
    private Chest chest;
    private Sign sign;
    private String owner;
    private float buyPrice, sellPrice;
    private Material stockedMaterial;
    private int unit;
    private int availableStock;

    public WrappedShop(Chest chest, Sign sign)
    {
        this.chest = chest;
        this.sign = sign;
        this.owner = sign.getLine(0);
        this.buyPrice = uSign.buyPrice(sign.getLine(2));
        this.sellPrice = uSign.sellPrice(sign.getLine(2));
        ItemStack stock = Items.getItemStack(sign.getLine(3));
        this.stockedMaterial = stock.getType();
        this.unit = uSign.itemAmount(sign.getLine(1));
        this.availableStock = uInventory.amount(chest.getInventory(), stock, stock.getDurability());
    }

    public String generateHash()
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String data = owner + buyPrice + sellPrice + stockedMaterial.name() + unit + availableStock;
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
        return new ShopDataModel(owner, availableStock, stockedMaterial.getId(), canBuy(), canSell(), buyPrice, sellPrice, unit, new ShopDataModel.ShopLocation(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ()));
    }

    public Material getStockedMaterial()
    {
        return stockedMaterial;
    }

    public int getUnit()
    {
        return unit;
    }

    public int getTotalStock()
    {
        int totalCount = 0;
        for (ItemStack stack : chest.getInventory().getContents())
            if (stack.getType() == stockedMaterial) totalCount += stack.getAmount();
        return totalCount;
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
