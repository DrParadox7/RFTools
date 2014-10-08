package com.mcjty.rftools.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;

public class PacketServerCommand extends AbstractServerCommand implements IMessageHandler<PacketServerCommand, IMessage> {

    public PacketServerCommand() {
    }

    public PacketServerCommand(int x, int y, int z, String command, Argument... arguments) {
        super(x, y, z, command, arguments);
    }

    @Override
    public IMessage onMessage(PacketServerCommand message, MessageContext ctx) {
        TileEntity te = ctx.getServerHandler().playerEntity.worldObj.getTileEntity(message.x, message.y, message.z);
        if(!(te instanceof CommandHandler)) {
            // @Todo better logging
            System.out.println("createStartScanPacket: TileEntity is not a CommandHandler!");
            return null;
        }
        CommandHandler commandHandler = (CommandHandler) te;
        if (!commandHandler.execute(message.command, message.args)) {
            System.out.println("Command "+message.command+" was not handled!");
        }
        return null;
    }

}
