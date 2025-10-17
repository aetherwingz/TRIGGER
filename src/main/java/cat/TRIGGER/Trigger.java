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

import cat.TRIGGER.quickhull3d.Point3d;
import cat.TRIGGER.quickhull3d.QuickHull3D;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The core logic of triggers.
 * This system is optimized for static geometry.
 * Triggers can be repositioned using {@link Trigger#setPosition(Pos)}, but they should not be constantly moving, since the collision detection runs per PlayerMoveEvent.
 * Simply put, the shape of a trigger is constructed by "shrink wrapping" all anchors, which is done by the {@link QuickHull3D} algorithm.
 * The resulting triangles make up the shape and are used for the collision detection and debug rendering.
 * Collision detection is based on the Separating Axis Theorem (SAT).
 * 2D shapes (where all anchors are coplanar, meaning they lie on the same plane) are slightly extruded to create a more functional trigger.
 * Should be used with {@link TriggerManager} and not by itself.
 * <p>
 * Note: most positions in 3D space are represented by {@link Vec}
 */
public class Trigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(Trigger.class);
    private final UUID uuid;
    private final List<Vec> anchors;
    private Vec position;
    private double checkRadius = 0;

    private Consumer<TriggeredCallback> triggeredCallback;

    private final Component name;
    private RGBLike color; // Debug render color
    private List<Triangle> triangles;
    private double lastComputationTime = 0.0;

    /**
     *
     * @param anchors The points (corners) in 3D space that define the shape of the trigger.
     * @param position The origin of the anchors, used to place the trigger in the world.
     * @param uuid The UUID used to identify the shape. Usually random, should not persist through restarts.
     * @param name The {@link Component} that holds the display name of the trigger.
     * @param color The {@link RGBLike} used for debug rendering.
     * @param triggeredCallback The {@link Consumer<TriggeredCallback>} that gets called when a trigger is triggered.
     */
    public Trigger(List<Vec> anchors, Vec position, UUID uuid, Component name, RGBLike color, Consumer<TriggeredCallback> triggeredCallback) {
        this.anchors = anchors;
        this.position = position;
        this.uuid = uuid;
        this.name = name;
        this.color = color;
        this.triggeredCallback = triggeredCallback;
        compute();
    }

    /**
     *  Internal method for the initial computation of the hull/shape.
     *  Call {@link Trigger#recompute()} to recompute the hull.
     */
    private void compute() {
        long startTime = System.nanoTime();
        if (anchors.size() < 4) {
            throw new IllegalArgumentException("Insufficient anchors to compute");
        }
        Point3d[] points = anchors.stream()
                .map(v -> new Point3d(v.x(), v.y(), v.z()))
                .toArray(Point3d[]::new);

        QuickHull3D hull = new QuickHull3D();
        hull.build(points);

        Point3d[] vertices = hull.getVertices();
        int[][] faceIndices = hull.getFaces();

        List<Triangle> tris = new ArrayList<>();
        for (int[] face : faceIndices) {
            if (face.length < 3) continue;

            Vec a = toVec(vertices[face[0]]);
            for (int i = 1; i < face.length - 1; i++) {
                Vec b = toVec(vertices[face[i]]);
                Vec c = toVec(vertices[face[i + 1]]);
                Vec normal = computeNormal(a, b, c);
                tris.add(new Triangle(a, b, c, normal));
            }
        }
        this.triangles = tris;
        this.checkRadius = 1.5 * computeBoundingRadius(anchors, position);

        long endTime = System.nanoTime();
        long durationInNs = endTime - startTime;
        this.lastComputationTime = durationInNs / 1000000.0;
    }

    /**
     * {@link Trigger#compute() Compute} the hull again. Don't call too often.
     */
    public void recompute() {
        compute();
    }

    /**
     * Internal method for calculating the distance between {@link Trigger#position} and the furthest {@link Trigger#anchors anchor}.
     * The {@link Trigger#checkRadius radius} used to check if you're near a trigger or not in order to skip more expensive calculations.
     * @param anchors The list of anchors to search through.
     * @param position The position to calculate distance to the furthest anchor from.
     * @return The distance from the position to the furthest anchor.
     */
    private double computeBoundingRadius(List<Vec> anchors, Vec position) {
        return anchors.stream().mapToDouble(anchor -> anchor.distance(position)).max().orElse(0);
    }

    /**
     * Iterate through all {@link Trigger#triangles} of the hull and draw them.
     * <p>
     * This can cause a LOT of LAG.
     * @param player The player to render the hull for.
     */
    public void render(Player player) {
        for (Triangle tri : triangles) {
            drawLine(player, tri.a().add(position), tri.b().add(position), color);
            drawLine(player, tri.b().add(position), tri.c().add(position), color);
            drawLine(player, tri.c().add(position), tri.a().add(position), color);
        }
    }

    /**
     * Internal part of the collision check.
     * @param points The points to check containment for.
     * @return Result of the containment check.
     */
    protected boolean contains(List<Vec> points) {
        List<Vec> axes = new ArrayList<>();
        for (Triangle tri : triangles) {
            axes.add(tri.normal());
        }
        axes.add(new Vec(1, 0, 0));
        axes.add(new Vec(0, 1, 0));
        axes.add(new Vec(0, 0, 1));

        for (Vec axis : axes) {
            double[] hullProj = projectOntoAxis(axis, anchors.stream()
                    .map(p -> p.add(position))
                    .toList());
            double[] boxProj = projectOntoAxis(axis, points);
            if (!overlaps(hullProj, boxProj)) {
                return false;
            }
        }
        return true;
    }

    /**
     * I don't remember what this does. Open a pull request to correct this if you know.
     * @param a double array.
     * @param b double array.
     * @return something about the two arrays.
     */
    private boolean overlaps(double[] a, double[] b) {
        return a[0] <= b[1] && b[0] <= a[1];
    }

    /**
     * Convert minestom's {@link Vec} to {@link QuickHull3D}'s {@link Point3d}.
     * @param p The {@link Point3d} to convert.
     * @return The converted {@link Vec}.
     */
    private static Vec toVec(Point3d p) {
        return new Vec(p.x, p.y, p.z);
    }

    /**
     * Internally used to compute the normal of a {@link Triangle}.
     * @param a First corner of the {@link Triangle}.
     * @param b Second corner of the {@link Triangle}.
     * @param c Third corner of the {@link Triangle}.
     * @return The normal {@link Vec vector} of the {@link Triangle}.
     */
    private static Vec computeNormal(Vec a, Vec b, Vec c) {
        Vec ab = b.sub(a);
        Vec ac = c.sub(a);
        return ab.cross(ac).normalize();
    }

    /**
     * Internally used to project a {@link List} of points onto an axis.
     * @param axis The axis {@link Vec} to project the points onto.
     * @param points The points to project onto the axis.
     * @return The projected points.
     */
    private double[] projectOntoAxis(Vec axis, List<Vec> points) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (Vec p : points) {
            double proj = p.dot(axis);
            if (proj < min) min = proj;
            if (proj > max) max = proj;
        }

        return new double[]{min, max};
    }

    /**
     * Draws a line using particles from one point to another.
     * Amount of particles is scaled based off of the length of the line, limited to 32.
     * <p>
     * This can cause a LOT of LAG.
     * @param player The player to draw the line for.
     * @param from The starting positon of the line.
     * @param to The target position of the line.
     * @param debugColor The color of the particles that the line is drawn with.
     */
    private void drawLine(Player player, Point from, Point to, RGBLike debugColor) {
        Vec start = new Vec(from.x(), from.y(), from.z());
        Vec end = new Vec(to.x(), to.y(), to.z());
        int steps = Math.min(32, 4 * Math.round((float) start.distance(end)));


        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = start.x() + (end.x() - start.x()) * t;
            double y = start.y() + (end.y() - start.y()) * t;
            double z = start.z() + (end.z() - start.z()) * t;

            ParticlePacket packet = new ParticlePacket(
                    Particle.TRAIL.withProperties(new Pos(x, y, z), debugColor, 7),
                    true, true,
                    x, y, z,
                    0f, 0f, 0f, 0f, 1
            );
            player.sendPacket(packet);
        }
    }

    /**
     * Calculates the corners of a players hitbox.
     * @param basePos The position of the player.
     * @param player The player.
     * @return A list of the hitbox corners.
     */
    public static List<Vec> getHitboxPoints(Pos basePos, Player player) {
        BoundingBox box = player.getBoundingBox();

        double halfWidth = box.width() / 2;
        double halfDepth = box.depth() / 2;
        double height = box.height();

        List<Vec> corners = new ArrayList<>(8);
        for (int xSign : new int[]{-1, 1}) {
            for (int ySign : new int[]{0, 1}) {
                for (int zSign : new int[]{-1, 1}) {
                    double x = basePos.x() + xSign * halfWidth;
                    double y = basePos.y() + ySign * height;
                    double z = basePos.z() + zSign * halfDepth;
                    corners.add(new Vec(x, y, z));
                }
            }
        }
        return corners;
    }

    /**
     * Very small value used as a minimum distance threshold
     */
    private static final double EPSILON = 1e-6;

    /**
     * Checks a list of points for any that might be too close together. Used for validating {@link Trigger#anchors}.
     * @param points The points to validate.
     * @return true if all points are sufficiently spaced, false if they are too close.
     */
    public static boolean validatePoints(List<Vec> points) {
        for (int i = 0; i < points.size(); i++) {
            Vec p1 = points.get(i);

            for (int j = i + 1; j < points.size(); j++) {
                Vec p2 = points.get(j);

                double dx = p1.x() - p2.x();
                double dy = p1.y() - p2.y();
                double dz = p1.z() - p2.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance < EPSILON) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if a list of points are coplanar aka. on the same plane.
     * Used for checking if base anchors are 2D.
     * @param points The list of points to check.
     * @return true if all points are coplanar, false if not
     */
    public static boolean arePointsCoplanar(List<Vec> points) {
        if (points.size() <= 3) {
            return true;
        }

        Vec centroid = new Vec(0, 0, 0);
        for (Vec p : points) {
            centroid = centroid.add(p);
        }
        centroid = centroid.mul(1.0 / points.size());

        double maxDistSquared = 0;
        for (Vec p : points) {
            double distSquared = p.sub(centroid).lengthSquared();
            if (distSquared > maxDistSquared) {
                maxDistSquared = distSquared;
            }
        }

        double scaleFactor = 1.0;
        if (maxDistSquared > 1e-14) {
            scaleFactor = 1.0 / Math.sqrt(maxDistSquared);
        }

        double[][] matrix = new double[points.size()][4];
        for (int i = 0; i < points.size(); i++) {
            Vec p = points.get(i).sub(centroid).mul(scaleFactor);
            matrix[i][0] = p.x();
            matrix[i][1] = p.y();
            matrix[i][2] = p.z();
            matrix[i][3] = 1.0;
        }

        final double EPSILON = 1e-12;

        int rows = matrix.length;
        int cols = 4;
        int rank = 0;
        boolean[] rowUsed = new boolean[rows];

        for (int c = 0; c < cols; c++) {
            int pivotRow = -1;
            double pivotValue = EPSILON;

            for (int r = 0; r < rows; r++) {
                if (!rowUsed[r] && Math.abs(matrix[r][c]) > pivotValue) {
                    pivotRow = r;
                    pivotValue = Math.abs(matrix[r][c]);
                }
            }

            if (pivotRow == -1) {
                continue;
            }

            rowUsed[pivotRow] = true;
            rank++;

            double pivotScale = 1.0 / matrix[pivotRow][c];
            for (int j = c; j < cols; j++) {
                matrix[pivotRow][j] *= pivotScale;
            }

            for (int r = 0; r < rows; r++) {
                if (r != pivotRow && Math.abs(matrix[r][c]) > EPSILON) {
                    double factor = matrix[r][c];
                    for (int j = c; j < cols; j++) {
                        matrix[r][j] -= factor * matrix[pivotRow][j];
                        if (Math.abs(matrix[r][j]) < EPSILON) {
                            matrix[r][j] = 0.0;
                        }
                    }
                }
            }
        }
        return rank <= 3;
    }

    /**
     * A representation of a simple triangle and its normal used for calculations in 3D space.
     * @param a The first {@link Point} of the triangle.
     * @param b The second {@link Point} of the triangle.
     * @param c The third {@link Point} of the triangle.
     * @param normal The normal {@link Vec} of the triangle.
     */
    public record Triangle(Point a, Point b, Point c, Vec normal) {
        public Triangle add(Point pos) {
            return new Triangle(this.a.add(pos), this.b.add(pos), this.c.add(pos), this.normal);
        }
    }

    /**
     * Get how long the last {@link Trigger#compute()} or {@link Trigger#recompute()} call took in milliseconds.
     * @return How long the last {@link Trigger#compute()} or {@link Trigger#recompute()} call took in milliseconds, returns zero if there was no last call.
     */
    public double getLastComputationTime() {
        return lastComputationTime;
    }

    /**
     * Get the base position of the hull.
     * @return The base position of the hull.
     */
    public Vec getPosition() {
        return position;
    }

    /**
     * Set a new base position for the hull.
     * Basically teleportation.
     * @param newPos The new hull position.
     */
    public void setPosition(Pos newPos) {
        position = newPos.asVec();
    }

    /**
     * Get the {@link UUID} of the trigger. Usually randomly assigned at creation of the trigger.
     * @return The {@link UUID} of the trigger.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Get all computed triangles of the hull.
     * @return The {@link List} of {@link Triangle Triangles} of the hull.
     */
    public List<Triangle> getTriangles() {
        return triangles;
    }

    /**
     * Replace the current hull triangles.
     * @param triangles The new hull triangles.
     */
    public void setTriangles(List<Triangle> triangles) {
        this.triangles = triangles;
    }

    /**
     * Get the current callback {@link Consumer} of this trigger.
     * @return The {@link TriggeredCallback} {@link Consumer} of this {@link Trigger}.
     */
    public Consumer<TriggeredCallback> getTriggeredCallback() {
        return triggeredCallback;
    }

    /**
     * Set a new callback {@link Consumer} for this trigger.
     * @param triggeredCallback The new {@link TriggeredCallback} {@link Consumer} of this {@link Trigger}.
     */
    public void setTriggeredCallback(Consumer<TriggeredCallback> triggeredCallback) {
        this.triggeredCallback = triggeredCallback;
    }

    /**
     * Serialize this triggers' {@link Trigger#anchors}, {@link Trigger#name} and {@link Trigger#position} to a JSON object that can be deserialized using {@link Trigger#fromJSON(String)}.
     * @return The serialized JSON String that contains the important data of this trigger.
     */
    public String toJSON() {
        return TriggerGlobals.GSON.toJson(this);
    }

    /**
     * Deserialize a JSON String that was serialized with {@link Trigger#toJSON()}.
     * <p>
     * THIS NEEDS TO BE ADDED IN THE RESPECTIVE {@link TriggerManager#getTriggers()} IN ORDER TO WORK PROPERLY
     * @param json The JSON String to deserialize
     * @return The new trigger from JSON
     */
    public static Trigger fromJSON(String json) {
        return TriggerGlobals.GSON.fromJson(json, Trigger.class);
    }

    /**
     * Get the debug rendering color.
     * @return The {@link RGBLike} debug rendering color.
     */
    public RGBLike getColor() {
        return color;
    }

    /**
     * Set the debug rendering color.
     * @param color The new {@link RGBLike} debug rendering color.
     */
    public void setColor(RGBLike color) {
        this.color = color;
    }

    /**
     * Get the distance check radius.
     * @return The distance check radius.
     */
    public double getCheckRadius() {
        return checkRadius;
    }

    /**
     * Get the {@link Component} that represents the display name of the trigger.
     * @return The {@link Component} that represents the display name of the trigger.
     */
    public Component getName() {
        return name;
    }

    /**
     * Get the base anchors of the hull.
     * @return The base anchors of the hull.
     */
    public List<Vec> getAnchors() {
        return anchors;
    }
}

