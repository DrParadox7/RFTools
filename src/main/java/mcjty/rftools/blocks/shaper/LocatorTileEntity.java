package mcjty.rftools.blocks.shaper;

import mcjty.lib.tileentity.GenericEnergyStorage;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.typed.Key;
import mcjty.lib.typed.Type;
import mcjty.lib.typed.TypedMap;
import mcjty.lib.varia.Counter;
import mcjty.lib.varia.EnergyTools;
import mcjty.rftools.shapes.BeaconType;
import mcjty.rftools.shapes.ScanDataManager;
import mcjty.rftools.shapes.ScanExtraData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;

import java.util.List;

import static mcjty.rftools.blocks.builder.BuilderSetup.TYPE_LOCATOR;

public class LocatorTileEntity extends GenericTileEntity implements ITickableTileEntity {

    public static final String CMD_SETTINGS = "locator.setSettings";

    public static final Key<String> PARAM_HOSTILE_TYPE = new Key<>("hostile", Type.STRING);
    public static final Key<String> PARAM_PASSIVE_TYPE = new Key<>("passive", Type.STRING);
    public static final Key<String> PARAM_PLAYER_TYPE = new Key<>("player", Type.STRING);
    public static final Key<String> PARAM_ENERGY_TYPE = new Key<>("energy", Type.STRING);
    public static final Key<Boolean> PARAM_HOSTILE_BEACON = new Key<>("hostileBeacon", Type.BOOLEAN);
    public static final Key<Boolean> PARAM_PASSIVE_BEACON = new Key<>("passiveBeacon", Type.BOOLEAN);
    public static final Key<Boolean> PARAM_PLAYER_BEACON = new Key<>("playerBeacon", Type.BOOLEAN);
    public static final Key<Boolean> PARAM_ENERGY_BEACON = new Key<>("energyBeacon", Type.BOOLEAN);
    public static final Key<String> PARAM_FILTER = new Key<>("filter", Type.STRING);
    public static final Key<Integer> PARAM_MIN_ENERGY = new Key<>("minEnergy", Type.INTEGER);
    public static final Key<Integer> PARAM_MAX_ENERGY = new Key<>("maxEnergy", Type.INTEGER);

    private int counter = 0;

    private BeaconType hostileType = BeaconType.BEACON_OFF;
    private boolean hostileBeacon = false;
    private BeaconType passiveType = BeaconType.BEACON_YELLOW;
    private boolean passiveBeacon = false;
    private BeaconType playerType = BeaconType.BEACON_OFF;
    private boolean playerBeacon = false;
    private BeaconType energyType = BeaconType.BEACON_OFF;
    private boolean energyBeacon = false;

    private String filter = "";
    private Integer minEnergy = null;
    private Integer maxEnergy = null;

    private LazyOptional<GenericEnergyStorage> energyHandler = LazyOptional.of(() -> new GenericEnergyStorage(this, true, ScannerConfiguration.LOCATOR_MAXENERGY.get(), ScannerConfiguration.LOCATOR_RECEIVEPERTICK.get()));

    public LocatorTileEntity() {
        super(TYPE_LOCATOR);
    }

    @Override
    public void tick() {
        if (!world.isRemote && isMachineEnabled()) {
            counter--;
            markDirtyQuick();
            if (counter <= 0) {
                counter = ScannerConfiguration.ticksPerLocatorScan.get();

                ScannerTileEntity scanner = getScanner();
                if (scanner == null || scanner.getDataDim() == null) {
                    // No scanner, do nothing
                    return;
                }

                energyHandler.ifPresent(h -> {
                    int energy = getEnergyPerScan(scanner);
                    if (h.getEnergyStored() < energy) {
                        // Do nothing
                        return;
                    }

                    BlockPos dim = scanner.getDataDim();
                    BlockPos start = scanner.getFirstCorner();
                    if (start == null) {
                        // No valid destination. Don't do anything
                        return;
                    }

                    World scanWorld = scanner.getScanWorld(scanner.getScanDimension());

                    h.consumeEnergy(energy);
                    AxisAlignedBB bb = new AxisAlignedBB(start, start.add(dim));

                    List<Entity> entities = scanWorld.getEntitiesWithinAABB(LivingEntity.class, bb);
                    int scanId = scanner.getScanId();
                    ScanExtraData extraData = ScanDataManager.get().getExtraData(scanId);
                    extraData.touch();
                    extraData.clear();

                    BlockPos center = scanner.getScanCenter();
                    findEntityBeacons(entities, extraData, center);
                    findEnergyBeacons(scanner, extraData, dim, start, center, scanWorld);
                });
            }
        }
    }

    private void findEnergyBeacons(ScannerTileEntity scanner, ScanExtraData extraData, BlockPos dim, BlockPos start, BlockPos center, World scanWorld) {
        if (energyType != BeaconType.BEACON_OFF) {
            int dx = (dim.getX() + 15) / 16;
            int dz = (dim.getZ() + 15) / 16;

            int chunks = dx * dz;
            if (chunks <= ScannerConfiguration.locatorMaxEnergyChunks.get()) {
                BlockPos end = scanner.getLastCorner();
                int minChunkX = start.getX() >> 4;
                int minChunkZ = start.getZ() >> 4;
                int maxChunkX = end.getX() >> 4;
                int maxChunkZ = end.getZ() >> 4;
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int cx = minChunkX ; cx <= maxChunkX ; cx++) {
                    for (int cz = minChunkZ ; cz <= maxChunkZ ; cz++) {
                        for (int x = 0 ; x < 16 ; x++) {
                            for (int z = 0 ; z < 16 ; z++) {
                                int rx = (cx << 4) + x;
                                int rz = (cz << 4) + z;
                                if (rx >= start.getX() && rx < end.getX() && rz >= start.getZ() && rz < end.getZ()) {
                                    for (int ry = start.getY() ; ry < end.getY() ; ry++) {
                                        mpos.setPos(rx, ry, rz);
                                        TileEntity tileEntity = scanWorld.getTileEntity(mpos);
                                        if (EnergyTools.isEnergyTE(tileEntity, null)) {
                                            BlockPos pos = mpos.subtract(center);
                                            EnergyTools.EnergyLevel el = EnergyTools.getEnergyLevelMulti(tileEntity, null);
                                            long max = el.getMaxEnergy();
                                            long e = el.getEnergy();
                                            int pct = max > 0 ? (int) (e * 100 / max) : 0;
                                            if (minEnergy != null && pct < minEnergy) {
                                                extraData.addBeacon(pos, energyType, energyBeacon);
                                            } else if (maxEnergy != null && pct > maxEnergy) {
                                                extraData.addBeacon(pos, energyType, energyBeacon);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void findEntityBeacons(List<Entity> entities, ScanExtraData extraData, BlockPos center) {
        Counter<BlockPos> counter = new Counter<>();
        boolean dofilter = filter != null && !filter.trim().isEmpty();
        String filt = "";
        if (dofilter) {
            filt = filter.toLowerCase();
        }

        for (Entity entity : entities) {
            BlockPos pos = entity.getPosition().subtract(center);
            if (counter.getOrDefault(pos, 0) < ScannerConfiguration.locatorEntitySafety.get()) {
                if (dofilter) {
                    if (!checkFilter(filt, entity)) {
                        continue;
                    }
                }
                if (entity instanceof AnimalEntity) {
                    if (passiveType != BeaconType.BEACON_OFF) {
                        extraData.addBeacon(pos, passiveType, passiveBeacon);
                        counter.increment(pos);
                    }
                } else if (entity instanceof PlayerEntity) {
                    if (playerType != BeaconType.BEACON_OFF) {
                        extraData.addBeacon(pos, playerType, playerBeacon);
                        counter.increment(pos);
                    }
                } else {
                    if (hostileType != BeaconType.BEACON_OFF) {
                        extraData.addBeacon(pos, hostileType, hostileBeacon);
                        counter.increment(pos);
                    }
                }
            }
        }
    }

    private boolean checkFilter(String filt, Entity entity) {
        String name = entity.getName().getFormattedText().toLowerCase();    // @todo 1.14
        if (name.contains(filt)) {
            return true;
        }

        // Check if the entity has a name tag
        if (entity.hasCustomName()) {
            String nameTag = entity.getCustomName().getFormattedText().toLowerCase();   // @todo 1.14
            if (nameTag.contains(filt)) {
                return true;
            }
        }

        return false;
    }

    public int getEnergyPerScan() {
        ScannerTileEntity scanner = getScanner();
        if (scanner == null) {
            return Integer.MAX_VALUE;
        }
        return getEnergyPerScan(scanner);
    }

    public int getEnergyPerScan(ScannerTileEntity scanner) {
        BlockPos dim = scanner.getDataDim();
        if (dim == null) {
            return Integer.MAX_VALUE;
        }
        int dx = (dim.getX() + 15) / 16;
        int dy = (dim.getY() + 15) / 16;
        int dz = (dim.getZ() + 15) / 16;
        int subchunks = dx * dy * dz;
        int chunks = dx * dz;

        int energy = ScannerConfiguration.LOCATOR_PERSCAN_BASE.get();
        energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_PERSCAN_CHUNK.get());
        if (hostileType != BeaconType.BEACON_OFF) {
            energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_PERSCAN_HOSTILE.get());
        }
        if (passiveType != BeaconType.BEACON_OFF) {
            energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_PERSCAN_PASSIVE.get());
        }
        if (playerType != BeaconType.BEACON_OFF) {
            energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_PERSCAN_PLAYER.get());
        }
        if (energyType != BeaconType.BEACON_OFF && chunks <= ScannerConfiguration.locatorMaxEnergyChunks.get()) {
            energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_PERSCAN_ENERGY.get());
        }
        if (filter == null || !filter.trim().isEmpty()) {
            energy += (int) (energy + subchunks * ScannerConfiguration.LOCATOR_FILTER_COST.get());
        }
        return energy;
    }

    private ScannerTileEntity getScanner() {
        TileEntity te = world.getTileEntity(getPos().down());
        if (te instanceof ScannerTileEntity) {
            return (ScannerTileEntity) te;
        }
        return null;
    }

    @Override
    protected boolean needsRedstoneMode() {
        return true;
    }

    public String getFilter() {
        return filter;
    }

    public Integer getMinEnergy() {
        return minEnergy;
    }

    public Integer getMaxEnergy() {
        return maxEnergy;
    }

    public BeaconType getHostileType() {
        return hostileType;
    }

    public boolean isHostileBeacon() {
        return hostileBeacon;
    }

    public BeaconType getPassiveType() {
        return passiveType;
    }

    public boolean isPassiveBeacon() {
        return passiveBeacon;
    }

    public BeaconType getPlayerType() {
        return playerType;
    }

    public boolean isPlayerBeacon() {
        return playerBeacon;
    }

    public BeaconType getEnergyType() {
        return energyType;
    }

    public boolean isEnergyBeacon() {
        return energyBeacon;
    }

    // @todo 1.14 loot tables
    @Override
    public void read(CompoundNBT tagCompound) {
        super.read(tagCompound);
        counter = tagCompound.getInt("counter");
        hostileType = BeaconType.getTypeByCode(tagCompound.getString("hostile"));
        passiveType = BeaconType.getTypeByCode(tagCompound.getString("passive"));
        playerType = BeaconType.getTypeByCode(tagCompound.getString("player"));
        energyType = BeaconType.getTypeByCode(tagCompound.getString("energylow"));
        hostileBeacon = tagCompound.getBoolean("hostileBeacon");
        passiveBeacon = tagCompound.getBoolean("passiveBeacon");
        playerBeacon = tagCompound.getBoolean("playerBeacon");
        energyBeacon = tagCompound.getBoolean("energyBeacon");
        filter = tagCompound.getString("filter");
        if (tagCompound.contains("minEnergy")) {
            minEnergy = tagCompound.getInt("minEnergy");
        } else {
            minEnergy = null;
        }
        if (tagCompound.contains("maxEnergy")) {
            maxEnergy = tagCompound.getInt("maxEnergy");
        } else {
            maxEnergy = null;
        }
    }

    // @todo 1.14 loot tables
    @Override
    public CompoundNBT write(CompoundNBT tagCompound) {
        super.write(tagCompound);
        tagCompound.putInt("counter", counter);
        tagCompound.putString("hostile", hostileType.getCode());
        tagCompound.putString("passive", passiveType.getCode());
        tagCompound.putString("player", playerType.getCode());
        tagCompound.putString("energylow", energyType.getCode());
        tagCompound.putBoolean("hostileBeacon", hostileBeacon);
        tagCompound.putBoolean("passiveBeacon", passiveBeacon);
        tagCompound.putBoolean("playerBeacon", playerBeacon);
        tagCompound.putBoolean("energyBeacon", energyBeacon);
        tagCompound.putString("filter", filter);
        if (minEnergy != null) {
            tagCompound.putInt("minEnergy", minEnergy);
        }
        if (maxEnergy != null) {
            tagCompound.putInt("maxEnergy", maxEnergy);
        }
        return tagCompound;
    }


    @Override
    public boolean execute(PlayerEntity playerMP, String command, TypedMap params) {
        boolean rc = super.execute(playerMP, command, params);
        if (rc) {
            return true;
        }
        if (CMD_SETTINGS.equals(command)) {
            hostileType = BeaconType.getTypeByCode(params.get(PARAM_HOSTILE_TYPE));
            passiveType = BeaconType.getTypeByCode(params.get(PARAM_PASSIVE_TYPE));
            playerType = BeaconType.getTypeByCode(params.get(PARAM_PLAYER_TYPE));
            energyType = BeaconType.getTypeByCode(params.get(PARAM_ENERGY_TYPE));
            hostileBeacon = params.get(PARAM_HOSTILE_BEACON);
            passiveBeacon = params.get(PARAM_PASSIVE_BEACON);
            playerBeacon = params.get(PARAM_PLAYER_BEACON);
            energyBeacon = params.get(PARAM_ENERGY_BEACON);
            filter = params.get(PARAM_FILTER);
            if (params.get(PARAM_MIN_ENERGY) != null) {
                minEnergy = params.get(PARAM_MIN_ENERGY);
            } else {
                minEnergy = null;
            }
            if (params.get(PARAM_MAX_ENERGY) != null) {
                maxEnergy = params.get(PARAM_MAX_ENERGY);
            } else {
                maxEnergy = null;
            }
            markDirtyClient();
            return true;
        }
        return false;
    }


//    @Override
//    @Optional.Method(modid = "theoneprobe")
//    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, PlayerEntity player, World world, BlockState blockState, IProbeHitData data) {
//        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
//        if (world.getBlockState(data.getPos().down()).getBlock() != BuilderSetup.scannerBlock) {
//            probeInfo.text(TextStyleClass.ERROR + "Error! Needs a scanner below!");
//        } else {
//            probeInfo.text(TextStyleClass.INFO + "Scanner detected");
//        }
//    }
//
//    @SideOnly(Side.CLIENT)
//    @Override
//    @Optional.Method(modid = "waila")
//    public void addWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
//        super.addWailaBody(itemStack, currenttip, accessor, config);
//        if (accessor.world.getBlockState(accessor.getPosition().down()).getBlock() != BuilderSetup.scannerBlock) {
//            currenttip.add(TextFormatting.RED.toString() + TextFormatting.BOLD + "Error! Needs a scanner below!");
//        } else {
//            currenttip.add(TextFormatting.WHITE + "Scanner detected");
//        }
//    }
}
