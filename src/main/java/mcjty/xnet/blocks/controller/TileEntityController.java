package mcjty.xnet.blocks.controller;

import mcjty.lib.entity.GenericEnergyReceiverTileEntity;
import mcjty.lib.network.Argument;
import mcjty.typed.Type;
import mcjty.xnet.XNet;
import mcjty.xnet.api.channels.IChannelType;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.keys.ConsumerId;
import mcjty.xnet.api.keys.NetworkId;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.api.keys.SidedPos;
import mcjty.xnet.blocks.cables.ConnectorBlock;
import mcjty.xnet.logic.*;
import mcjty.xnet.multiblock.WorldBlob;
import mcjty.xnet.multiblock.XNetBlobData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static mcjty.xnet.logic.ChannelInfo.MAX_CHANNELS;

public final class TileEntityController extends GenericEnergyReceiverTileEntity implements ITickable, IControllerContext {

    public static final String CMD_GETCHANNELS = "getChannelInfo";
    public static final String CLIENTCMD_CHANNELSREADY = "channelsReady";
    public static final String CMD_GETCONNECTEDBLOCKS = "getConnectedBlocks";
    public static final String CLIENTCMD_CONNECTEDBLOCKSREADY = "connectedBlocksReady";

    public static final String CMD_CREATECONNECTOR = "createConnector";
    public static final String CMD_REMOVECONNECTOR = "removeConnector";
    public static final String CMD_UPDATECONNECTOR = "updateConnector";

    public static final String CMD_CREATECHANNEL = "createChannel";
    public static final String CMD_REMOVECHANNEL = "removeChannel";
    public static final String CMD_UPDATECHANNEL = "updateChannel";

    private NetworkId networkId;

    private final ChannelInfo[] channels = new ChannelInfo[MAX_CHANNELS];

    // Cached/transient data
    private Map<SidedConsumer, IConnectorSettings> cachedConnectors[] = new Map[MAX_CHANNELS];

    public TileEntityController() {
        super(100000, 1000); // @todo configurable
        for (int i = 0; i < MAX_CHANNELS; i++) {
            channels[i] = null;
        }
    }

    @Override
    public World getControllerWorld() {
        return getWorld();
    }

    @Override
    public NetworkId getNetworkId() {
        return networkId;
    }

    public void setNetworkId(NetworkId networkId) {
        this.networkId = networkId;
        markDirty();
    }

    @Override
    public void update() {
        if (!getWorld().isRemote) {
            for (int i = 0 ; i < MAX_CHANNELS ; i++) {
                if (channels[i] != null && channels[i].isEnabled()) {
                    channels[i].getChannelSettings().tick(i, this);
                }
            }
        }
    }

    private void cleanCache(int channel) {
        cachedConnectors[channel] = null;
        channels[channel].getChannelSettings().cleanCache();
    }

    @Override
    @Nonnull
    public Map<SidedConsumer, IConnectorSettings> getConnectors(int channel) {
        if (cachedConnectors[channel] == null) {
            cachedConnectors[channel] = new HashMap<>();
            for (Map.Entry<SidedConsumer, ConnectorInfo> entry : channels[channel].getConnectors().entrySet()) {
                cachedConnectors[channel].put(entry.getKey(), entry.getValue().getConnectorSettings());
            }
        }
        return cachedConnectors[channel];
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        return super.writeToNBT(tagCompound);
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        if (networkId != null) {
            tagCompound.setInteger("networkId", networkId.getId());
        }

        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (channels[i] != null) {
                NBTTagCompound tc = new NBTTagCompound();
                tc.setString("type", channels[i].getType().getID());
                channels[i].writeToNBT(tc);
                tagCompound.setTag("channel" + i, tc);
            }
        }
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        if (tagCompound.hasKey("networkId")) {
            networkId = new NetworkId(tagCompound.getInteger("networkId"));
        } else {
            networkId = null;
        }
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (tagCompound.hasKey("channel" + i)) {
                NBTTagCompound tc = (NBTTagCompound) tagCompound.getTag("channel" + i);
                String id = tc.getString("type");
                IChannelType type = XNet.xNetApi.findType(id);
                if (type == null) {
                    XNet.logger.warn("Unsupported type " + id + "!");
                    continue;
                }
                channels[i] = new ChannelInfo(type);
                channels[i].readFromNBT(tc);
            } else {
                channels[i] = null;
            }
        }
    }

    @Nullable
    @Override
    public BlockPos findConsumerPosition(@Nonnull ConsumerId consumerId) {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());
        return findConsumerPosition(worldBlob, consumerId);
    }

    @Nullable
    private BlockPos findConsumerPosition(@Nonnull WorldBlob worldBlob, @Nonnull ConsumerId consumerId) {
        Set<BlockPos> consumers = worldBlob.getConsumers(networkId);
        for (BlockPos pos : consumers) {
            ConsumerId c = worldBlob.getConsumerAt(pos);
            if (consumerId.equals(c)) {
                return pos;
            }
        }
        return null;
    }

    @Nonnull
    private List<ConnectedBlockClientInfo> findConnectedBlocks() {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());

        Set<ConnectedBlockClientInfo> set = new HashSet<>();
        for (BlockPos consumerPos : worldBlob.getConsumers(networkId)) {
            ConsumerId consumerId = worldBlob.getConsumerAt(consumerPos);
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos pos = consumerPos.offset(facing);
                if (ConnectorBlock.isConnectable(getWorld(), pos)) {
                    SidedPos sidedPos = new SidedPos(pos, facing.getOpposite());
                    IBlockState state = getWorld().getBlockState(pos);
                    ItemStack item = state.getBlock().getItem(getWorld(), pos, state);
                    ConnectedBlockClientInfo info = new ConnectedBlockClientInfo(sidedPos, item);
                    set.add(info);
                }
            }
        }
        List<ConnectedBlockClientInfo> list = new ArrayList<>(set);
        list.sort((i1, i2) -> {
            if (i1.getPos().getPos().equals(i2.getPos().getPos())) {
                return i1.getPos().getSide().compareTo(i2.getPos().getSide());
            } else {
                return i1.getPos().getPos().compareTo(i2.getPos().getPos());
            }
        });
        return list;
    }

    @Nonnull
    private List<ChannelClientInfo> findChannelInfo() {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());

        List<ChannelClientInfo> chanList = new ArrayList<>();
        for (ChannelInfo channel : channels) {
            if (channel != null) {
                ChannelClientInfo clientInfo = new ChannelClientInfo(channel.getType(),
                        channel.getChannelSettings(), channel.isEnabled());

                for (Map.Entry<SidedConsumer, ConnectorInfo> entry : channel.getConnectors().entrySet()) {
                    SidedConsumer sidedConsumer = entry.getKey();
                    ConnectorInfo info = entry.getValue();
                    if (info.getConnectorSettings() != null) {
                        BlockPos consumerPos = findConsumerPosition(worldBlob, sidedConsumer.getConsumerId());
                        if (consumerPos != null) {
                            SidedPos pos = new SidedPos(consumerPos.offset(sidedConsumer.getSide()), sidedConsumer.getSide().getOpposite());
                            ConnectorClientInfo ci = new ConnectorClientInfo(pos, sidedConsumer.getConsumerId(), channel.getType(), info.getConnectorSettings());
                            clientInfo.getConnectors().put(sidedConsumer, ci);
                        } else {
                            // Consumer was possibly removed. We might want to remove the entry from our list here?
                            // @todo
                        }
                    }
                }

                chanList.add(clientInfo);
            } else {
                chanList.add(null);
            }
        }
        return chanList;
    }

    private void updateChannel(int channel, Map<String, Argument> args) {
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<String, Argument> e : args.entrySet()) {
            data.put(e.getKey(), e.getValue().getValue());
        }
        channels[channel].getChannelSettings().update(data);

        Boolean enabled = (Boolean) data.get(GuiController.TAG_ENABLED);
        channels[channel].setEnabled(Boolean.TRUE.equals(enabled));

        cleanCache(channel);
        markDirty();
    }

    private void removeChannel(int channel) {
        channels[channel] = null;
        cachedConnectors[channel] = null;
        markDirty();
    }

    private void createChannel(int channel, String typeId) {
        IChannelType type = XNet.xNetApi.findType(typeId);
        channels[channel] = new ChannelInfo(type);
        cleanCache(channel);
        markDirty();
    }

    private void updateConnector(int channel, SidedPos pos, Map<String, Argument> args) {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());
        ConsumerId consumerId = worldBlob.getConsumerAt(pos.getPos().offset(pos.getSide()));
        for (Map.Entry<SidedConsumer, ConnectorInfo> entry : channels[channel].getConnectors().entrySet()) {
            SidedConsumer key = entry.getKey();
            if (key.getConsumerId().equals(consumerId) && key.getSide().getOpposite().equals(pos.getSide())) {
                Map<String, Object> data = new HashMap<>();
                for (Map.Entry<String, Argument> e : args.entrySet()) {
                    data.put(e.getKey(), e.getValue().getValue());
                }
                channels[channel].getConnectors().get(key).getConnectorSettings().update(data);
                cleanCache(channel);
                markDirty();
                return;
            }
        }
    }

    private void removeConnector(int channel, SidedPos pos) {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());
        ConsumerId consumerId = worldBlob.getConsumerAt(pos.getPos().offset(pos.getSide()));
        SidedConsumer toremove = null;
        for (Map.Entry<SidedConsumer, ConnectorInfo> entry : channels[channel].getConnectors().entrySet()) {
            SidedConsumer key = entry.getKey();
            if (key.getConsumerId().equals(consumerId)) {
                toremove = key;
                break;
            }
        }
        if (toremove != null) {
            channels[channel].getConnectors().remove(toremove);
            cleanCache(channel);
            markDirty();
        }
    }

    private void createConnector(int channel, SidedPos pos) {
        WorldBlob worldBlob = XNetBlobData.getBlobData(getWorld()).getWorldBlob(getWorld());
        ConsumerId consumerId = worldBlob.getConsumerAt(pos.getPos().offset(pos.getSide()));
        if (consumerId == null) {
            throw new RuntimeException("What?");
        }
        SidedConsumer id = new SidedConsumer(consumerId, pos.getSide().getOpposite());
        channels[channel].createConnector(id);
        cleanCache(channel);
        markDirty();
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, Map<String, Argument> args) {
        boolean rc = super.execute(playerMP, command, args);
        if (rc) {
            return true;
        }
        if (CMD_CREATECHANNEL.equals(command)) {
            int index = args.get("index").getInteger();
            String typeId = args.get("type").getString();
            createChannel(index, typeId);
            return true;
        } else if (CMD_CREATECONNECTOR.equals(command)) {
            int channel = args.get("channel").getInteger();
            SidedPos pos = new SidedPos(args.get("pos").getCoordinate(), EnumFacing.values()[args.get("side").getInteger()]);
            createConnector(channel, pos);
            return true;
        } else if (CMD_REMOVECHANNEL.equals(command)) {
            int index = args.get("index").getInteger();
            removeChannel(index);
            return true;
        } else if (CMD_REMOVECONNECTOR.equals(command)) {
            SidedPos pos = new SidedPos(args.get("pos").getCoordinate(), EnumFacing.values()[args.get("side").getInteger()]);
            int channel = args.get("channel").getInteger();
            removeConnector(channel, pos);
            return true;
        } else if (CMD_UPDATECONNECTOR.equals(command)) {
            SidedPos pos = new SidedPos(args.get("pos").getCoordinate(), EnumFacing.values()[args.get("side").getInteger()]);
            int channel = args.get("channel").getInteger();
            updateConnector(channel, pos, args);
            return true;
        } else if (CMD_UPDATECHANNEL.equals(command)) {
            int channel = args.get("channel").getInteger();
            updateChannel(channel, args);
            return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public <T> List<T> executeWithResultList(String command, Map<String, Argument> args, Type<T> type) {
        List<T> rc = super.executeWithResultList(command, args, type);
        if (!rc.isEmpty()) {
            return rc;
        }
        if (CMD_GETCHANNELS.equals(command)) {
            return type.convert(findChannelInfo());
        } else if (CMD_GETCONNECTEDBLOCKS.equals(command)) {
            return type.convert(findConnectedBlocks());
        }
        return Collections.emptyList();
    }

    @Override
    public <T> boolean execute(String command, List<T> list, Type<T> type) {
        boolean rc = super.execute(command, list, type);
        if (rc) {
            return true;
        }
        if (CLIENTCMD_CHANNELSREADY.equals(command)) {
            GuiController.fromServer_channels = new ArrayList<>(Type.create(ChannelClientInfo.class).convert(list));
            return true;
        } else if (CLIENTCMD_CONNECTEDBLOCKSREADY.equals(command)) {
            GuiController.fromServer_connectedBlocks = new ArrayList<>(Type.create(ConnectedBlockClientInfo.class).convert(list));
            return true;
        }
        return false;
    }
}
