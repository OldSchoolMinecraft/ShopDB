package net.oldschoolminecraft.shopdb;

public class ShopDataModel
{
    public String owner;
    public int materialID;
    public boolean canBuy, canSell;
    public float buyPrice, sellPrice;
    public int unit;

    public ShopDataModel(String owner, int materialID, boolean canBuy, boolean canSell, float buyPrice, float sellPrice, int unit)
    {
        this.owner = owner;
        this.materialID = materialID;
        this.canBuy = canBuy;
        this.canSell = canSell;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.unit = unit;
    }
}
