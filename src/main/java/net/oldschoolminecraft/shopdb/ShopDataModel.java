package net.oldschoolminecraft.shopdb;

public class ShopDataModel
{
    public String owner;
    public int materialID;
    public int availableStock;
    public boolean canBuy, canSell;
    public float buyPrice, sellPrice;
    public int unit;
    private ShopLocation location;

    public ShopDataModel(String owner, int availableStock, int materialID, boolean canBuy, boolean canSell, float buyPrice, float sellPrice, int unit, ShopLocation location)
    {
        this.owner = owner;
        this.availableStock = availableStock;
        this.materialID = materialID;
        this.canBuy = canBuy;
        this.canSell = canSell;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.unit = unit;
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
