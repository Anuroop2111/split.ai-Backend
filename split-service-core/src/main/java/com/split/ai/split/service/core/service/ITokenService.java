package com.split.ai.split.service.core.service;

import java.util.Map;
import java.util.UUID;

public interface ITokenService {

    String generateAccessToken(UUID userId, UUID sessionId, int tokenVersion);

    String generateRefreshToken(UUID refreshTokenId, UUID userId, UUID sessionId, int tokenVersion);

    Map<String, Object> parseAccessToken(String token);

    Map<String, Object> parseRefreshToken(String token);

    String hashToken(String token);
}

