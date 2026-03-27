package org.example.zonedrop.services;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.entity.File;
import org.example.zonedrop.repositories.FileRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileExpiryScheduler {

    private final FileRepository fileRepository;
    private final SupabaseStorageService supabaseStorageService;

    @Scheduled(fixedDelay = 60_000)
    public void deleteExpiredFiles() {
        List<File> expired = fileRepository.findByExpiresAtBefore(LocalDateTime.now());
        for (File file : expired) {
            try {
                supabaseStorageService.delete(file.getStorageKey());
            } catch (RuntimeException ignored) {
                // Best-effort: remove from storage; DB record deleted regardless.
            }
            fileRepository.delete(file);
        }
    }
}
