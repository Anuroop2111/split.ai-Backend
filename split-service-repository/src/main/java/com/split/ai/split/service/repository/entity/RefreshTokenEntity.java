package com.split.ai.split.service.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_refresh_token")
public class RefreshTokenEntity {

    @Id
    private UUID refreshTokenId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String tokenHash;

    @Column(nullable = false)
    private Long issuedAt;

    @Column(nullable = false)
    private Long expiresAt;

    private Long revokedAt;

    private UUID replacedBy;
}

