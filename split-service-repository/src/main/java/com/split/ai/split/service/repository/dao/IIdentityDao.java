package com.split.ai.split.service.repository.dao;

import com.split.ai.split.service.model.enums.IDENTITY_PROVIDER;
import com.split.ai.split.service.repository.entity.IdentityEntity;

import java.util.Optional;
import java.util.UUID;

public interface IIdentityDao {

    IdentityEntity save(IdentityEntity identityEntity);

    Optional<IdentityEntity> findByProviderAndIdentifier(IDENTITY_PROVIDER provider, String identifier);

    Optional<IdentityEntity> findById(UUID id);
}
