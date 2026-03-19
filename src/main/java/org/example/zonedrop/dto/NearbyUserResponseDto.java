package org.example.zonedrop.dto;

public record NearbyUserResponseDto(
    Long userId,
    String name,
    String email,
    Double latitude,
    Double longitude,
    Double distanceKm
) {
}
