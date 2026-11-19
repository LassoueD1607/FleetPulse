package com.fleetpulse.processing.rules;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeofenceService {

    // Polygon vertices as [longitude, latitude] pairs: x = longitude, y = latitude.
    private final double[][] polygon;

    public GeofenceService() throws IOException {
        try (InputStream in = new ClassPathResource("tunisia.geojson").getInputStream()) {
            JsonNode ring = new JsonMapper().readTree(in).get("coordinates").get(0);
            polygon = new double[ring.size()][2];
            for (int i = 0; i < ring.size(); i++) {
                polygon[i][0] = ring.get(i).get(0).asDouble();
                polygon[i][1] = ring.get(i).get(1).asDouble();
            }
        }
    }

    // Ray casting: a horizontal ray from the point crosses the polygon border an odd number of
    // times if the point is inside, and an even number of times if it is outside.
    public boolean isInsideTunisia(double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double xi = polygon[i][0], yi = polygon[i][1];
            double xj = polygon[j][0], yj = polygon[j][1];
            boolean edgeCrossesLatitude = (yi > lat) != (yj > lat);
            if (edgeCrossesLatitude && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
