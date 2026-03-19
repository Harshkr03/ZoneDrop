package org.example.zonedrop.services;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@Service
public class SupabaseStorageService {

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String bucketName;
    private final String serviceKey;

    public SupabaseStorageService(
        @Value("${supabase.url}") String supabaseUrl,
        @Value("${supabase.serviceKey}") String serviceKey,
        @Value("${supabase.bucket}") String bucketName
    ) {
        this.supabaseUrl = normalizeBaseUrl(supabaseUrl);
        this.bucketName = requireValue(bucketName, "supabase.bucket");
        this.serviceKey = requireValue(serviceKey, "supabase.serviceKey");
        this.restClient = RestClient.builder()
            .baseUrl(this.supabaseUrl)
            .defaultHeader("apikey", this.serviceKey)
            .defaultHeader("Authorization", "Bearer " + this.serviceKey)
            .build();
    }

    public String upload(String storageKey, byte[] content, String contentType) {
        String normalizedStorageKey = requireValue(storageKey, "storageKey");
        byte[] payload = content == null ? new byte[0] : content;

        try {
            restClient.post()
                .uri(buildObjectPath(normalizedStorageKey))
                .contentType(resolveMediaType(contentType))
                .header("x-upsert", "true")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            return normalizedStorageKey;
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                exception.getStatusCode(),
                "Supabase upload failed: " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Supabase upload failed",
                exception
            );
        }
    }

    public void delete(String storageKey) {
        String normalizedStorageKey = requireValue(storageKey, "storageKey");

        try {
            restClient.delete()
                .uri(buildObjectPath(normalizedStorageKey))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                exception.getStatusCode(),
                "Supabase delete failed: " + exception.getResponseBodyAsString(),
                exception
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Supabase delete failed",
                exception
            );
        }
    }

    public String getPublicUrl(String storageKey) {
        String normalizedStorageKey = requireValue(storageKey, "storageKey");
        return supabaseUrl + "/storage/v1/object/public/"
            + encodePathSegment(bucketName) + "/"
            + encodePath(normalizedStorageKey);
    }

    private String buildObjectPath(String storageKey) {
        return "/storage/v1/object/" + encodePathSegment(bucketName) + "/" + encodePath(storageKey);
    }

    private MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = requireValue(value, "supabase.url");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String requireValue(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(fieldName + " must be configured");
        }
        return value;
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return UriUtils.encodePath(value, StandardCharsets.UTF_8);
    }
}
