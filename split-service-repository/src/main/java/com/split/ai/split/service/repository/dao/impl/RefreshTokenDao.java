package com.split.ai.split.service.repository.dao.impl;

import com.split.ai.commons.postgres.PostgresClient;
import com.split.ai.split.service.repository.dao.IRefreshTokenDao;
import com.split.ai.split.service.repository.entity.RefreshTokenEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RefreshTokenDao implements IRefreshTokenDao {

    private final PostgresClient postgresClient;

    @Override
    public RefreshTokenEntity save(RefreshTokenEntity entity) {
        log.debug("[RefreshTokenDao : save] : {}", entity);
        return postgresClient.insert(entity);
    }

    @Override
    public void update(RefreshTokenEntity entity) {
        log.debug("[RefreshTokenDao : update] : {}", entity);
        postgresClient.partialUpdate(entity);
    }

    @Override
    public Optional<RefreshTokenEntity> findById(UUID refreshTokenId) {
        log.debug("[RefreshTokenDao : findById] : {}", refreshTokenId);
        return Optional.ofNullable(postgresClient.findById(RefreshTokenEntity.class, refreshTokenId));
    }
}

