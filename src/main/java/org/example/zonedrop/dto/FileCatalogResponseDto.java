package org.example.zonedrop.dto;

public record FileCatalogResponseDto(
    Long userId,
    String userName,
    String fileName,
    Long fileSize,
    String downloadUrl
) {
}
