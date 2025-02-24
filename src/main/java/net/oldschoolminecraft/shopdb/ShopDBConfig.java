package net.oldschoolminecraft.shopdb;

import org.bukkit.util.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopDBConfig extends Configuration
{
    public ShopDBConfig(File file)
    {
        super(file);
        reload();
    }

    public void reload()
    {
        load();
        write();
        save();
    }

    public void write()
    {
        generateConfigOption("dataExportFile", "shop_db.json");
        generateConfigOption("shopSearchRegions", Arrays.asList("mall1:world:-250,250/-250,250", "mall2:world:-420,420/-420,420"));
    }

    public ArrayList<SearchRegion> getSearchRegions()
    {
        List<String> regionsRaw = getStringList("shopSearchRegions", new ArrayList<>());
        ArrayList<SearchRegion> regions = new ArrayList<>();
        for (String str : regionsRaw)
        {
            int startX, endX, startZ, endZ;

            String[] mainParts = str.split(":");
            String regionName = mainParts[0];
            String worldName = mainParts[1];
            String[] dataParts = mainParts[2].split("/");
            String[] xRawParts = dataParts[0].split(",");
            String[] zRawParts = dataParts[1].split(",");
            startX = Integer.parseInt(xRawParts[0]);
            endX = Integer.parseInt(xRawParts[1]);
            startZ = Integer.parseInt(zRawParts[0]);
            endZ = Integer.parseInt(zRawParts[1]);
            regions.add(new SearchRegion(regionName, worldName, startX, endX, startZ, endZ));
        }
        return regions;
    }

    private void generateConfigOption(String key, Object defaultValue)
    {
        if (this.getProperty(key) == null) this.setProperty(key, defaultValue);
        final Object value = this.getProperty(key);
        this.removeProperty(key);
        this.setProperty(key, value);
    }

    public Object getConfigOption(String key)
    {
        return this.getProperty(key);
    }

    public Object getConfigOption(String key, Object defaultValue)
    {
        Object value = getConfigOption(key);
        if (value == null) value = defaultValue;
        return value;
    }
}
