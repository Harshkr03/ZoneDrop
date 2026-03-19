package org.example.zonedrop.services;

import lombok.RequiredArgsConstructor;
import org.example.zonedrop.dto.AuthResponseDto;
import org.example.zonedrop.dto.LoginRequestDto;
import org.example.zonedrop.dto.SignupRequestDto;
import org.example.zonedrop.dto.UserResponseDto;
import org.example.zonedrop.entity.User;
import org.example.zonedrop.repositories.UserRepository;
import org.example.zonedrop.security.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuthUtil authUtil;

    public AuthResponseDto signup(SignupRequestDto request) {
        validateSignupRequest(request);

        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
            .name(request.name().trim())
            .email(request.email().trim().toLowerCase())
            .password(passwordEncoder.encode(request.password()))
            .build();

        User savedUser = userRepository.save(user);
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(savedUser.getEmail())
            .password(savedUser.getPassword())
            .roles("USER")
            .build();

        return new AuthResponseDto(authUtil.generateToken(userDetails), toUserResponse(savedUser));
    }

    public AuthResponseDto login(LoginRequestDto request) {
        validateLoginRequest(request);

        String email = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, request.password())
            );
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .roles("USER")
            .build();

        return new AuthResponseDto(authUtil.generateToken(userDetails), toUserResponse(user));
    }

    private UserResponseDto toUserResponse(User user) {
        return new UserResponseDto(user.getId(), user.getName(), user.getEmail());
    }

    private void validateSignupRequest(SignupRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
    }

    private void validateLoginRequest(LoginRequestDto request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
    }
}
