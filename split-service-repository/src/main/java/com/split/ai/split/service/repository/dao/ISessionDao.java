package com.split.ai.split.service.repository.dao;

import com.split.ai.split.service.repository.entity.SessionEntity;

import java.util.Optional;
import java.util.UUID;

public interface ISessionDao {

    SessionEntity save(SessionEntity entity);

    void update(SessionEntity entity);

    Optional<SessionEntity> findById(UUID sessionId);
}

