package org.example.zonedrop.dto;

import java.time.LocalDateTime;
import lombok.*;

@Builder
public record FileResponseDto(
    Long id,
    Long userId,
    String fileName,
    String storageKey,
    String mimeType,
    Long sizeBytes,
    Double latitude,
    Double longitude,
    Double radiusKm,
    LocalDateTime expiresAt
) {
}
