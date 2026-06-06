package net.oldschoolminecraft.shopdb;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.material.MaterialData;

public class WrappedSign implements Sign
{
    private String[] lines;

    public WrappedSign(String[] lines)
    {
        if (lines.length > 4)
            throw new IllegalArgumentException("A sign can't have more than 4 lines!");
        this.lines = lines;
    }

    @Override
    public String[] getLines()
    {
        return lines;
    }

    @Override
    public String getLine(int i) throws IndexOutOfBoundsException
    {
        return lines[i];
    }

    @Override
    public void setLine(int i, String s) throws IndexOutOfBoundsException
    {
        lines[i] = s;
    }

    @Override
    public Block getBlock()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public MaterialData getData()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Material getType()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTypeId()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getLightLevel()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public World getWorld()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getX()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getY()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getZ()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Chunk getChunk()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setData(MaterialData materialData)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setType(Material material)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setTypeId(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean update()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean update(boolean b)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getRawData()
    {
        throw new UnsupportedOperationException();
    }
}
