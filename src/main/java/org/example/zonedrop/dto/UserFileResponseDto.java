package org.example.zonedrop.dto;

public record UserFileResponseDto(
    String downloadUrl,
    String fileName,
    Long fileSize
) {
}
