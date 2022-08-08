package com.jelly.MightyMiner.baritone.automine;

import com.jelly.MightyMiner.MightyMiner;
import com.jelly.MightyMiner.baritone.automine.config.MineBehaviour;
import com.jelly.MightyMiner.baritone.automine.pathing.config.PathBehaviour;
import com.jelly.MightyMiner.baritone.logging.Logger;
import com.jelly.MightyMiner.baritone.automine.pathing.AStarPathFinder;
import com.jelly.MightyMiner.baritone.structures.BlockNode;
import com.jelly.MightyMiner.baritone.structures.BlockType;
import com.jelly.MightyMiner.handlers.KeybindHandler;
import com.jelly.MightyMiner.player.Rotation;
import com.jelly.MightyMiner.render.BlockRenderer;
import com.jelly.MightyMiner.utils.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.awt.*;
import java.util.LinkedList;

public class AutoMineBaritone{

    Minecraft mc = Minecraft.getMinecraft();
    MineBehaviour mineBehaviour;

    LinkedList<BlockNode> blocksToMine = new LinkedList<>();
    LinkedList<BlockNode> minedBlocks = new LinkedList<>();

    boolean inAction = false;
    Rotation rotation = new Rotation();

    int deltaJumpTick = 0;

    enum PlayerState {
        WALKING,
        MINING,
        NONE
    }
    PlayerState currentState;
    boolean enabled;

    AStarPathFinder pathFinder;

    public AutoMineBaritone(PathBehaviour pathBehaviour, MineBehaviour mineBehaviour){
        pathFinder = new AStarPathFinder(pathBehaviour);
        this.mineBehaviour = mineBehaviour;
    }


    public void clearBlocksToWalk(){
        blocksToMine.clear();
        BlockRenderer.renderMap.clear();
        minedBlocks.clear();
    }


    public void enableBaritone(Block... blockType){
        enabled = true;
        mc.thePlayer.addChatMessage(new ChatComponentText("Starting automine"));

        clearBlocksToWalk();
        KeybindHandler.resetKeybindState();

        new Thread(() -> {
            try{
                blocksToMine = pathFinder.getPath(blockType);
            } catch (Throwable e){
                Logger.playerLog("Error when getting path!");
                e.printStackTrace();
            }
            Logger.playerLog("Checking if path is valid");

            if (!blocksToMine.isEmpty()) {
                for (BlockNode blockNode : blocksToMine) {
                    BlockRenderer.renderMap.put(blockNode.getBlockPos(), Color.ORANGE);
                }
                BlockRenderer.renderMap.put(blocksToMine.getFirst().getBlockPos(), Color.RED);
            } else {
                Logger.playerLog("blocks to mine EMPTY!");
            }
            Logger.playerLog("Starting to mine");

            inAction = true;
            currentState = PlayerState.NONE;
            stuckTickCount = 0;
        }).start();
    }


    public void disableBaritone() {
        pauseMacro();
        enabled = false;
    }
    private void pauseMacro() {
        inAction = false;
        currentState = PlayerState.NONE;
        KeybindHandler.resetKeybindState();
        clearBlocksToWalk();
    }
    public boolean isEnabled(){
        return enabled;
    }


    public void onOverlayRenderEvent(RenderGameOverlayEvent event){
        if(event.type == RenderGameOverlayEvent.ElementType.TEXT){
            if(blocksToMine != null){
                if(!blocksToMine.isEmpty()){
                    for(int i = 0; i < blocksToMine.size(); i++){
                        mc.fontRendererObj.drawString(blocksToMine.get(i).getBlockPos().toString() + " " + blocksToMine.get(i).getBlockType().toString() , 5, 5 + 10 * i, -1);
                    }
                }
            }
            if(currentState != null)
                mc.fontRendererObj.drawString(currentState.toString(), 300, 5, -1);
        }
    }


    int stuckTickCount = 0;
    public void onTickEvent(TickEvent.Phase phase){

        if(phase != TickEvent.Phase.START || !inAction || blocksToMine.isEmpty())
            return;

        if ( (blocksToMine.getLast().getBlockType() == BlockType.MINE && BlockUtils.isPassable(blocksToMine.getLast().getBlockPos())) ||
                (blocksToMine.getLast().getBlockType() == BlockType.WALK &&
                (BlockUtils.onTheSameXZ(blocksToMine.getLast().getBlockPos(), BlockUtils.getPlayerLoc()) || !BlockUtils.fitsPlayer(blocksToMine.getLast().getBlockPos().down())) )
        ){
            stuckTickCount = 0;
            minedBlocks.add(blocksToMine.getLast());
            BlockRenderer.renderMap.remove(blocksToMine.getLast().getBlockPos());
            blocksToMine.removeLast();
        } else {
            //stuck handling
            stuckTickCount++;
            if(stuckTickCount > 20 * MightyMiner.config.gemRestartTimeThreshold){
                new Thread(restartBaritone).start();
                return;
            }
        }

        if(blocksToMine.isEmpty() || BlockUtils.isPassable(blocksToMine.getFirst().getBlockPos())){
            mc.thePlayer.addChatMessage(new ChatComponentText("Finished baritone"));
            disableBaritone();
            return;
        }

        updateState();

        BlockPos lastMinedBlockPos = minedBlocks.isEmpty() ? null : minedBlocks.getLast().getBlockPos();
        BlockPos targetMineBlock = blocksToMine.getLast().getBlockPos();

        switch (currentState){
            case WALKING:
                KeybindHandler.updateKeys(
                        (lastMinedBlockPos != null || BlockUtils.isPassable(targetMineBlock)),
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        deltaJumpTick > 0);

                if(BlockUtils.isPassable(targetMineBlock))
                    rotation.intLockAngle(AngleUtils.getRequiredYaw(targetMineBlock), mc.thePlayer.rotationPitch, mineBehaviour.getRotationTime());
                else if(lastMinedBlockPos != null)
                    rotation.intLockAngle(AngleUtils.getRequiredYaw(lastMinedBlockPos), mc.thePlayer.rotationPitch, mineBehaviour.getRotationTime());


                if(lastMinedBlockPos != null){
                    if(blocksToMine.getLast().getBlockType() == BlockType.WALK){
                        if (targetMineBlock.getY() >= (int) mc.thePlayer.posY + 1)
                            deltaJumpTick = 3;

                    } else {
                        if (minedBlocks.getLast().getBlockType() == BlockType.MINE) {
                            if (lastMinedBlockPos.getY() >= (int) mc.thePlayer.posY + 1 && !(BlockUtils.onTheSameXZ(lastMinedBlockPos, BlockUtils.getPlayerLoc()))
                                    && (BlockUtils.fitsPlayer(lastMinedBlockPos.down()) || BlockUtils.fitsPlayer(lastMinedBlockPos.down(2))))
                                deltaJumpTick = 3;
                        }
                    }
                } else {
                    if(BlockUtils.isPassable(targetMineBlock) && targetMineBlock.getY() == (int) mc.thePlayer.posY + 1)
                        deltaJumpTick = 3;
                }
                break;

            case MINING:
                mc.thePlayer.inventory.currentItem = PlayerUtils.getItemInHotbar("Pick", "Drill");
                KeybindHandler.updateKeys(
                        false,
                        false,
                        false,
                        false,
                        mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null &&
                                mc.objectMouseOver.getBlockPos().equals(targetMineBlock),
                        false,
                        mineBehaviour.isShiftWhenMine(),
                        false);


                if(mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null){
                    if (!BlockUtils.isPassable(targetMineBlock) && !rotation.rotating)
                        rotation.intLockAngle(AngleUtils.getRequiredYaw(targetMineBlock), AngleUtils.getRequiredPitch(targetMineBlock), mineBehaviour.getRotationTime());
                }
                break;
        }

        if (deltaJumpTick > 0)
            deltaJumpTick--;
    }

    public void onRenderEvent(){
        if(rotation.rotating)
            rotation.update();

    }

    private void updateState(){
        if(blocksToMine.isEmpty())
            return;

        if(minedBlocks.isEmpty()){
            currentState =  blocksToMine.getLast().getBlockType().equals(BlockType.MINE) ? PlayerState.MINING : PlayerState.WALKING;
            return;
        }
        if(blocksToMine.getLast().getBlockType() == BlockType.WALK) {
            currentState = PlayerState.WALKING;
            return;
        }

        if(currentState == PlayerState.WALKING){
            if(minedBlocks.getLast().getBlockType() == BlockType.WALK){
                if(blocksToMine.getLast().getBlockType() == BlockType.MINE)
                    currentState = PlayerState.MINING;

            } else if(minedBlocks.getLast().getBlockType() == BlockType.MINE) {
                if (BlockUtils.onTheSameXZ(minedBlocks.getLast().getBlockPos(), BlockUtils.getPlayerLoc()))
                    currentState = PlayerState.MINING;
            }

        } else if(currentState == PlayerState.MINING){
            if (blocksToMine.getLast().getBlockType() == BlockType.MINE) {
                if( (BlockUtils.fitsPlayer(minedBlocks.getLast().getBlockPos().down()) || BlockUtils.fitsPlayer(minedBlocks.getLast().getBlockPos().down(2)))
                        && !BlockUtils.onTheSameXZ(minedBlocks.getLast().getBlockPos(), BlockUtils.getPlayerLoc())) {
                    currentState = PlayerState.WALKING;
                }
            }

        } else if(currentState == PlayerState.NONE){
            currentState = blocksToMine.getLast().getBlockType().equals(BlockType.MINE) ? PlayerState.MINING : PlayerState.WALKING;
        }
    }

    private final Runnable restartBaritone = () -> {
        try {
            pauseMacro();
            mc.thePlayer.addChatMessage(new ChatComponentText("Restarting baritone"));
            Thread.sleep(200);
            KeybindHandler.setKeyBindState(KeybindHandler.keybindS, true);
            Thread.sleep(100);
            enableBaritone(Blocks.stained_glass, Blocks.stained_glass_pane);
        } catch (InterruptedException ignored) {}
    };


}
