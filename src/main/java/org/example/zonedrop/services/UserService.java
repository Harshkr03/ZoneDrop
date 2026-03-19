package org.example.zonedrop.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.CreateUserRequestDto;
import org.example.zonedrop.dto.NearbyUserResponseDto;
import org.example.zonedrop.dto.UpdateUserLiveLocationRequestDto;
import org.example.zonedrop.dto.UserLocationResponseDto;
import org.example.zonedrop.dto.UserLiveLocationResponseDto;
import org.example.zonedrop.dto.UserResponseDto;
import org.example.zonedrop.entity.User;
import org.example.zonedrop.entity.UserLiveLocation;
import org.example.zonedrop.repositories.UserLiveLocationRepository;
import org.example.zonedrop.repositories.UserRepository;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String USER_LIVE_LOCATION_GEO_KEY = "zonedrop:user-live-locations";

    private final UserRepository userRepository;
    private final UserLiveLocationRepository userLiveLocationRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public UserResponseDto createUser(CreateUserRequestDto request) {
        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .password(request.password())
            .build();

        return toUserResponse(userRepository.save(user));
    }

    public List<UserResponseDto> getUsers() {
        return userRepository.findAll()
            .stream()
            .map(this::toUserResponse)
            .toList();
    }

    public List<UserLocationResponseDto> getUsersWithLocations() {
        Map<Long, UserLiveLocation> liveLocationsByUserId = userLiveLocationRepository.findAll()
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                location -> location.getUser().getId(),
                location -> location
            ));

        return userRepository.findAll()
            .stream()
            .map(user -> toUserLocationResponse(user, liveLocationsByUserId.get(user.getId())))
            .toList();
    }

    public UserLiveLocationResponseDto updateUserLiveLocation(UpdateUserLiveLocationRequestDto request) {
        if (request.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        validateCoordinates(request.latitude(), request.longitude());

        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserLiveLocation userLiveLocation = userLiveLocationRepository.findByUser(user)
            .orElseGet(() -> UserLiveLocation.builder().user(user).build());

        userLiveLocation.setLatitude(request.latitude());
        userLiveLocation.setLongitude(request.longitude());

        UserLiveLocation savedLocation = userLiveLocationRepository.save(userLiveLocation);
        stringRedisTemplate.opsForGeo().add(
            USER_LIVE_LOCATION_GEO_KEY,
            new Point(request.longitude(), request.latitude()),
            request.userId().toString()
        );

        return toUserLiveLocationResponse(savedLocation);
    }

    public List<NearbyUserResponseDto> findUsersWithinRadius(Double latitude, Double longitude, Double radiusKm) {
        validateCoordinates(latitude, longitude);
        if (radiusKm == null || radiusKm <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be greater than 0");
        }

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
            USER_LIVE_LOCATION_GEO_KEY,
            new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS)),
            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
        );

        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        List<Long> userIds = new ArrayList<>();
        Map<Long, Double> distanceByUserId = new HashMap<>();

        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            Long userId = parseUserId(result.getContent().getName());
            if (userId == null) {
                continue;
            }
            userIds.add(userId);
            distanceByUserId.put(userId, result.getDistance() == null ? null : result.getDistance().getValue());
        }

        if (userIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = userRepository.findAllById(userIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(User::getId, user -> user));
        Map<Long, UserLiveLocation> liveLocationsByUserId = userLiveLocationRepository.findAll()
            .stream()
            .filter(location -> userIds.contains(location.getUser().getId()))
            .collect(java.util.stream.Collectors.toMap(
                location -> location.getUser().getId(),
                location -> location
            ));

        return userIds.stream()
            .map(usersById::get)
            .filter(java.util.Objects::nonNull)
            .map(user -> toNearbyUserResponse(user, liveLocationsByUserId.get(user.getId()), distanceByUserId.get(user.getId())))
            .sorted(Comparator.comparing(
                NearbyUserResponseDto::distanceKm,
                Comparator.nullsLast(Double::compareTo)
            ))
            .toList();
    }

    private UserResponseDto toUserResponse(User user) {
        return new UserResponseDto(user.getId(), user.getName(), user.getEmail());
    }

    private UserLiveLocationResponseDto toUserLiveLocationResponse(UserLiveLocation userLiveLocation) {
        return new UserLiveLocationResponseDto(
            userLiveLocation.getId(),
            userLiveLocation.getUser().getId(),
            userLiveLocation.getLatitude(),
            userLiveLocation.getLongitude()
        );
    }

    private NearbyUserResponseDto toNearbyUserResponse(User user, UserLiveLocation liveLocation, Double distanceKm) {
        return new NearbyUserResponseDto(
            user.getId(),
            user.getName(),
            user.getEmail(),
            liveLocation == null ? null : liveLocation.getLatitude(),
            liveLocation == null ? null : liveLocation.getLongitude(),
            distanceKm
        );
    }

    private UserLocationResponseDto toUserLocationResponse(User user, UserLiveLocation liveLocation) {
        return new UserLocationResponseDto(
            user.getId(),
            user.getName(),
            user.getEmail(),
            liveLocation == null ? null : liveLocation.getLatitude(),
            liveLocation == null ? null : liveLocation.getLongitude()
        );
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "longitude must be between -180 and 180");
        }
    }

    private Long parseUserId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
