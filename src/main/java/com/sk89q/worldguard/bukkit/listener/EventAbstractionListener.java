/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.listener;

import com.google.common.collect.Lists;
import com.sk89q.worldguard.bukkit.WorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvents;
import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;
import com.sk89q.worldguard.bukkit.event.block.UseBlockEvent;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.DestroyEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.bukkit.event.entity.UseEntityEvent;
import com.sk89q.worldguard.bukkit.event.inventory.UseItemEvent;
import com.sk89q.worldguard.bukkit.listener.debounce.BlockPistonExtendKey;
import com.sk89q.worldguard.bukkit.listener.debounce.BlockPistonRetractKey;
import com.sk89q.worldguard.bukkit.listener.debounce.EventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.AbstractEventDebounce.Entry;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.BlockEntityEventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.EntityEntityEventDebounce;
import com.sk89q.worldguard.bukkit.listener.debounce.legacy.InventoryMoveItemEventDebounce;
import com.sk89q.worldguard.bukkit.util.BlockStateAsBlockFunction;
import com.sk89q.worldguard.bukkit.util.Blocks;
import com.sk89q.worldguard.bukkit.util.Events;
import com.sk89q.worldguard.bukkit.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Dispenser;
import org.bukkit.material.MaterialData;
import org.bukkit.material.SpawnEgg;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldguard.bukkit.cause.Cause.create;

public class EventAbstractionListener extends AbstractListener {

    private final BlockEntityEventDebounce interactDebounce = new BlockEntityEventDebounce(10000);
    private final EntityEntityEventDebounce pickupDebounce = new EntityEntityEventDebounce(10000);
    private final BlockEntityEventDebounce entityBreakBlockDebounce = new BlockEntityEventDebounce(10000);
    private final InventoryMoveItemEventDebounce moveItemDebounce = new InventoryMoveItemEventDebounce(30000);
    private final EventDebounce<BlockPistonRetractKey> pistonRetractDebounce = EventDebounce.create(5000);
    private final EventDebounce<BlockPistonExtendKey> pistonExtendDebounce = EventDebounce.create(5000);

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public EventAbstractionListener(WorldGuardPlugin plugin) {
        super(plugin);
    }

    //-------------------------------------------------------------------------
    // Block break / place
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Events.fireToCancel(event, new BreakBlockEvent(event, create(event.getPlayer()), event.getBlock()));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlock().getLocation().add(0.5, 1, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BlockState previousState = event.getBlockReplacedState();

        // Some blocks, like tall grass and fire, get replaced
        if (previousState.getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, create(event.getPlayer()), previousState.getLocation(), previousState.getType()));
        }

        if (!event.isCancelled()) {
            ItemStack itemStack = new ItemStack(event.getBlockPlaced().getType(), 1);
            Events.fireToCancel(event, new UseItemEvent(event, create(event.getPlayer()), event.getPlayer().getWorld(), itemStack));
        }

        if (!event.isCancelled()) {
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(event.getPlayer()), event.getBlock()));
        }

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block target = event.getBlock();

        Block[] adjacent = new Block[] {
                target.getRelative(BlockFace.NORTH),
                target.getRelative(BlockFace.SOUTH),
                target.getRelative(BlockFace.WEST),
                target.getRelative(BlockFace.EAST),
                target.getRelative(BlockFace.UP),
                target.getRelative(BlockFace.DOWN)};

        int found = 0;
        boolean allowed = false;

        for (Block source : adjacent) {
            if (source.getType() == Material.FIRE) {
                found++;
                if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(source), target))) {
                    source.setType(Material.AIR);
                } else {
                    allowed = true;
                }
            }
        }

        if (found > 0 && !allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrowEvent(StructureGrowEvent event) {
        int originalCount = event.getBlocks().size();
        List<Block> blockList = Lists.transform(event.getBlocks(), new BlockStateAsBlockFunction());

        Player player = event.getPlayer();
        if (player != null) {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(player), event.getLocation().getWorld(), blockList, Material.AIR));
        } else {
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getLocation().getBlock()), event.getLocation().getWorld(), blockList, Material.AIR));
        }

        if (!event.isCancelled() && event.getBlocks().size() != originalCount) {
            event.getLocation().getBlock().setType(Material.AIR);
        }
    }

    // TODO: Handle EntityCreatePortalEvent?

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Entity entity = event.getEntity();
        Material to = event.getTo();

        // Forget about Redstone ore, especially since we handle it in INTERACT
        if (Materials.isRedstoneOre(block.getType()) && Materials.isRedstoneOre(to)) {
            return;
        }

        // Fire two events: one as BREAK and one as PLACE
        if (event.getTo() != Material.AIR && event.getBlock().getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, create(entity), block));
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(entity), block.getLocation(), to));
        } else {
            if (event.getTo() == Material.AIR) {
                // Track the source so later we can create a proper chain of causes
                if (entity instanceof FallingBlock) {
                    Cause.trackParentCause(entity, block);

                    // Switch around the event
                    Events.fireToCancel(event, new SpawnEntityEvent(event, create(block), entity));
                } else {
                    entityBreakBlockDebounce.debounce(
                            event.getBlock(), event.getEntity(), event, new BreakBlockEvent(event, create(entity), event.getBlock()));
                }
            } else {
                boolean wasCancelled = event.isCancelled();
                Cause cause = create(entity);

                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, event.getBlock().getLocation(), to));

                if (event.isCancelled() && !wasCancelled && entity instanceof FallingBlock) {
                    FallingBlock fallingBlock = (FallingBlock) entity;
                    ItemStack itemStack = new ItemStack(fallingBlock.getMaterial(), 1, fallingBlock.getBlockData());
                    Item item = block.getWorld().dropItem(fallingBlock.getLocation(), itemStack);
                    item.setVelocity(new Vector());
                    if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(block, entity), item))) {
                        item.remove();
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();

        Events.fireBulkEventToCancel(event, new BreakBlockEvent(event, create(entity), event.getLocation().getWorld(), event.blockList(), Material.AIR));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (event.isSticky()) {
            EventDebounce.Entry entry = pistonRetractDebounce.getIfNotPresent(new BlockPistonRetractKey(event), event);
            if (entry != null) {
                Cause cause = create(event.getBlock());
                Events.fireToCancel(event, new BreakBlockEvent(event, cause, event.getRetractLocation(), Material.AIR));
                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, event.getBlock().getRelative(event.getDirection())));
                entry.setCancelled(event.isCancelled());

                if (event.isCancelled()) {
                    playDenyEffect(event.getBlock().getLocation().add(0.5, 1, 0.5));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        EventDebounce.Entry entry = pistonExtendDebounce.getIfNotPresent(new BlockPistonExtendKey(event), event);
        if (entry != null) {
            // A hack for now
            List<Block> blocks = new ArrayList<Block>(event.getBlocks());
            Block lastBlock = event.getBlock().getRelative(event.getDirection(), event.getLength() + 1);
            blocks.add(lastBlock);
            int originalLength = blocks.size();
            Events.fireBulkEventToCancel(event, new PlaceBlockEvent(event, create(event.getBlock()), event.getBlock().getWorld(), blocks, Material.STONE));
            if (blocks.size() != originalLength) {
                event.setCancelled(true);
            }
            entry.setCancelled(event.isCancelled());

            if (event.isCancelled()) {
                playDenyEffect(event.getBlock().getLocation().add(0.5, 1, 0.5));
            }
        }
    }

    //-------------------------------------------------------------------------
    // Block external interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Block target = event.getBlock();

        // Previously, and perhaps still, the only way to catch cake eating
        // events was through here
        if (target.getType() == Material.CAKE_BLOCK) {
            Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), target));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        @Nullable ItemStack item = player.getItemInHand();
        Block clicked = event.getClickedBlock();
        Block placed;
        boolean silent = false;
        Cause cause = create(player);

        switch (event.getAction()) {
            case PHYSICAL:
                // Forget about Redstone ore
                if (Materials.isRedstoneOre(clicked.getType()) || clicked.getType() == Material.SOIL) {
                    silent = true;
                }

                interactDebounce.debounce(clicked, event.getPlayer(), event, DelegateEvents.setSilent(new UseBlockEvent(event, cause, clicked), silent));
                break;

            case RIGHT_CLICK_BLOCK:
                placed = clicked.getRelative(event.getBlockFace());

                // Re-used for dispensers
                handleBlockRightClick(event, create(event.getPlayer()), item, clicked, event.getBlockFace(), placed);

            case LEFT_CLICK_BLOCK:
                placed = clicked.getRelative(event.getBlockFace());

                // Only fire events for blocks that are modified when right clicked
                if (isBlockModifiedOnClick(clicked) || (item != null && isItemAppliedToBlock(item, clicked))) {
                    if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, clicked))) {
                        event.setUseInteractedBlock(Result.DENY);
                    }

                    // Handle connected blocks (i.e. beds, chests)
                    for (Block connected : Blocks.getConnected(clicked)) {
                        if (Events.fireAndTestCancel(new UseBlockEvent(event, create(event.getPlayer()), connected))) {
                            event.setUseInteractedBlock(Result.DENY);
                            break;
                        }
                    }
                }

                // Special handling of putting out fires
                if (event.getAction() == Action.LEFT_CLICK_BLOCK && placed.getType() == Material.FIRE) {
                    if (Events.fireAndTestCancel(new BreakBlockEvent(event, create(event.getPlayer()), placed))) {
                        event.setUseInteractedBlock(Result.DENY);
                        break;
                    }
                }

                if (event.isCancelled()) {
                    playDenyEffect(event.getPlayer(), clicked.getLocation().add(0.5, 1, 0.5));
                }

            case LEFT_CLICK_AIR:
            case RIGHT_CLICK_AIR:
                if (item != null && !item.getType().isBlock() && Events.fireAndTestCancel(new UseItemEvent(event, cause, player.getWorld(), item))) {
                    event.setUseItemInHand(Result.DENY);
                }

                // Check for items that the administrator has configured to
                // emit a "use block here" event where the player is
                // standing, which is a hack to protect items that don't
                // throw events
                if (item != null && getWorldConfig(player.getWorld()).blockUseAtFeet.test(item)) {
                    if (Events.fireAndTestCancel(new UseBlockEvent(event, cause, player.getLocation().getBlock()))) {
                        event.setCancelled(true);
                    }
                }

                break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(org.bukkit.event.entity.EntityInteractEvent event) {
        interactDebounce.debounce(event.getBlock(), event.getEntity(), event, new UseBlockEvent(event, create(event.getEntity()), event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Cause cause;

        // Find the cause
        if (event.getPlayer() != null) {
            cause = create(event.getPlayer());
        } else if (event.getIgnitingEntity() != null) {
            cause = create(event.getIgnitingEntity());
        } else if (event.getIgnitingBlock() != null) {
            cause = create(event.getIgnitingBlock());
        } else {
            cause = Cause.unknown();
        }

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, block.getLocation(), Material.FIRE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), event.getBlock()));

        if (event.isCancelled()) {
            playDenyEffect(event.getPlayer(), event.getBlock().getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Events.fireToCancel(event, new UseBlockEvent(event, create(event.getPlayer()), event.getBed()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block blockAffected = event.getBlockClicked().getRelative(event.getBlockFace());

        // Milk buckets can't be emptied as of writing
        if (event.getBucket() != Material.MILK_BUCKET) {
            ItemStack item = new ItemStack(event.getBucket(), 1);
            Material blockMaterial = Materials.getBucketBlockMaterial(event.getBucket());
            Events.fireToCancel(event, new PlaceBlockEvent(event, create(player), blockAffected.getLocation(), blockMaterial));
            Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item));
        }

        playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 0.5, 0.5));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block blockAffected = event.getBlockClicked().getRelative(event.getBlockFace());

        // Milk buckets can't be filled by right clicking the ground as of writing
        if (event.getBucket() != Material.MILK_BUCKET) {
            ItemStack item = new ItemStack(event.getBucket(), 1);
            Events.fireToCancel(event, new BreakBlockEvent(event, create(player), blockAffected));
            Events.fireToCancel(event, new UseItemEvent(event, create(player), player.getWorld(), item));
        }

        playDenyEffect(event.getPlayer(), blockAffected.getLocation().add(0.5, 1, 0.5));
    }

    // TODO: Handle EntityPortalEnterEvent

    //-------------------------------------------------------------------------
    // Block self-interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        WorldConfiguration config = getWorldConfig(event.getBlock().getWorld());

        // This only applies to regions but nothing else cares about high
        // frequency events at the moment
        if (!config.useRegions || (!config.highFreqFlags && !config.checkLiquidFlow)) {
            return;
        }

        Block from = event.getBlock();
        Block to = event.getToBlock();
        Material fromType = from.getType();
        Material toType = to.getType();

        // Liquids pass this event when flowing to solid blocks
        if (toType.isSolid() && Materials.isLiquid(fromType)) {
            return;
        }

        // This significantly reduces the number of events without having
        // too much effect. Unfortunately it appears that even if this
        // check didn't exist, you can raise the level of some liquid
        // flow and the from/to data may not be correct.
        if ((Materials.isWater(fromType) && Materials.isWater(toType)) || (Materials.isLava(fromType) && Materials.isLava(toType))) {
            return;
        }

        Cause cause = create(from);

        // Disable since it's probably not needed
        /*if (from.getType() != Material.AIR) {
            Events.fireToCancel(event, new BreakBlockEvent(event, cause, to));
        }*/

        Events.fireToCancel(event, new PlaceBlockEvent(event, cause, to.getLocation(), from.getType()));
    }

    //-------------------------------------------------------------------------
    // Entity break / place
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case DISPENSE_EGG:
            case EGG:
            case SPAWNER_EGG:
                if (getWorldConfig(event.getEntity().getWorld()).strictEntitySpawn) {
                    Events.fireToCancel(event, new SpawnEntityEvent(event, Cause.unknown(), event.getEntity()));
                }
                break;
            case NATURAL:
            case JOCKEY:
            case CHUNK_GEN:
            case SPAWNER:
            case LIGHTNING:
            case BUILD_SNOWMAN:
            case BUILD_IRONGOLEM:
            case BUILD_WITHER:
            case VILLAGE_DEFENSE:
            case VILLAGE_INVASION:
            case BREEDING:
            case SLIME_SPLIT:
            case REINFORCEMENTS:
            case NETHER_PORTAL:
            case INFECTION:
            case CURED:
            case OCELOT_BABY:
            case SILVERFISH_BLOCK:
            case MOUNT:
            case CUSTOM:
            case DEFAULT:
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Events.fireToCancel(event, new SpawnEntityEvent(event, create(event.getPlayer()), event.getEntity()));

        if (event.isCancelled()) {
            Block effectBlock = event.getBlock().getRelative(event.getBlockFace());
            playDenyEffect(event.getPlayer(), effectBlock.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            Entity remover = ((HangingBreakByEntityEvent) event).getRemover();
            Events.fireToCancel(event, new DestroyEntityEvent(event, create(remover), event.getEntity()));

            if (event.isCancelled() && remover instanceof Player) {
                playDenyEffect((Player) remover, event.getEntity().getLocation());
            }
        } else {
            Events.fireToCancel(event, new DestroyEntityEvent(event, Cause.unknown(), event.getEntity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Events.fireToCancel(event, new DestroyEntityEvent(event, create(event.getAttacker()), event.getVehicle()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        if (event.getExpToDrop() > 0) { // Event is raised even where no XP is being dropped
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getBlock()), event.getBlock().getLocation(), EntityType.EXPERIENCE_ORB))) {
                event.setExpToDrop(0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getPlayer(), event.getHook()), event.getHook().getLocation(), EntityType.EXPERIENCE_ORB))) {
            event.setExpToDrop(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExpBottle(ExpBottleEvent event) {
        if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getEntity()), event.getEntity().getLocation(), EntityType.EXPERIENCE_ORB))) {
            event.setExperience(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getDroppedExp() > 0) {
            if (Events.fireAndTestCancel(new SpawnEntityEvent(event, create(event.getEntity()), event.getEntity().getLocation(), EntityType.EXPERIENCE_ORB))) {
                event.setDroppedExp(0);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Entity external interaction
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        ItemStack item = player.getItemInHand();
        Entity entity = event.getRightClicked();

        Events.fireToCancel(event, new UseItemEvent(event, create(player), world, item));
        Events.fireToCancel(event, new UseEntityEvent(event, create(player), entity));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByBlockEvent) {
            Events.fireToCancel(event, new DamageEntityEvent(event, create(((EntityDamageByBlockEvent) event).getDamager()), event.getEntity()));

        } else if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
            Entity damager = entityEvent.getDamager();
            Events.fireToCancel(event, new DamageEntityEvent(event, create(damager), event.getEntity()));

            // Item use event with the item in hand
            // Older blacklist handler code used this, although it suffers from
            // race problems
            if (damager instanceof Player) {
                ItemStack item = ((Player) damager).getItemInHand();

                if (item != null) {
                    Events.fireToCancel(event, new UseItemEvent(event, create(damager), event.getEntity().getWorld(), item));
                }
            }

        } else {
            Events.fireToCancel(event, new DamageEntityEvent(event, Cause.unknown(), event.getEntity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent) {
            Events.fireToCancel(event, new DamageEntityEvent(event, create(((EntityCombustByBlockEvent) event).getCombuster()), event.getEntity()));

        } else if (event instanceof EntityCombustByEntityEvent) {
            Events.fireToCancel(event, new DamageEntityEvent(event, create(((EntityCombustByEntityEvent) event).getCombuster()), event.getEntity()));

        } else {
            Events.fireToCancel(event, new DamageEntityEvent(event, Cause.unknown(), event.getEntity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityUnleash(EntityUnleashEvent event) {
        if (event instanceof PlayerUnleashEntityEvent) {
            PlayerUnleashEntityEvent playerEvent = (PlayerUnleashEntityEvent) event;
            Events.fireToCancel(playerEvent, new UseEntityEvent(playerEvent, create(playerEvent.getPlayer()), event.getEntity()));
        } else {
            // TODO: Raise anyway?
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getOwner()), event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getPlayer()), event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Item item = event.getItem();
        pickupDebounce.debounce(event.getPlayer(), item, event, new DestroyEntityEvent(event, create(event.getPlayer()), event.getItem()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Events.fireToCancel(event, new SpawnEntityEvent(event, create(event.getPlayer()), event.getItemDrop()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Entity attacker = event.getAttacker();
        Events.fireToCancel(event, new DestroyEntityEvent(event, create(attacker), event.getVehicle()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleDamageEvent event) {
        Events.fireToCancel(event, new UseEntityEvent(event, create(event.getAttacker()), event.getVehicle()));
    }

    //-------------------------------------------------------------------------
    // Composite events
    //-------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        final InventoryHolder causeHolder = event.getInitiator().getHolder();
        InventoryHolder sourceHolder = event.getSource().getHolder();
        InventoryHolder targetHolder = event.getDestination().getHolder();

        Entry entry;

        if ((entry = moveItemDebounce.tryDebounce(event)) != null) {
            Cause cause;

            if (causeHolder instanceof Entity) {
                cause = create(causeHolder);
            } else if (causeHolder instanceof BlockState) {
                cause = create(((BlockState) causeHolder).getBlock());
            } else {
                cause = Cause.unknown();
            }

            if (!causeHolder.equals(sourceHolder)) {
                handleInventoryHolderUse(event, cause, sourceHolder);
            }

            handleInventoryHolderUse(event, cause, targetHolder);

            if (event.isCancelled() && causeHolder instanceof Hopper) {
                Bukkit.getScheduler().scheduleSyncDelayedTask(getPlugin(), new Runnable() {
                    @Override
                    public void run() {
                        ((Hopper) causeHolder).getBlock().breakNaturally();
                    }
                });
            } else {
                entry.setCancelled(event.isCancelled());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        Entity entity = event.getEntity();
        ThrownPotion potion = event.getPotion();
        World world = entity.getWorld();
        Cause cause = create(potion.getShooter());

        // Fire item interaction event
        Events.fireToCancel(event, new UseItemEvent(event, cause, world, potion.getItem()));

        // Fire entity interaction event
        if (!event.isCancelled()) {
            int blocked = 0;

            for (LivingEntity affected : event.getAffectedEntities()) {
                if (Events.fireAndTestCancel(new DamageEntityEvent(event, cause, affected))) {
                    event.setIntensity(affected, 0);
                    blocked++;
                }
            }

            if (blocked == event.getAffectedEntities().size()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        Cause cause = create(event.getBlock());
        Block dispenserBlock = event.getBlock();
        ItemStack item = event.getItem();
        MaterialData materialData = dispenserBlock.getState().getData();

        Events.fireToCancel(event, new UseItemEvent(event, cause, dispenserBlock.getWorld(), item));

        // Simulate right click event as players have it
        if (materialData instanceof Dispenser) {
            Dispenser dispenser = (Dispenser) materialData;
            Block placed = dispenserBlock.getRelative(dispenser.getFacing());
            Block clicked = placed.getRelative(dispenser.getFacing());
            handleBlockRightClick(event, cause, item, clicked, dispenser.getFacing().getOppositeFace(), placed);
        }
    }

    /**
     * Handle the right click of a block while an item is held.
     *
     * @param event the original event
     * @param cause the list of cause
     * @param item the item
     * @param clicked the clicked block
     * @param faceClicked the face of the clicked block
     * @param placed the placed block
     * @param <T> the event type
     */
    private static <T extends Event & Cancellable> void handleBlockRightClick(T event, Cause cause, @Nullable ItemStack item, Block clicked, BlockFace faceClicked, Block placed) {
        if (item != null && item.getType() == Material.TNT) {
            // Workaround for a bug that allowed TNT to trigger instantly if placed
            // next to redstone, without plugins getting the clicked place event
            // (not sure if this actually still happens)
            Events.fireToCancel(event, new UseBlockEvent(event, cause, clicked.getLocation(), Material.TNT));
        }

        // Handle created Minecarts
        if (item != null && Materials.isMinecart(item.getType())) {
            // TODO: Give a more specific Minecart type
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.MINECART));
        }

        // Handle created boats
        if (item != null && item.getType() == Material.BOAT) {
            Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), EntityType.BOAT));
        }

        // Handle created spawn eggs
        if (item != null && item.getType() == Material.MONSTER_EGG) {
            MaterialData data = item.getData();
            if (data instanceof SpawnEgg) {
                Events.fireToCancel(event, new SpawnEntityEvent(event, cause, placed.getLocation().add(0.5, 0, 0.5), ((SpawnEgg) data).getSpawnedType()));
            }
        }

        // Handle cocoa beans
        if (item != null && item.getType() == Material.INK_SACK && Materials.isDyeColor(item.getData(), DyeColor.BROWN)) {
            // CraftBukkit doesn't or didn't throw a clicked place for this
            if (!(faceClicked == BlockFace.DOWN || faceClicked == BlockFace.UP)) {
                Events.fireToCancel(event, new PlaceBlockEvent(event, cause, placed.getLocation(), Material.COCOA));
            }
        }

        // Workaround for http://leaky.bukkit.org/issues/1034
        if (item != null && item.getType() == Material.TNT) {
            Events.fireToCancel(event, new PlaceBlockEvent(event, cause, placed.getLocation(), Material.TNT));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Event & Cancellable> void handleInventoryHolderUse(T originalEvent, Cause cause, InventoryHolder holder) {
        if (originalEvent.isCancelled()) {
            return;
        }

        if (holder instanceof Entity) {
            Events.fireToCancel(originalEvent, new UseEntityEvent(originalEvent, cause, (Entity) holder));
        } else if (holder instanceof BlockState) {
            Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((BlockState) holder).getBlock()));
        } else if (holder instanceof DoubleChest) {
            Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((BlockState) ((DoubleChest) holder).getLeftSide()).getBlock()));
            Events.fireToCancel(originalEvent, new UseBlockEvent(originalEvent, cause, ((BlockState) ((DoubleChest) holder).getRightSide()).getBlock()));
        }
    }

    private boolean isBlockModifiedOnClick(Block block) {
        return Materials.isBlockModifiedOnClick(block.getType()) && !getWorldConfig(block.getWorld()).allowAllInteract.test(block);
    }

    private boolean isItemAppliedToBlock(ItemStack item, Block clicked) {
        return Materials.isItemAppliedToBlock(item.getType(), clicked.getType())
                && !getWorldConfig(clicked.getWorld()).allowAllInteract.test(clicked)
                && !getWorldConfig(clicked.getWorld()).allowAllInteract.test(item);
    }

    private void playDenyEffect(Player player, Location location) {
        //player.playSound(location, Sound.SUCCESSFUL_HIT, 0.2f, 0.4f);
        player.playEffect(location, Effect.SMOKE, BlockFace.UP);
    }

    private void playDenyEffect(Location location) {
        location.getWorld().playEffect(location, Effect.SMOKE, BlockFace.UP);
    }

}
