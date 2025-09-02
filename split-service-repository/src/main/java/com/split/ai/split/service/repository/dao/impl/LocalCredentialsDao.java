package com.split.ai.split.service.repository.dao.impl;

import com.split.ai.commons.postgres.PostgresClient;
import com.split.ai.split.service.repository.dao.ILocalCredentialsDao;
import com.split.ai.split.service.repository.entity.LocalCredentialsEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class LocalCredentialsDao implements ILocalCredentialsDao {

    private final PostgresClient postgresClient;

    public LocalCredentialsEntity save(LocalCredentialsEntity entity) {
        log.debug("[LocalCredentialsDao : save] : {}", entity);
        entity.beforeInsertOrUpdate();
        return postgresClient.insert(entity);
    }

    public void update(LocalCredentialsEntity entity) {
        log.debug("[LocalCredentialsDao : update] : {}", entity);
        entity.beforeUpdate();
        postgresClient.partialUpdate(entity);
    }

    public Optional<LocalCredentialsEntity> findByUserId(UUID userId) {
        log.debug("[LocalCredentialsDao : findByUserId] : {}", userId);
        return Optional.ofNullable(postgresClient.findById(LocalCredentialsEntity.class, userId));
    }
}
