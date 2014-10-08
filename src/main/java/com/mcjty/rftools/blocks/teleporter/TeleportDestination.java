package com.mcjty.rftools.blocks.teleporter;

import com.mcjty.rftools.network.ByteBufConverter;
import com.mcjty.varia.Coordinate;
import io.netty.buffer.ByteBuf;

import java.util.HashSet;
import java.util.Set;

public class TeleportDestination implements ByteBufConverter {
    private final Coordinate coordinate;
    private final int dimension;
    private String name = "";
    private final Set<String> allowedPlayers;

    public TeleportDestination(ByteBuf buf) {
        coordinate = new Coordinate(buf.readInt(), buf.readInt(), buf.readInt());
        dimension = buf.readInt();
        byte[] dst = new byte[buf.readInt()];
        buf.readBytes(dst);
        setName(new String(dst));
        this.allowedPlayers = new HashSet<String>();
    }

    public TeleportDestination(Coordinate coordinate, int dimension, Set<String> allowedPlayers) {
        this.coordinate = coordinate;
        this.dimension = dimension;
        this.allowedPlayers = new HashSet<String>(allowedPlayers);
    }

    public TeleportDestination(Coordinate coordinate, int dimension) {
        this.coordinate = coordinate;
        this.dimension = dimension;
        this.allowedPlayers = new HashSet<String>();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(coordinate.getX());
        buf.writeInt(coordinate.getY());
        buf.writeInt(coordinate.getZ());
        buf.writeInt(dimension);
        buf.writeInt(getName().length());
        buf.writeBytes(getName().getBytes());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null) {
            this.name = "";
        } else {
            this.name = name;
        }
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public int getDimension() {
        return dimension;
    }

    public Set<String> getAllowedPlayers() {
        return allowedPlayers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeleportDestination that = (TeleportDestination) o;

        if (dimension != that.dimension) return false;
        if (allowedPlayers != null ? !allowedPlayers.equals(that.allowedPlayers) : that.allowedPlayers != null)
            return false;
        if (!coordinate.equals(that.coordinate)) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = coordinate.hashCode();
        result = 31 * result + dimension;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (allowedPlayers != null ? allowedPlayers.hashCode() : 0);
        return result;
    }
}
