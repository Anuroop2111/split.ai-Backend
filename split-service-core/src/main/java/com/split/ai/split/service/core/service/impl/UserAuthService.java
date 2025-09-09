package com.split.ai.split.service.core.service.impl;

import com.split.ai.split.service.commons.exception.ErrorCode;
import com.split.ai.split.service.commons.exception.SplitException;
import com.split.ai.split.service.core.mapper.UserAuthServiceMapper;
import com.split.ai.split.service.core.helper.TokenHelper;
import com.split.ai.split.service.core.helper.TokenHelper.Tokens;
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
import com.split.ai.split.service.model.enums.LANGUAGE;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final TokenHelper tokenHelper;

    @Value("${security.password.hash-algo}")
    private String hashAlgo;

    public SignupResponse signup(SignupRequest request, HttpServletResponse httpResponse) {
        log.info("[UserAuthService : signup] : userName={}", request.getUserName());
        IdentityEntity identityEntity = UserAuthServiceMapper.MAPPER.toIdentityEntity(request, Boolean.FALSE);
        UUID userId = identityEntity.getUserId();
        identityDao.save(identityEntity);

        String encodedPassword = passwordService.encode(request.getPassword());
        LocalCredentialsEntity credentialsEntity = UserAuthServiceMapper.MAPPER.toLocalCredentialsEntity(userId, encodedPassword, hashAlgo);
        localCredentialsDao.save(credentialsEntity);

        UserEntity userEntity = UserEntity.builder()
                .userId(userId)
                .fullName(request.getUserName())
                .userName(request.getUserName())
                .emailId(request.getEmailId())
                .phone("")
                .userStatus(USER_STATUS.ACTIVE)
                .language(LANGUAGE.ENGLISH)
                .tokenVersion(0)
                .build();
        userEntity.beforeInsertOrUpdate();
        userDao.save(userEntity);

        SignupResponse response = UserAuthServiceMapper.MAPPER.toSignupResponse(request, userId);
        Tokens tokens = tokenHelper.createTokens(userId, userEntity.getTokenVersion());
        tokenHelper.setCookies(httpResponse, tokens);
        return response;
    }

    public LoginResponse login(LoginRequest request, HttpServletResponse httpResponse) {
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
        Tokens tokens = tokenHelper.createTokens(userEntity.getUserId(), userEntity.getTokenVersion());
        tokenHelper.setCookies(httpResponse, tokens);
        return response;
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = tokenHelper.getRefreshToken(httpRequest);
        tokenHelper.clearCookies(httpResponse);
        if (refreshToken == null) {
            return;
        }
        try {
            Map<String, Object> claims = tokenService.parseRefreshToken(refreshToken);
            UUID tokenId = UUID.fromString((String) claims.get("jti"));
            RefreshTokenEntity rt = refreshTokenDao.findById(tokenId)
                    .orElseThrow(() -> SplitException.createException(ErrorCode.INVALID_CREDENTIALS));
            SessionEntity sess = sessionDao.findById(rt.getSessionId())
                    .orElseThrow(() -> SplitException.createException(ErrorCode.INVALID_CREDENTIALS));
            rt.setRevokedAt(System.currentTimeMillis());
            refreshTokenDao.update(rt);
            sess.setRevokedAt(System.currentTimeMillis());
            sessionDao.update(sess);
        } catch (SplitException e) {
            throw e;
        } catch (Exception e) {
            log.error("[UserAuthService : logout] : failed", e);
            throw SplitException.createException(ErrorCode.INVALID_CREDENTIALS);
        }
    }
}
