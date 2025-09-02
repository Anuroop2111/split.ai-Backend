package com.split.ai.split.service.repository.dao.impl;

import com.split.ai.commons.postgres.PostgresClient;
import com.split.ai.split.service.model.enums.IDENTITY_PROVIDER;
import com.split.ai.split.service.repository.dao.IIdentityDao;
import com.split.ai.split.service.repository.entity.IdentityEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class IdentityDao implements IIdentityDao {

    private final PostgresClient postgresClient;

    public IdentityEntity save(IdentityEntity identityEntity) {
        log.debug("[IdentityDao : save] : {}", identityEntity);
        identityEntity.beforeInsertOrUpdate();
        return postgresClient.insert(identityEntity);
    }

    public Optional<IdentityEntity> findByProviderAndIdentifier(IDENTITY_PROVIDER provider, String identifier) {
        log.debug("[IdentityDao : findByProviderAndIdentifier] provider {} identifier {}", provider, identifier);
        String sql = "SELECT * FROM identity WHERE provider = :provider AND identifier = :identifier";
        Map<String, Object> params = new HashMap<>();
        params.put("provider", provider.name());
        params.put("identifier", identifier);
        List<IdentityEntity> results = postgresClient.queryNative(sql, params, IdentityEntity.class);
        return results.stream().findFirst();
    }

    public Optional<IdentityEntity> findById(UUID id) {
        log.debug("[IdentityDao : findById] : {}", id);
        return Optional.ofNullable(postgresClient.findById(IdentityEntity.class, id));
    }
}
