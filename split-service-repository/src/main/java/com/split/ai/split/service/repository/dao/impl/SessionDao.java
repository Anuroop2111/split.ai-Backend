package com.split.ai.split.service.repository.dao.impl;

import com.split.ai.commons.postgres.PostgresClient;
import com.split.ai.split.service.repository.dao.ISessionDao;
import com.split.ai.split.service.repository.entity.SessionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionDao implements ISessionDao {

    private final PostgresClient postgresClient;

    @Override
    public SessionEntity save(SessionEntity entity) {
        log.debug("[SessionDao : save] : {}", entity);
        entity.beforeInsert();
        return postgresClient.insert(entity);
    }

    @Override
    public void update(SessionEntity entity) {
        log.debug("[SessionDao : update] : {}", entity);
        entity.beforeUpdate();
        postgresClient.partialUpdate(entity);
    }

    @Override
    public Optional<SessionEntity> findById(UUID sessionId) {
        log.debug("[SessionDao : findById] : {}", sessionId);
        return Optional.ofNullable(postgresClient.findById(SessionEntity.class, sessionId));
    }
}

