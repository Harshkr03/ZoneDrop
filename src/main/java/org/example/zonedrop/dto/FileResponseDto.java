package org.example.zonedrop.dto;

import lombok.*;

@Builder
public record FileResponseDto(
    Long id,
    Long userId,
    String fileName,
    String storageKey,
    String mimeType,
    Long sizeBytes
) {
}
