package com.split.ai.split.service.core.helper;

import com.split.ai.split.service.core.service.ITokenService;
import com.split.ai.split.service.repository.dao.IRefreshTokenDao;
import com.split.ai.split.service.repository.dao.ISessionDao;
import com.split.ai.split.service.repository.entity.RefreshTokenEntity;
import com.split.ai.split.service.repository.entity.SessionEntity;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenHelper {

    private final ISessionDao sessionDao;
    private final IRefreshTokenDao refreshTokenDao;
    private final ITokenService tokenService;

    @Value("${security.auth.refresh-token-ttl}")
    private long refreshTtl;

    public Tokens createTokens(UUID userId, int tokenVersion) {
        UUID sessionId = UUID.randomUUID();
        sessionDao.save(SessionEntity.builder().sessionId(sessionId).userId(userId).build());

        UUID refreshTokenId = UUID.randomUUID();
        String refreshToken = tokenService.generateRefreshToken(refreshTokenId, userId, sessionId, tokenVersion);
        long now = System.currentTimeMillis();
        RefreshTokenEntity refreshEntity = RefreshTokenEntity.builder()
                .refreshTokenId(refreshTokenId)
                .userId(userId)
                .sessionId(sessionId)
                .tokenHash(tokenService.hashToken(refreshToken))
                .issuedAt(now)
                .expiresAt(now + refreshTtl)
                .build();
        refreshTokenDao.save(refreshEntity);

        String accessToken = tokenService.generateAccessToken(userId, sessionId, tokenVersion);
        return new Tokens(accessToken, refreshToken);
    }

    public void setCookies(HttpServletResponse response, Tokens tokens) {
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", tokens.accessToken())
                .httpOnly(true).path("/").build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", tokens.refreshToken())
                .httpOnly(true).path("/").build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public void clearCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", "")
                .path("/").maxAge(0).build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", "")
                .path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public String getAccessToken(HttpServletRequest request) {
        return getCookie(request, "AccessToken");
    }

    public String getRefreshToken(HttpServletRequest request) {
        return getCookie(request, "RefreshToken");
    }

    private String getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public record Tokens(String accessToken, String refreshToken) {
    }
}

