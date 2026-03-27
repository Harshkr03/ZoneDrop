package org.example.zonedrop.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.CreateFileRequestDto;
import org.example.zonedrop.dto.FileCatalogResponseDto;
import org.example.zonedrop.dto.FileResponseDto;
import org.example.zonedrop.dto.NearbyFileResponseDto;
import org.example.zonedrop.dto.UserFileResponseDto;
import org.example.zonedrop.entity.File;
import org.example.zonedrop.entity.User;
import org.example.zonedrop.repositories.FileRepository;
import org.example.zonedrop.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService supabaseStorageService;

    public FileResponseDto uploadFile(CreateFileRequestDto request) {
        if (request.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        if (request.getFile() == null || request.getFile().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude are required");
        }
        if (request.getRadiusKm() == null || request.getRadiusKm() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be a positive number");
        }
        if (request.getTtlSeconds() == null || request.getTtlSeconds() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ttlSeconds must be a positive number");
        }

        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String fileName = resolveFileName(request);
        String mimeType = resolveMimeType(request);
        long sizeBytes = request.getFile().getSize();
        String storageKey = buildStorageKey(user.getId(), fileName);
        LocalDateTime now = LocalDateTime.now();

        try {
            supabaseStorageService.upload(storageKey, request.getFile().getBytes(), mimeType);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read uploaded file", exception);
        }

        try {
            File file = File.builder()
                .user(user)
                .fileName(fileName)
                .storageKey(storageKey)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusKm(request.getRadiusKm())
                .uploadedAt(now)
                .expiresAt(now.plusSeconds(request.getTtlSeconds()))
                .build();

            return toFileResponse(fileRepository.save(file));
        } catch (RuntimeException exception) {
            try {
                supabaseStorageService.delete(storageKey);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup if database persistence fails after upload.
            }
            throw exception;
        }
    }

    public List<UserFileResponseDto> getFilesByUser(Long userId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return fileRepository.findByUserIdAndExpiresAtAfter(userId, LocalDateTime.now())
            .stream()
            .map(this::toUserFileResponse)
            .toList();
    }

    public List<FileCatalogResponseDto> getAllFiles() {
        return fileRepository.findByExpiresAtAfter(LocalDateTime.now())
            .stream()
            .map(this::toFileCatalogResponse)
            .toList();
    }

    public List<NearbyFileResponseDto> getFilesNearLocation(Double userLat, Double userLng) {
        return fileRepository.findByExpiresAtAfter(LocalDateTime.now())
            .stream()
            .filter(file -> {
                double distance = haversineKm(userLat, userLng, file.getLatitude(), file.getLongitude());
                return distance <= file.getRadiusKm();
            })
            .map(file -> toNearbyFileResponse(file, userLat, userLng))
            .toList();
    }

    private FileResponseDto toFileResponse(File file) {
        return FileResponseDto.builder()
            .id(file.getId())
            .userId(file.getUser().getId())
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

    private UserFileResponseDto toUserFileResponse(File file) {
        return new UserFileResponseDto(
            supabaseStorageService.getPublicUrl(file.getStorageKey()),
            file.getFileName(),
            file.getSizeBytes()
        );
    }

    private FileCatalogResponseDto toFileCatalogResponse(File file) {
        return new FileCatalogResponseDto(
            file.getUser().getId(),
            file.getUser().getName(),
            file.getFileName(),
            file.getSizeBytes(),
            supabaseStorageService.getPublicUrl(file.getStorageKey()),
            file.getLatitude(),
            file.getLongitude(),
            file.getRadiusKm(),
            file.getExpiresAt()
        );
    }

    private NearbyFileResponseDto toNearbyFileResponse(File file, Double userLat, Double userLng) {
        double distanceKm = haversineKm(userLat, userLng, file.getLatitude(), file.getLongitude());
        return new NearbyFileResponseDto(
            file.getId(),
            file.getUser().getId(),
            file.getUser().getName(),
            file.getFileName(),
            file.getSizeBytes(),
            supabaseStorageService.getPublicUrl(file.getStorageKey()),
            file.getLatitude(),
            file.getLongitude(),
            file.getRadiusKm(),
            file.getExpiresAt(),
            Math.round(distanceKm * 100.0) / 100.0
        );
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String resolveFileName(CreateFileRequestDto request) {
        String originalFilename = request.getFile().getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.bin";
        }
        return originalFilename;
    }

    private String resolveMimeType(CreateFileRequestDto request) {
        String contentType = request.getFile().getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String buildStorageKey(Long userId, String fileName) {
        return "users/" + userId + "/" + UUID.randomUUID() + "-" + sanitizeFileName(fileName);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
