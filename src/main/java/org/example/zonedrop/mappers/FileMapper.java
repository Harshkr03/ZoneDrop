package org.example.zonedrop.mappers;

import org.example.zonedrop.dto.FileResponseDto;
import org.example.zonedrop.entity.File;

public class FileMapper {

    public static FileResponseDto toDto(File file) {
        if (file == null) return null;

        return FileResponseDto.builder()
                .id(file.getId())
                .userId(file.getUser() != null ? file.getUser().getId() : null)
                .fileName(file.getFileName())
                .storageKey(file.getStorageKey())
                .mimeType(file.getMimeType())
                .sizeBytes(file.getSizeBytes())
                .latitude(file.getLatitude())
                .longitude(file.getLongitude())
                .radiusKm(file.getRadiusKm())
                .expiresAt(file.getExpiresAt())
                .build();
    } 
}
