/*
 *     This file is part of TRIGGER by @catkillsreality.
 *
 *     TRIGGER is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *     TRIGGER is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along with TRIGGER. If not, see <https://www.gnu.org/licenses/>.
 */

package cat.TRIGGER;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;


/**
 * A utility class for managing a collection of triggers.
 * Standalone use of the {@link Trigger} class is not recommended since it does not contain any event hooks.
 */
public class TriggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerManager.class);
    private final List<Trigger> triggers;
    private int totalTriangles = 0;
    private final boolean debug;

    /**
     * The default constructor
     * @param debug Debug mode, enables rendering. <p> DEBUG RENDERING CAN CAUSE BIG LAG.
     */
    public TriggerManager(boolean debug) {
        this.debug = debug;
        this.triggers = new ArrayList<>();

        if (debug) {
            MinecraftServer.getSchedulerManager().buildTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> triggers.forEach(trigger -> trigger.render(player)))).repeat(TaskSchedule.nextTick()).schedule();
        }
    }

    /**
     * Alternate constructor that allows you to pass a list of existing triggers.
     * @param debug Debug mode, enables rendering. <p> DEBUG RENDERING CAN CAUSE BIG LAG.
     * @param triggers The existing triggers.
     */
    public TriggerManager(boolean debug, List<Trigger> triggers) {
        this.debug = debug;
        this.triggers = triggers;

        if (debug) {
            MinecraftServer.getSchedulerManager().buildTask(() -> MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> triggers.forEach(trigger -> trigger.render(player)))).repeat(TaskSchedule.nextTick()).schedule();
        }
    }

    public Trigger create(List<Vec> anchors, Vec position, UUID uuid, Component name, RGBLike color, Consumer<TriggeredCallback> triggeredCallback) {
        if (!Trigger.validatePoints(anchors)) LOGGER.warn("Detected very close points for {}, collision and/or rendering may break due to numerical instability, use at your own risk", PlainTextComponentSerializer.plainText().serialize(name));

        /*---------------< EXTRUDE 2D INTO 3D >---------------*/
        // since 2D shapes projected onto 3D directly would be infinitely thin, extruding them slightly allows for good collision detection.
        if (Trigger.arePointsCoplanar(anchors)) {


            double thickness = 0.1;
            List<Vec> extrudedPoints = new ArrayList<>();
            Vec origin = anchors.get(0);

            // Calculate normal of the plane
            Vec edge1 = anchors.get(1).sub(origin);
            Vec edge2 = anchors.get(2).sub(origin);
            Vec normal = edge1.cross(edge2).normalize();

            for (Vec point : anchors) {
                extrudedPoints.add(point.add(normal.mul(thickness / 2)));
                extrudedPoints.add(point.sub(normal.mul(thickness / 2)));
            }

            anchors = List.copyOf(extrudedPoints);
        }

        final Trigger trigger = new Trigger(anchors, position, uuid, name, color, triggeredCallback);

        if (debug) {
            DecimalFormat df = new DecimalFormat("###.###");
            LOGGER.info("Hull computation of {} took {}ms", PlainTextComponentSerializer.plainText().serialize(trigger.getName()), df.format(trigger.getLastComputationTime()));
        }

        triggers.add(trigger);
        totalTriangles += trigger.getTriangles().size();
        return trigger;
    }

    /**
     * The main movement event hook that glues the underlying collision logic together.
     * @param event The {@link PlayerMoveEvent}.
     */
    public void playerMoveEvent(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final Pos oldPos = player.getPosition();
        final Pos newPos = event.getNewPosition();

        List<Vec> previousPoints = Trigger.getHitboxPoints(oldPos, player);
        List<Vec> currentPoints = Trigger.getHitboxPoints(newPos, player);

        for (Trigger trigger : triggers) {
            // skip expensive checks if the player is nowhere near that trigger
            final double checkRadius = trigger.getCheckRadius();
            if (trigger.getPosition().distanceSquared(oldPos) > checkRadius * checkRadius) {
                continue;
            } else if (trigger.getPosition().distanceSquared(newPos) > checkRadius * checkRadius) {
                continue;
            }

            boolean wasInside = trigger.contains(previousPoints);
            boolean isInside = trigger.contains(currentPoints);

            if (!wasInside && isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.ENTERED));
            } else if (wasInside && !isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.EXITED));
            }

            if (isInside) {
                trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.TICK));
            }
        }
    }

    /**
     * The main teleport event hook that glues the underlying collision logic together.
     * @param event The {@link EntityTeleportEvent}.
     */
    public void entityTeleportEvent(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player player) {
            final Pos oldPos = player.getPosition();
            final Pos newPos = event.getNewPosition();

            List<Vec> previousPoints = Trigger.getHitboxPoints(oldPos, player);
            List<Vec> currentPoints = Trigger.getHitboxPoints(newPos, player);

            for (Trigger trigger : triggers) {
                // skip expensive checks if the player is nowhere near that trigger
                final double checkRadius = trigger.getCheckRadius();
                if (trigger.getPosition().distanceSquared(oldPos) > checkRadius * checkRadius) {
                    continue;
                } else if (trigger.getPosition().distanceSquared(newPos) > checkRadius * checkRadius) {
                    continue;
                }

                boolean wasInside = trigger.contains(previousPoints);
                boolean isInside = trigger.contains(currentPoints);

                if (!wasInside && isInside) {
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.ENTERED));
                } else if (wasInside && !isInside) {
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.EXITED));
                }

                if (isInside) {
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.TICK));
                }
            }
        }
    }

    /**
     * The main spawn event hook that glues the underlying collision logic together.
     * @param event The {@link EntitySpawnEvent}.
     */
    public void entitySpawnEvent(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Player player) {
            final Pos spawnPos = player.getPosition();

            List<Vec> currentPoints = Trigger.getHitboxPoints(spawnPos, player);

            for (Trigger trigger : triggers) {
                // skip expensive checks if the player is nowhere near that trigger
                final double checkRadius = trigger.getCheckRadius();
                if (trigger.getPosition().distanceSquared(spawnPos) > checkRadius * checkRadius) {
                    continue;
                }

                boolean isInside = trigger.contains(currentPoints);

                // The player either spawns inside or not inside
                if (isInside) {
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.TICK));
                    trigger.getTriggeredCallback().accept(new TriggeredCallback(player, trigger, TriggeredCallback.Type.ENTERED));
                }
            }
        }
    }

    public void registerEvents(EventNode<@NotNull Event> handler) {
        handler.addListener(PlayerMoveEvent.class, this::playerMoveEvent)
                .addListener(EntityTeleportEvent.class, this::entityTeleportEvent)
                .addListener(EntitySpawnEvent.class, this::entitySpawnEvent);
    }

    /**
     * Remove a trigger from {@link TriggerManager#triggers}.
     * @param trigger The trigger to remove.
     * @return True if the trigger was removed, false if it does not exist.
     */
    public boolean remove(Trigger trigger) {
        if (!triggers.contains(trigger)) return false;
        triggers.remove(trigger);
        totalTriangles -= trigger.getTriangles().size();
        return true;
    }

    /**
     * Add an existing trigger to {@link TriggerManager#triggers}.
     * @param trigger The trigger to add.
     */
    public void add(Trigger trigger) {
        triggers.add(trigger);
    }

    /**
     * Check if the list of {@link TriggerManager#triggers triggers} contains a trigger.
     * @param trigger The trigger to check containment for.
     * @return true if {@link TriggerManager#triggers triggers} contains the trigger, false if not.
     */
    public boolean contains(Trigger trigger) {
        return triggers.contains(trigger);
    }

    /**
     * Iterate over all registered triggers.
     * @param trigger The consumer.
     */
    public void forEach(Consumer<Trigger> trigger) {
        triggers.forEach(trigger);
    }

    /**
     * Get an unmodifiable copy of the list containing all triggers of this manager.
     * @return An unmodifiable copy of the list containing all triggers of this manager.
     */
    public List<Trigger> getTriggers() {
        return List.copyOf(triggers);
    }

    /**
     * Get the combined total triangle count of all registered triggers.
     * @return Combined total triangle count of all registered triggers.
     */
    public int getTotalTriangles() {
        return totalTriangles;
    }

    /**
     * Get if this instance is in debug mode.
     * @return true if in debug mode, false if not.
     */
    public boolean isDebug() {
        return debug;
    }
}



