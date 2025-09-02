package com.split.ai.split.service.repository.dao;

import com.split.ai.split.service.repository.entity.LocalCredentialsEntity;

import java.util.Optional;
import java.util.UUID;

public interface ILocalCredentialsDao {

    LocalCredentialsEntity save(LocalCredentialsEntity entity);

    void update(LocalCredentialsEntity entity);

    Optional<LocalCredentialsEntity> findByUserId(UUID userId);
}
