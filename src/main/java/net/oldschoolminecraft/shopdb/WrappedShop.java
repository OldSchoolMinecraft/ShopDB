package net.oldschoolminecraft.shopdb;

import com.Acrobot.ChestShop.Items.Items;
import com.Acrobot.ChestShop.Utils.uSign;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.inventory.ItemStack;

public class WrappedShop
{
    private Chest chest;
    private Sign sign;
    private String owner;
    private float buyPrice, sellPrice;
    private Material stockedMaterial;
    private int unit;

    public WrappedShop(Chest chest, Sign sign)
    {
        this.chest = chest;
        this.sign = sign;
        this.owner = sign.getLine(0);
        this.buyPrice = uSign.buyPrice(sign.getLine(2));
        this.sellPrice = uSign.sellPrice(sign.getLine(2));
        this.stockedMaterial = Items.getItemStack(sign.getLine(3)).getType();
        this.unit = uSign.itemAmount(sign.getLine(1));
    }

    public ShopDataModel getSerializable()
    {
        return new ShopDataModel(owner, stockedMaterial.getId(), canBuy(), canSell(), buyPrice, sellPrice, unit);
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
