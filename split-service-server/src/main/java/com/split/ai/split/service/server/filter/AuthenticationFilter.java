package com.split.ai.split.service.server.filter;

import com.split.ai.split.service.core.service.ITokenService;
import com.split.ai.split.service.repository.dao.IRefreshTokenDao;
import com.split.ai.split.service.repository.dao.ISessionDao;
import com.split.ai.split.service.repository.dao.IUserDao;
import com.split.ai.split.service.repository.entity.RefreshTokenEntity;
import com.split.ai.split.service.repository.entity.SessionEntity;
import com.split.ai.split.service.repository.entity.UserEntity;
import com.split.ai.split.service.model.enums.USER_STATUS;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final ITokenService tokenService;
    private final IRefreshTokenDao refreshTokenDao;
    private final ISessionDao sessionDao;
    private final IUserDao userDao;

    @Value("${security.auth.session-max-age}")
    private long sessionMaxAge;

    @Value("${security.auth.access-token-refresh-before}")
    private long accessRefreshBefore;

    @Value("${security.auth.refresh-token-refresh-before}")
    private long refreshRefreshBefore;

    @Value("${security.auth.refresh-token-ttl}")
    private long refreshTtl;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = getCookie(request, "AccessToken");
        String refreshToken = getCookie(request, "RefreshToken");

        boolean authenticated = false;
        if (accessToken != null) {
            try {
                Map<String, Object> claims = tokenService.parseAccessToken(accessToken);
                UUID userId = UUID.fromString((String) claims.get("sub"));
                UUID sessionId = UUID.fromString((String) claims.get("sid"));
                int tokenVersion = (Integer) claims.get("tv");
                long exp = ((Number) claims.get("exp")).longValue();
                long now = System.currentTimeMillis();

                SessionEntity session = sessionDao.findById(sessionId).orElse(null);
                UserEntity user = userDao.findById(userId);

                if (session != null && session.getRevokedAt() == null && user != null
                        && user.getUserStatus() != USER_STATUS.BLOCKED && user.getTokenVersion() == tokenVersion
                        && session.getCreatedAt() + sessionMaxAge > now) {
                    session.setLastUsedAt(now);
                    sessionDao.update(session);
                    authenticated = true;
                    if (refreshToken != null) {
                        try {
                            Map<String, Object> rtClaims = tokenService.parseRefreshToken(refreshToken);
                            long rExp = ((Number) rtClaims.get("exp")).longValue();
                            if ((exp - now) <= accessRefreshBefore || (rExp - now) <= refreshRefreshBefore) {
                                rotateTokens(refreshToken, user, session, response);
                            }
                        } catch (Exception e) {
                            // ignore and try refresh
                        }
                    } else if ((exp - now) <= accessRefreshBefore && refreshToken != null) {
                        rotateTokens(refreshToken, user, session, response);
                    }
                }
            } catch (Exception e) {
                log.debug("[AuthenticationFilter] access token invalid, attempting refresh");
            }
        }

        if (!authenticated && refreshToken != null) {
            try {
                Map<String, Object> rtClaims = tokenService.parseRefreshToken(refreshToken);
                UUID userId = UUID.fromString((String) rtClaims.get("sub"));
                UUID sessionId = UUID.fromString((String) rtClaims.get("sid"));
                UserEntity user = userDao.findById(userId);
                SessionEntity session = sessionDao.findById(sessionId).orElse(null);
                rotateTokens(refreshToken, user, session, response);
                authenticated = true;
            } catch (Exception ex) {
                clearCookies(response);
            }
        }

        if (!authenticated && refreshToken == null && accessToken != null) {
            clearCookies(response);
        }

        filterChain.doFilter(request, response);
    }

    private void rotateTokens(String refreshToken, UserEntity user, SessionEntity session, HttpServletResponse response) {
        if (user == null || session == null) {
            clearCookies(response);
            return;
        }
        try {
            Map<String, Object> rtClaims = tokenService.parseRefreshToken(refreshToken);
            UUID tokenId = UUID.fromString((String) rtClaims.get("jti"));
            long now = System.currentTimeMillis();
            RefreshTokenEntity existing = refreshTokenDao.findById(tokenId).orElse(null);
            if (existing == null || existing.getRevokedAt() != null
                    || !existing.getTokenHash().equals(tokenService.hashToken(refreshToken))) {
                clearCookies(response);
                return;
            }
            UUID newRefreshId = UUID.randomUUID();
            String newRefresh = tokenService.generateRefreshToken(newRefreshId, user.getUserId(), session.getSessionId(), user.getTokenVersion());
            RefreshTokenEntity newEntity = RefreshTokenEntity.builder()
                    .refreshTokenId(newRefreshId)
                    .userId(user.getUserId())
                    .sessionId(session.getSessionId())
                    .tokenHash(tokenService.hashToken(newRefresh))
                    .issuedAt(now)
                    .expiresAt(now + refreshTtl)
                    .build();
            refreshTokenDao.save(newEntity);
            existing.setRevokedAt(now);
            existing.setReplacedBy(newRefreshId);
            refreshTokenDao.update(existing);

            String newAccess = tokenService.generateAccessToken(user.getUserId(), session.getSessionId(), user.getTokenVersion());
            setCookies(response, newAccess, newRefresh);
        } catch (Exception e) {
            clearCookies(response);
        }
    }

    private void setCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", accessToken)
                .httpOnly(true).path("/").build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", refreshToken)
                .httpOnly(true).path("/").build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", "").path("/").maxAge(0).build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", "").path("/").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
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
}

