package mcjty.xnet.blocks.generic;

import mcjty.lib.entity.GenericTileEntity;
import net.minecraft.nbt.NBTTagCompound;

public class GenericCableTileEntity extends GenericTileEntity {

    private int id = -1;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        if (id == this.id) {
            return;
        }
        this.id = id;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        id = tagCompound.getInteger("networkId");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setInteger("networkId", id);
        return tagCompound;
    }


}
