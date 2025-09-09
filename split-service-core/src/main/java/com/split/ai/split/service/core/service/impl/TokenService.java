package com.split.ai.split.service.core.service.impl;

import com.split.ai.split.service.core.service.ITokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService implements ITokenService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.auth.access-token-secret}")
    private String accessSecret;

    @Value("${security.auth.refresh-token-secret}")
    private String refreshSecret;

    @Value("${security.auth.access-token-ttl}")
    private long accessTtl;

    @Value("${security.auth.refresh-token-ttl}")
    private long refreshTtl;

    private String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String encode(Map<String, Object> payload, String secret) {
        try {
            String headerJson = objectMapper.writeValueAsString(Map.of("alg", "HS256", "typ", "JWT"));
            String payloadJson = objectMapper.writeValueAsString(payload);
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(header + "." + body, secret);
            return header + "." + body + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> decode(String token, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token");
            }
            String sig = sign(parts[0] + "." + parts[1], secret);
            if (!sig.equals(parts[2])) {
                throw new IllegalArgumentException("Invalid token signature");
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(json, MAP_TYPE);
            long exp = ((Number) payload.get("exp")).longValue();
            if (exp < System.currentTimeMillis()) {
                throw new IllegalArgumentException("Token expired");
            }
            return payload;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String generateAccessToken(UUID userId, UUID sessionId, int tokenVersion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId.toString());
        payload.put("sid", sessionId.toString());
        payload.put("tv", tokenVersion);
        payload.put("exp", System.currentTimeMillis() + accessTtl);
        return encode(payload, accessSecret);
    }

    @Override
    public String generateRefreshToken(UUID refreshTokenId, UUID userId, UUID sessionId, int tokenVersion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jti", refreshTokenId.toString());
        payload.put("sub", userId.toString());
        payload.put("sid", sessionId.toString());
        payload.put("tv", tokenVersion);
        payload.put("exp", System.currentTimeMillis() + refreshTtl);
        return encode(payload, refreshSecret);
    }

    @Override
    public Map<String, Object> parseAccessToken(String token) {
        return decode(token, accessSecret);
    }

    @Override
    public Map<String, Object> parseRefreshToken(String token) {
        return decode(token, refreshSecret);
    }

    @Override
    public String hashToken(String token) {
        return sign(token, refreshSecret);
    }
}

