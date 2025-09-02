package com.split.ai.split.service.core.service.impl;

import com.split.ai.split.service.commons.exception.ErrorCode;
import com.split.ai.split.service.commons.exception.SplitException;
import com.split.ai.split.service.core.mapper.UserAuthServiceMapper;
import com.split.ai.split.service.core.service.IPasswordService;
import com.split.ai.split.service.core.service.IUserAuthService;
import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.LogoutRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import com.split.ai.split.service.repository.dao.IIdentityDao;
import com.split.ai.split.service.repository.dao.ILocalCredentialsDao;
import com.split.ai.split.service.repository.entity.IdentityEntity;
import com.split.ai.split.service.repository.entity.LocalCredentialsEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService implements IUserAuthService {

    private final IIdentityDao identityDao;
    private final ILocalCredentialsDao localCredentialsDao;
    private final IPasswordService passwordService;

    @Value("${security.password.hash-algo}")
    private String hashAlgo;

    public SignupResponse signup(SignupRequest request) {
        log.info("[UserAuthService : signup] : userName={}", request.getUserName());
        IdentityEntity identityEntity = UserAuthServiceMapper.MAPPER.toIdentityEntity(request, Boolean.FALSE);
        UUID userId = identityEntity.getUserId();
        identityDao.save(identityEntity);

        String encodedPassword = passwordService.encode(request.getPassword());
        LocalCredentialsEntity credentialsEntity = UserAuthServiceMapper.MAPPER.toLocalCredentialsEntity(userId, encodedPassword, hashAlgo);
        localCredentialsDao.save(credentialsEntity);

        return UserAuthServiceMapper.MAPPER.toSignupResponse(request, userId);
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

        return UserAuthServiceMapper.MAPPER.toLoginResponse(identityEntity);
    }

    // todo
    public void logout(LogoutRequest request) {
        log.info("[UserAuthService : logout] : noop");
    }
}
