package org.example.zonedrop.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.CreateUserRequestDto;
import org.example.zonedrop.dto.NearbyUserResponseDto;
import org.example.zonedrop.dto.UpdateUserLiveLocationRequestDto;
import org.example.zonedrop.dto.UserLocationResponseDto;
import org.example.zonedrop.dto.UserLiveLocationResponseDto;
import org.example.zonedrop.dto.UserResponseDto;
import org.example.zonedrop.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/zonedrop/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponseDto> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/locations")
    public List<UserLocationResponseDto> getUsersWithLocations() {
        return userService.getUsersWithLocations();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto createUser(@RequestBody CreateUserRequestDto request) {
        return userService.createUser(request);
    }

    @GetMapping("/nearby")
    public List<NearbyUserResponseDto> findUsersWithinRadius(
        @RequestParam Double latitude,
        @RequestParam Double longitude,
        @RequestParam Double radiusKm
    ) {
        return userService.findUsersWithinRadius(latitude, longitude, radiusKm);
    }

    @PutMapping("/live-location")
    public UserLiveLocationResponseDto updateUserLiveLocation(@RequestBody UpdateUserLiveLocationRequestDto request) {
        return userService.updateUserLiveLocation(request);
    }
}
