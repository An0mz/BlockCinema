package dev.anomz.blockcinema.client.browser;

/**
 * How loud a screen is for the player. The players' own volume controls set it, so the sound itself is
 * left as the service sends it (YouTube's terms forbid separating or modifying a video's audio).
 */
public final class TvVolume {
    static final double FULL_DISTANCE = 4;
    static final double SILENT_DISTANCE = 48;
    static final double MAX_PAN = 0.8;

    private TvVolume() {
    }

    public static float falloff(double distance) {
        if (distance <= FULL_DISTANCE) {
            return 1;
        }
        if (distance >= SILENT_DISTANCE) {
            return 0;
        }
        return (float) (1 - (distance - FULL_DISTANCE) / (SILENT_DISTANCE - FULL_DISTANCE));
    }

    /** From -1 (left) to 1 (right): where a listener turned {@code yaw} degrees hears a sound {@code dx}, {@code dz} blocks away. */
    public static float pan(double dx, double dz, float yaw) {
        double distance = Math.hypot(dx, dz);
        if (distance < 1e-6) {
            return 0;
        }
        double radians = Math.toRadians(yaw);
        double right = -(dx * Math.cos(radians) + dz * Math.sin(radians)) / distance;
        return (float) (right * MAX_PAN * Math.min(1, distance / FULL_DISTANCE));
    }

    public static double distanceToBox(double x, double y, double z, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - x, 0), x - maxX);
        double dy = Math.max(Math.max(minY - y, 0), y - maxY);
        double dz = Math.max(Math.max(minZ - z, 0), z - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
