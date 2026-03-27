package org.example.zonedrop.services;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.NearbyUserResponseDto;
import org.example.zonedrop.dto.UserLiveLocationResponseDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationBroadcastService {

    private static final double DEFAULT_NEARBY_RADIUS_KM = 5.0;

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    /**
     * Called after every location update. Broadcasts to three channels:
     *
     * <ul>
     *   <li>/topic/location/{userId}  – clients watching a specific user's pin (e.g. passenger
     *       watching their driver)</li>
     *   <li>/topic/locations/all      – clients showing every active user on a map</li>
     *   <li>/topic/nearby/{userId}    – the moving user's own live nearby-user list</li>
     * </ul>
     */
    public void broadcastLocationUpdate(UserLiveLocationResponseDto location) {
        messagingTemplate.convertAndSend("/topic/location/" + location.userId(), location);
        messagingTemplate.convertAndSend("/topic/locations/all", location);

        try {
            List<NearbyUserResponseDto> nearby = userService.findUsersWithinRadius(
                location.latitude(), location.longitude(), DEFAULT_NEARBY_RADIUS_KM
            );
            messagingTemplate.convertAndSend("/topic/nearby/" + location.userId(), nearby);
        } catch (Exception ignored) {}
    }

    public void broadcastNearbySearch(Long userId, List<NearbyUserResponseDto> nearby) {
        messagingTemplate.convertAndSend("/topic/nearby/" + userId, nearby);
    }
}
