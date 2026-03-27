package org.example.zonedrop.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.NearbySearchRequestDto;
import org.example.zonedrop.dto.NearbyUserResponseDto;
import org.example.zonedrop.dto.UpdateUserLiveLocationRequestDto;
import org.example.zonedrop.dto.UserLiveLocationResponseDto;
import org.example.zonedrop.services.LocationBroadcastService;
import org.example.zonedrop.services.UserService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final UserService userService;
    private final LocationBroadcastService locationBroadcastService;

    /**
     * Client → /app/location.update
     * Body: { "userId": 1, "latitude": 40.7128, "longitude": -74.0060 }
     * Persists to PostgreSQL + Redis, then broadcasts to /topic/location/{id},
     * /topic/locations/all, and /topic/nearby/{id}.
     */
    @MessageMapping("/location.update")
    public void handleLocationUpdate(UpdateUserLiveLocationRequestDto request) {
        UserLiveLocationResponseDto updated = userService.updateUserLiveLocation(request);
        locationBroadcastService.broadcastLocationUpdate(updated);
    }

    /**
     * Client → /app/nearby.search
     * Body: { "userId": 1, "radiusKm": 5.0 }
     * Looks up the user's stored location, queries Redis for nearby users,
     * and pushes the result to /topic/nearby/{userId}.
     */
    @MessageMapping("/nearby.search")
    public void handleNearbySearch(NearbySearchRequestDto request) {
        try {
            List<NearbyUserResponseDto> nearby = userService.findUsersNearUser(
                request.userId(), request.radiusKm());
            locationBroadcastService.broadcastNearbySearch(request.userId(), nearby);
        } catch (Exception ignored) {}
    }
}
