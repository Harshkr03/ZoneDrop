package org.example.zonedrop.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.CreateFileRequestDto;
import org.example.zonedrop.dto.FileCatalogResponseDto;
import org.example.zonedrop.dto.FileResponseDto;
import org.example.zonedrop.dto.UserFileResponseDto;
import org.example.zonedrop.services.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/zonedrop/files")
public class FileController {

    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponseDto uploadFile(@ModelAttribute CreateFileRequestDto request) {
        return fileService.uploadFile(request);
    }

    @GetMapping
    public List<FileCatalogResponseDto> getAllFiles() {
        return fileService.getAllFiles();
    }

    @GetMapping("/{userId}")
    public List<UserFileResponseDto> getFilesByUser(@PathVariable Long userId) {
        return fileService.getFilesByUser(userId);
    }

}
