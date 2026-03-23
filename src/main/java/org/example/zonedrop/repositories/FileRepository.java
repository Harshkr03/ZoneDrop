package org.example.zonedrop.repositories;

import java.time.LocalDateTime;
import java.util.List;
import org.example.zonedrop.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByUserId(Long userId);

    List<File> findByExpiresAtAfter(LocalDateTime now);

    List<File> findByUserIdAndExpiresAtAfter(Long userId, LocalDateTime now);

    List<File> findByExpiresAtBefore(LocalDateTime now);
}
