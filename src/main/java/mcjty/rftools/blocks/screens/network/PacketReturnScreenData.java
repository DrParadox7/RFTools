package mcjty.rftools.blocks.screens.network;

import io.netty.buffer.ByteBuf;
import mcjty.lib.network.NetworkTools;
import mcjty.lib.thirteen.Context;
import mcjty.lib.varia.GlobalCoordinate;
import mcjty.rftools.RFTools;
import mcjty.rftools.api.screens.data.IModuleData;
import mcjty.rftools.api.screens.data.IModuleDataFactory;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketReturnScreenData implements IMessage {
    private GlobalCoordinate pos;
    private Map<Integer, IModuleData> screenData;

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = new GlobalCoordinate(NetworkTools.readPos(buf), buf.readInt());
        int size = buf.readInt();
        screenData = new HashMap<>(size);
        for (int i = 0 ; i < size ; i++) {
            int key = buf.readInt();
            int shortId = buf.readInt();
            String id = RFTools.screenModuleRegistry.getNormalId(shortId);
            IModuleDataFactory<?> dataFactory = RFTools.screenModuleRegistry.getModuleDataFactory(id);
            IModuleData data = dataFactory.createData(buf);
            screenData.put(key, data);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NetworkTools.writePos(buf, pos.getCoordinate());
        buf.writeInt(pos.getDimension());

        buf.writeInt(screenData.size());
        for (Map.Entry<Integer, IModuleData> me : screenData.entrySet()) {
            buf.writeInt(me.getKey());
            IModuleData c = me.getValue();
            buf.writeInt(RFTools.screenModuleRegistry.getShortId(c.getId()));
            c.writeToBuf(buf);
        }
    }

    public GlobalCoordinate getPos() {
        return pos;
    }

    public Map<Integer, IModuleData> getScreenData() {
        return screenData;
    }

    public PacketReturnScreenData() {
    }

    public PacketReturnScreenData(ByteBuf buf) {
        fromBytes(buf);
    }

    public PacketReturnScreenData(GlobalCoordinate pos, Map<Integer, IModuleData> screenData) {
        this.pos = pos;
        this.screenData = screenData;
    }

    public void handle(Supplier<Context> supplier) {
        Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            PacketGetScreenDataHelper.setScreenData(this);
        });
        ctx.setPacketHandled(true);
    }
}