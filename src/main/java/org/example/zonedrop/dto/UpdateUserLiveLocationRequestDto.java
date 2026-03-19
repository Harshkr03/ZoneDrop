package org.example.zonedrop.dto;

public record UpdateUserLiveLocationRequestDto(Long userId, Double latitude, Double longitude) {
}
