package net.oldschoolminecraft.shopdb;

public class SearchRegion
{
    public String regionName, worldName;
    public int startX, endX, startZ, endZ;

    public SearchRegion(String regionName, String worldName, int startX, int endX, int startZ, int endZ)
    {
        this.regionName = regionName;
        this.worldName = worldName;
        this.startX = startX;
        this.endX = endX;
        this.startZ = startZ;
        this.endZ = endZ;
    }
}
