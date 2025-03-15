package net.oldschoolminecraft.shopdb;

public class ShopDataModel
{
    public String owner;
    public double ownerBalance;
    public int materialID;
    public int durability;
    public int availableStock;
    public boolean canBuy, canSell;
    public float buyPrice, sellPrice;
    public int unit;
    public String shopDataHash;
    public ShopLocation location;

    public ShopDataModel(String owner, double ownerBalance, int availableStock, int materialID, int durability, boolean canBuy, boolean canSell, float buyPrice, float sellPrice, int unit, String shopDataHash, ShopLocation location)
    {
        this.owner = owner;
        this.ownerBalance = ownerBalance;
        this.availableStock = availableStock;
        this.materialID = materialID;
        this.durability = durability;
        this.canBuy = canBuy;
        this.canSell = canSell;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.unit = unit;
        this.shopDataHash = shopDataHash;
        this.location = location;
    }

    public static class ShopLocation
    {
        public int x, y, z;

        public ShopLocation(int x, int y, int z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
