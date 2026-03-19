package org.example.zonedrop.dto;

public record UserLocationResponseDto(
    Long userId,
    String name,
    String email,
    Double latitude,
    Double longitude
) {
}
