package com.split.ai.split.service.repository.dao;

import com.split.ai.split.service.repository.entity.RefreshTokenEntity;

import java.util.Optional;
import java.util.UUID;

public interface IRefreshTokenDao {

    RefreshTokenEntity save(RefreshTokenEntity entity);

    void update(RefreshTokenEntity entity);

    Optional<RefreshTokenEntity> findById(UUID refreshTokenId);
}

