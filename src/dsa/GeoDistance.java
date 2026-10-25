package dsa;

import model.Center;
import java.util.*;

/**
 * GeoDistance Algorithm Engine.
 * Implements the Haversine Great-Circle Distance Formula to calculate
 * real-world spherical distances (in kilometers) between two GPS coordinates.
 * 
 * Formula:
 *   a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlng/2)
 *   c = 2 * atan2(√a, √(1-a))
 *   d = R * c (where R = 6371 km)
 */
public class GeoDistance {
    private static final double EARTH_RADIUS_KM = 6371.0;

    public static class CenterDistance {
        public Center center;
        public double distanceKm;

        public CenterDistance(Center center, double distanceKm) {
            this.center = center;
            this.distanceKm = Math.round(distanceKm * 10.0) / 10.0; // 1 decimal place
        }

        public String toJson() {
            return String.format(
                "{\"center\":%s,\"distanceKm\":%.1f}",
                center.toJson(), distanceKm
            );
        }
    }

    /**
     * Calculates distance in kilometers between two GPS points using Haversine formula.
     */
    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(rLat1) * Math.cos(rLat2) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Finds nearest centers from a given user coordinate, sorted ascending by distance.
     */
    public static List<CenterDistance> findNearestCenters(Collection<Center> centers, double userLat, double userLng) {
        List<CenterDistance> list = new ArrayList<>();
        for (Center c : centers) {
            double dist = haversine(userLat, userLng, c.getLatitude(), c.getLongitude());
            list.add(new CenterDistance(c, dist));
        }

        // Sort ascending by distance (Nearest first)
        list.sort(Comparator.comparingDouble(cd -> cd.distanceKm));
        return list;
    }
}
