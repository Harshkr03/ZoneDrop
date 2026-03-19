package org.example.zonedrop.repositories;

import java.util.List;
import org.example.zonedrop.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByUserId(Long userId);
}
