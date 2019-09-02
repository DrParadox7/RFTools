package mcjty.rftools.blocks.spawner;

import mcjty.lib.container.GenericContainer;
import mcjty.lib.gui.GenericGuiContainer;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.widgets.BlockRender;
import mcjty.lib.gui.widgets.EnergyBar;
import mcjty.lib.gui.widgets.Label;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.tileentity.GenericEnergyStorage;
import mcjty.lib.typed.TypedMap;
import mcjty.rftools.RFTools;
import mcjty.rftools.items.SyringeItem;
import mcjty.rftools.network.RFToolsMessages;
import mcjty.rftools.setup.GuiProxy;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.CapabilityItemHandler;

import java.awt.*;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;


public class GuiSpawner extends GenericGuiContainer<SpawnerTileEntity, GenericContainer> {
    private static final int SPAWNER_WIDTH = 180;
    private static final int SPAWNER_HEIGHT = 152;

    private EnergyBar energyBar;
    private BlockRender blocks[] = new BlockRender[3];
    private Label labels[] = new Label[3];
    private Label name;
    private Label rfTick;

    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/spawner.png");

    public GuiSpawner(SpawnerTileEntity spawnerTileEntity, GenericContainer container, PlayerInventory inventory) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, spawnerTileEntity, container, inventory, GuiProxy.GUI_MANUAL_MAIN, "spawner");

        xSize = SPAWNER_WIDTH;
        ySize = SPAWNER_HEIGHT;
    }

    @Override
    public void init() {
        super.init();

        energyBar = new EnergyBar(minecraft, this).setVertical().setLayoutHint(10, 7, 8, 54).setShowText(false);

        blocks[0] = new BlockRender(minecraft, this).setLayoutHint(80, 5, 18, 18);
        blocks[1] = new BlockRender(minecraft, this).setLayoutHint(80, 25, 18, 18);
        blocks[2] = new BlockRender(minecraft, this).setLayoutHint(80, 45, 18, 18);
        labels[0] = new Label(minecraft, this).setHorizontalAlignment(HorizontalAlignment.ALIGN_LEFT); labels[0].setLayoutHint(100, 5, 74, 18);
        labels[1] = new Label(minecraft, this).setHorizontalAlignment(HorizontalAlignment.ALIGN_LEFT); labels[1].setLayoutHint(100, 25, 74, 18);
        labels[2] = new Label(minecraft, this).setHorizontalAlignment(HorizontalAlignment.ALIGN_LEFT); labels[2].setLayoutHint(100, 45, 74, 18);
        name = new Label(minecraft, this).setHorizontalAlignment(HorizontalAlignment.ALIGN_LEFT); name.setLayoutHint(22, 31, 78, 16);
        rfTick = new Label(minecraft, this).setHorizontalAlignment(HorizontalAlignment.ALIGN_LEFT); rfTick.setLayoutHint(22, 47, 78, 16);

        Panel toplevel = new Panel(minecraft, this).setBackground(iconLocation).setLayout(new PositionalLayout()).addChild(energyBar).
                addChildren(blocks[0], labels[0], blocks[1], labels[1], blocks[2], labels[2], rfTick, name);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        window = new Window(this, toplevel);
    }

    private static long lastTime = 0;

    private void showSyringeInfo() {
        for (int i = 0 ; i < 3 ; i++) {
            blocks[i].setRenderItem(null);
            labels[i].setText("");
        }
        name.setText("");
        rfTick.setText("");

        ItemStack stack = tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).map(h -> h.getStackInSlot(SpawnerTileEntity.SLOT_SYRINGE)).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return;
        }

        String mobId = SyringeItem.getMobId(stack);
        if (mobId != null) {
            String mobName = SyringeItem.getMobName(stack);
            name.setText(mobName);
            rfTick.setText(SpawnerConfiguration.mobSpawnRf.get(mobId) + "RF");
            int i = 0;
            List<SpawnerConfiguration.MobSpawnAmount> list = SpawnerConfiguration.mobSpawnAmounts.get(mobId);
            if (list != null) {
                if (System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    tileEntity.requestDataFromServer(RFTools.MODID, SpawnerTileEntity.CMD_GET_SPAWNERINFO, TypedMap.EMPTY);
//                    RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID, new SpawnerInfoPacketServer(
//                            tileEntity.getWorld().getDimension().getType().getId(),
//                            tileEntity.getPos())));
                }

                float[] matter = new float[] { SpawnerTileEntity.matterReceived0, SpawnerTileEntity.matterReceived1, SpawnerTileEntity.matterReceived2 };

                for (SpawnerConfiguration.MobSpawnAmount spawnAmount : list) {
                    ItemStack b = spawnAmount.getObject();
                    float amount = spawnAmount.getAmount();
                    if (b.isEmpty()) {
                        Object[] blocks = {Blocks.BIRCH_LEAVES, Blocks.PUMPKIN, Items.WHEAT, Items.POTATO, Items.BEEF}; // @todo 1.14 use tags or better way to find leaves
                        int index = (int) ((System.currentTimeMillis() / 500) % blocks.length);
                        if (blocks[index] instanceof Block) {
                            this.blocks[i].setRenderItem(new ItemStack((Block) blocks[index], 1));
                        } else {
                            this.blocks[i].setRenderItem(new ItemStack((Item) blocks[index], 1));
                        }
                    } else {
                        blocks[i].setRenderItem(b);
                    }
                    DecimalFormat format = new DecimalFormat("#.##");
                    format.setRoundingMode(RoundingMode.DOWN);
                    String mf = format.format(matter[i]);
                    labels[i].setText(mf + "/" + Float.toString(amount));
                    i++;
                }
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        showSyringeInfo();

        drawWindow();
        tileEntity.getCapability(CapabilityEnergy.ENERGY).ifPresent(e -> {
            energyBar.setMaxValue(((GenericEnergyStorage)e).getCapacity());
            energyBar.setValue(((GenericEnergyStorage)e).getEnergy());
        });
    }
}
