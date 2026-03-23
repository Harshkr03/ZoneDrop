package org.example.zonedrop.dto;

import java.time.LocalDateTime;

public record NearbyFileResponseDto(
    Long fileId,
    Long userId,
    String userName,
    String fileName,
    Long fileSize,
    String downloadUrl,
    Double latitude,
    Double longitude,
    Double radiusKm,
    LocalDateTime expiresAt,
    Double distanceKm
) {
}
