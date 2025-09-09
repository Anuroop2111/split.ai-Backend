package com.split.ai.split.service.core.service.impl;

import com.split.ai.split.service.commons.exception.ErrorCode;
import com.split.ai.split.service.commons.exception.SplitException;
import com.split.ai.split.service.core.mapper.UserAuthServiceMapper;
import com.split.ai.split.service.core.service.IPasswordService;
import com.split.ai.split.service.core.service.ITokenService;
import com.split.ai.split.service.core.service.IUserAuthService;
import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import com.split.ai.split.service.repository.dao.IIdentityDao;
import com.split.ai.split.service.repository.dao.ILocalCredentialsDao;
import com.split.ai.split.service.repository.dao.IRefreshTokenDao;
import com.split.ai.split.service.repository.dao.ISessionDao;
import com.split.ai.split.service.repository.dao.IUserDao;
import com.split.ai.split.service.repository.entity.IdentityEntity;
import com.split.ai.split.service.repository.entity.LocalCredentialsEntity;
import com.split.ai.split.service.repository.entity.RefreshTokenEntity;
import com.split.ai.split.service.repository.entity.SessionEntity;
import com.split.ai.split.service.repository.entity.UserEntity;
import com.split.ai.split.service.model.enums.USER_STATUS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService implements IUserAuthService {

    private final IIdentityDao identityDao;
    private final ILocalCredentialsDao localCredentialsDao;
    private final IRefreshTokenDao refreshTokenDao;
    private final ISessionDao sessionDao;
    private final IUserDao userDao;
    private final IPasswordService passwordService;
    private final ITokenService tokenService;

    @Value("${security.password.hash-algo}")
    private String hashAlgo;

    @Value("${security.auth.refresh-token-ttl}")
    private long refreshTtl;

    public SignupResponse signup(SignupRequest request) {
        log.info("[UserAuthService : signup] : userName={}", request.getUserName());
        IdentityEntity identityEntity = UserAuthServiceMapper.MAPPER.toIdentityEntity(request, Boolean.FALSE);
        UUID userId = identityEntity.getUserId();
        identityDao.save(identityEntity);

        String encodedPassword = passwordService.encode(request.getPassword());
        LocalCredentialsEntity credentialsEntity = UserAuthServiceMapper.MAPPER.toLocalCredentialsEntity(userId, encodedPassword, hashAlgo);
        localCredentialsDao.save(credentialsEntity);

        SignupResponse response = UserAuthServiceMapper.MAPPER.toSignupResponse(request, userId);
        attachTokens(userId, 0, response);
        return response;
    }

    public LoginResponse login(LoginRequest request) {
        log.info("[UserAuthService : login] : identifier={}", request.getIdentifier());
        Optional<IdentityEntity> identityOpt = identityDao.findByProviderAndIdentifier(request.getProvider(), request.getIdentifier());
        IdentityEntity identityEntity = identityOpt.orElseThrow(() -> {
            log.error("[UserAuthService : login] : identity not found for provider {} identifier {}", request.getProvider(), request.getIdentifier());
            return SplitException.createException(ErrorCode.INVALID_CREDENTIALS);
        });
        LocalCredentialsEntity credentialsEntity = localCredentialsDao.findByUserId(identityEntity.getUserId())
                .orElseThrow(() -> {
                    log.error("[UserAuthService : login] : credentials not found for user {}", identityEntity.getUserId());
                    return SplitException.createException(ErrorCode.INVALID_CREDENTIALS);
                });

        boolean matches = passwordService.matchesAndUpgrade(request.getPassword(), credentialsEntity.getPasswordHash(), newHash -> {
            credentialsEntity.setPasswordHash(newHash);
            localCredentialsDao.update(credentialsEntity);
        });
        if (!matches) {
            log.error("[UserAuthService : login] : password mismatch for user {}", identityEntity.getUserId());
            throw SplitException.createException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserEntity userEntity = userDao.findById(identityEntity.getUserId());
        if (userEntity.getUserStatus() == USER_STATUS.BLOCKED) {
            log.error("[UserAuthService : login] : user blocked {}", userEntity.getUserId());
            throw SplitException.createException(ErrorCode.INVALID_CREDENTIALS);
        }
        LoginResponse response = UserAuthServiceMapper.MAPPER.toLoginResponse(identityEntity);
        attachTokens(userEntity.getUserId(), userEntity.getTokenVersion(), response);
        return response;
    }


    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        try {
            Map<String, Object> claims = tokenService.parseRefreshToken(refreshToken);
            UUID tokenId = UUID.fromString((String) claims.get("jti"));
            refreshTokenDao.findById(tokenId).ifPresent(rt -> {
                rt.setRevokedAt(System.currentTimeMillis());
                refreshTokenDao.update(rt);
                sessionDao.findById(rt.getSessionId()).ifPresent(sess -> {
                    sess.setRevokedAt(System.currentTimeMillis());
                    sessionDao.update(sess);
                });
            });
        } catch (Exception e) {
            log.error("[UserAuthService : logout] : failed", e);
        }
    }

    private void attachTokens(UUID userId, int tokenVersion, SignupResponse response) {
        Tokens tokens = createTokens(userId, tokenVersion);
        response.setAccessToken(tokens.accessToken);
        response.setRefreshToken(tokens.refreshToken);
    }

    private void attachTokens(UUID userId, int tokenVersion, LoginResponse response) {
        Tokens tokens = createTokens(userId, tokenVersion);
        response.setAccessToken(tokens.accessToken);
        response.setRefreshToken(tokens.refreshToken);
    }

    private Tokens createTokens(UUID userId, int tokenVersion) {
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

    private record Tokens(String accessToken, String refreshToken) {}
}
