package com.civicsphere.util;

import java.util.Locale;

/**
 * Location normalization and Haversine distance calculations for recurrence detection.
 */
public final class LocationUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private LocationUtil() {
    }

    /**
     * Normalizes location text: trims, lowercases, strips punctuation, collapses whitespace.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    /**
     * Computes geographical distance in meters using the Haversine formula.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static final class LocationMatchResult {
        private final int score;
        private final String reason;

        public LocationMatchResult(int score, String reason) {
            this.score = score;
            this.reason = reason;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Computes up to 40 points for location proximity/match.
     */
    public static LocationMatchResult evaluateLocationMatch(
            String area1, String city1, String state1, String landmark1, Double lat1, Double lon1,
            String area2, String city2, String state2, String landmark2, Double lat2, Double lon2,
            double maxDistanceMeters) {

        if (lat1 != null && lon1 != null && lat2 != null && lon2 != null) {
            double meters = haversineMeters(lat1, lon1, lat2, lon2);
            if (meters <= maxDistanceMeters) {
                return new LocationMatchResult(
                        40,
                        String.format(Locale.ROOT, "GPS coordinates within %.1f m (threshold %.0f m)", meters, maxDistanceMeters)
                );
            }
            return new LocationMatchResult(
                    0,
                    String.format(Locale.ROOT, "GPS coordinates %.1f m apart (exceeds %.0f m threshold)", meters, maxDistanceMeters)
            );
        }

        String nArea1 = normalize(area1);
        String nArea2 = normalize(area2);
        String nCity1 = normalize(city1);
        String nCity2 = normalize(city2);
        String nState1 = normalize(state1);
        String nState2 = normalize(state2);
        String nLand1 = normalize(landmark1);
        String nLand2 = normalize(landmark2);

        boolean stateMatches = nState1.isEmpty() || nState2.isEmpty() || nState1.equals(nState2);
        boolean cityMatches = !nCity1.isEmpty() && nCity1.equals(nCity2);
        boolean areaMatches = !nArea1.isEmpty() && (nArea1.equals(nArea2) || nArea1.contains(nArea2) || nArea2.contains(nArea1));

        if (!stateMatches || !cityMatches) {
            return new LocationMatchResult(0, "Different city/state");
        }

        if (areaMatches) {
            if (!nLand1.isEmpty() && !nLand2.isEmpty()) {
                if (nLand1.equals(nLand2) || nLand1.contains(nLand2) || nLand2.contains(nLand1)) {
                    return new LocationMatchResult(40, "Exact area, city, and landmark match");
                }
                double landmarkSim = TextSimilarityUtil.jaccardSimilarity(nLand1, nLand2);
                if (landmarkSim >= 0.35) {
                    return new LocationMatchResult(40, "Same area/city with matching landmark keywords");
                }
                return new LocationMatchResult(30, "Same area and city (different landmark)");
            }
            if (nLand1.isEmpty() && nLand2.isEmpty()) {
                return new LocationMatchResult(40, "Same area and city");
            }
            return new LocationMatchResult(35, "Same area and city (landmark provided on one report)");
        }

        return new LocationMatchResult(10, "Same city only (different area)");
    }
}

