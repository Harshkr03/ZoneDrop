package org.example.zonedrop.services;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.CreateFileRequestDto;
import org.example.zonedrop.dto.FileCatalogResponseDto;
import org.example.zonedrop.dto.FileResponseDto;
import org.example.zonedrop.dto.UserFileResponseDto;
import org.example.zonedrop.entity.File;
import org.example.zonedrop.entity.User;
import org.example.zonedrop.mappers.FileMapper;
import org.example.zonedrop.repositories.FileRepository;
import org.example.zonedrop.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FileService {

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

        // Validate if the user exists
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String fileName = resolveFileName(request);
        String mimeType = resolveMimeType(request);
        long sizeBytes = request.getFile().getSize();
        String storageKey = buildStorageKey(user.getId(), fileName);

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
                .build();

            return FileMapper.toDto(fileRepository.save(file));
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

        return fileRepository.findByUserId(userId)
            .stream()
            .map(this::toUserFileResponse)
            .toList();
    }

    public List<FileCatalogResponseDto> getAllFiles() {
        return fileRepository.findAll()
            .stream()
            .map(this::toFileCatalogResponse)
            .toList();
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
            supabaseStorageService.getPublicUrl(file.getStorageKey())
        );
    }
}
