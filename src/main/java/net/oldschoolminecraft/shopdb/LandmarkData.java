package net.oldschoolminecraft.shopdb;

import java.util.ArrayList;

public class LandmarkData
{
    public String name;
    public String worldName;
    public double x, y, z;
    public float yaw, pitch;
    public ArrayList<String> visitors;

    public int getBlockX()
    {
        return (int) Math.floor(x);
    }

    public int getBlockY()
    {
        return (int) Math.floor(y);
    }

    public int getBlockZ()
    {
        return (int) Math.floor(z);
    }
}
