package org.example.zonedrop.repositories;

import java.util.Optional;
import org.example.zonedrop.entity.User;
import org.example.zonedrop.entity.UserLiveLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLiveLocationRepository extends JpaRepository<UserLiveLocation, Long> {

    Optional<UserLiveLocation> findByUser(User user);
}
