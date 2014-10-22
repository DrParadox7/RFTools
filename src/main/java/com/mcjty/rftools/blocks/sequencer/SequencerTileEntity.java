package com.mcjty.rftools.blocks.sequencer;

import com.mcjty.entity.GenericTileEntity;
import com.mcjty.entity.SyncedValue;
import com.mcjty.rftools.blocks.BlockTools;
import com.mcjty.rftools.network.Argument;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

public class SequencerTileEntity extends GenericTileEntity {

    public static final int MODE_ONCE1 = 0;             // Cycle once as soon as a redstone signal is received. Ignores new signals until cycleBits is done
    public static final int MODE_ONCE2 = 1;             // Cycle once as soon as a redstone signal is received. Restarts cycleBits if a new redstone signal arrives
    public static final int MODE_LOOP1 = 2;             // Cycle all the time. Ignore redstone signals
    public static final int MODE_LOOP2 = 3;             // Cycle all the time. Restone signal sets cycle to the beginning
    public static final int MODE_LOOP3 = 4;             // Cycle for as long as a redstone signal is given. Stop as soon as the signal ends
    public static final int MODE_STEP = 5;              // Proceed one step in the cycleBits every time a redstone signal comes in

    public static final String CMD_MODE = "mode";
    public static final String CMD_SETBIT = "setBit";
    public static final String CMD_SETBITS = "setBits";
    public static final String CMD_SETDELAY = "setDelay";

    private int mode = MODE_ONCE1;
    private long cycleBits = 0;
    private int currentStep = -1;

    // For pulse detection.
    private boolean prevIn = false;

    private int delay = 1;
    private int timer = 0;
    private SyncedValue<Boolean> redstoneOut = new SyncedValue<Boolean>(false);

    public SequencerTileEntity() {
        registerSyncedObject(redstoneOut);
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        switch (mode) {
            case MODE_ONCE1:
            case MODE_ONCE2:
            case MODE_LOOP3:
                currentStep = -1;
                break;
            case MODE_LOOP1:
            case MODE_LOOP2:
            case MODE_STEP:
                currentStep = 0;
                break;
        }
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean getCycleBit(int bit) {
        return ((cycleBits >> bit) & 1) == 1;
    }

    public void setCycleBit(int bit, boolean flag) {
        if (flag) {
            cycleBits |= 1L << bit;
        } else {
            cycleBits &= ~(1L << bit);
        }
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }


    public void setCycleBits(int start, int stop, boolean flag) {
        for (int bit = start ; bit <= stop ; bit++) {
            if (flag) {
                cycleBits |= 1L << bit;
            } else {
                cycleBits &= ~(1L << bit);
            }
        }
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }


    @Override
    protected void checkStateServer() {
        super.checkStateServer();

        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        boolean newvalue = BlockTools.getRedstoneSignalIn(meta);
        boolean pulse = newvalue && !prevIn;
        prevIn = newvalue;

        if (pulse) {
            handlePulse();
        }

        timer--;
        if (timer <= 0) {
            timer = delay;
        } else {
            return;
        }

        handleCycle(newvalue);

        boolean newout;
        if (currentStep == -1) {
            newout = false;
        } else {
            newout = getCycleBit(currentStep);
        }

        if (newout != redstoneOut.getValue()) {
            redstoneOut.setValue(newout);
            notifyBlockUpdate();
        }
    }

    /**
     * Handle a cycle step.
     * @param redstone
     */
    private void handleCycle(boolean redstone) {
        switch (mode) {
            case MODE_ONCE1:
            case MODE_ONCE2:
                if (currentStep != -1) {
                    nextStepAndStop();
                }
                break;
            case MODE_LOOP1:
                nextStep();
                break;
            case MODE_LOOP2:
                nextStep();
                break;
            case MODE_LOOP3:
                if (redstone) {
                    nextStep();
                }
                break;
            case MODE_STEP:
                break;
        }
    }

    /**
     * Handle the arrival of a new redstone pulse.
     */
    private void handlePulse() {
        switch (mode) {
            case MODE_ONCE1:
                // If we're not doing a cycle then we start one now. Otherwise we do nothing.
                if (currentStep == -1) {
                    currentStep = 0;
                }
                break;
            case MODE_ONCE2:
                // If we're not doing a cycle then we start one now. Otherwise we restart the cycle..
                currentStep = 0;
                break;
            case MODE_LOOP1:
                // Ignore signals
                break;
            case MODE_LOOP2:
                // Set cycle to the start.
                currentStep = 0;
                break;
            case MODE_LOOP3:
                // Ignore pulses. We just work on redstone signal.
                break;
            case MODE_STEP:
                // Go to next step.
                nextStep();
                break;
        }
    }

    private void nextStep() {
        currentStep++;
        if (currentStep >= 64) {
            currentStep = 0;
        }
    }

    private void nextStepAndStop() {
        currentStep++;
        if (currentStep >= 64) {
            currentStep = -1;
        }
    }

    @Override
    protected int updateMetaData(int meta) {
        meta = super.updateMetaData(meta);
        Boolean value = redstoneOut.getValue();
        return BlockTools.setRedstoneSignalOut(meta, value == null ? false : value);
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        cycleBits = tagCompound.getLong("bits");
        redstoneOut.setValue(tagCompound.getBoolean("rs"));
        mode = tagCompound.getInteger("mode");
        currentStep = tagCompound.getInteger("step");
        prevIn = tagCompound.getBoolean("prevIn");
        timer = tagCompound.getInteger("timer");
        delay = tagCompound.getInteger("delay");
        if (delay == 0) {
            delay = 1;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setLong("bits", cycleBits);
        Boolean value = redstoneOut.getValue();
        tagCompound.setBoolean("rs", value == null ? false : value);
        tagCompound.setInteger("mode", mode);
        tagCompound.setInteger("step", currentStep);
        tagCompound.setBoolean("prevIn", prevIn);
        tagCompound.setInteger("timer", timer);
        tagCompound.setInteger("delay", delay);
    }

    @Override
    public boolean execute(String command, Map<String, Argument> args) {
        boolean rc = super.execute(command, args);
        if (rc) {
            return true;
        }
        if (CMD_MODE.equals(command)) {
            setMode(args.get("mode").getInteger());
            return true;
        } else if (CMD_SETBIT.equals(command)) {
            setCycleBit(args.get("bit").getInteger(), args.get("choice").getBoolean());
            return true;
        } else if (CMD_SETBITS.equals(command)) {
            setCycleBits(args.get("start").getInteger(), args.get("stop").getInteger(), args.get("choice").getBoolean());
            return true;
        } else if (CMD_SETDELAY.equals(command)) {
            setDelay(args.get("delay").getInteger());
            return true;
        }
        return false;
    }
}
