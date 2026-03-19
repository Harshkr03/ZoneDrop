package org.example.zonedrop.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final long TOKEN_TTL_SECONDS = 24 * 60 * 60;
    private static final String JWT_HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secretBytes;

    public AuthUtil(@Value("${jwt.secretKey}") String secretKey) {
        this.secretBytes = normalizeSecret(secretKey).getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(UserDetails userDetails) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + TOKEN_TTL_SECONDS;
        String payloadJson = "{\"sub\":\"" + escapeJson(userDetails.getUsername()) + "\",\"iat\":" + issuedAt
            + ",\"exp\":" + expiresAt + "}";

        try {
            String encodedHeader = encode(JWT_HEADER_JSON);
            String encodedPayload = encode(payloadJson);
            String unsignedToken = encodedHeader + "." + encodedPayload;

            return unsignedToken + "." + sign(unsignedToken);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate JWT", exception);
        }
    }

    public String extractUsername(String token) {
        String payloadJson = parseAndValidate(token);
        return extractStringClaim(payloadJson, "sub");
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String payloadJson = parseAndValidate(token);
        String subject = extractStringClaim(payloadJson, "sub");
        Long expiresAt = extractLongClaim(payloadJson, "exp");
        if (subject == null || expiresAt == null) {
            return false;
        }

        return userDetails.getUsername().equals(subject) && expiresAt > Instant.now().getEpochSecond();
    }

    private String parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            String unsignedToken = parts[0] + "." + parts[1];
            String expectedSignature = sign(unsignedToken);
            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            return new String(payloadBytes, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT token", exception);
        }
    }

    private String encode(String value) {
        return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String extractStringClaim(String payloadJson, String claimName) {
        String pattern = "\"" + claimName + "\":\"";
        int start = payloadJson.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = payloadJson.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return payloadJson.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private Long extractLongClaim(String payloadJson, String claimName) {
        String pattern = "\"" + claimName + "\":";
        int start = payloadJson.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = start;
        while (end < payloadJson.length() && Character.isDigit(payloadJson.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return Long.parseLong(payloadJson.substring(start, end));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeSecret(String secretKey) {
        String normalized = secretKey == null ? "" : secretKey.trim();
        if (normalized.startsWith("'") && normalized.endsWith("'") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("jwt.secretKey must not be empty");
        }
        return normalized;
    }
}
