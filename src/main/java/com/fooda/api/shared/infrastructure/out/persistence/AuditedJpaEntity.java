package com.fooda.api.shared.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** Standard audit columns. created_* are not updatable so a merge from a fresh entity never nulls them. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedJpaEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Written only by an explicit UPDATE (soft delete). */
    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    @Column(name = "deleted_by", insertable = false, updatable = false)
    private Long deletedBy;

    /** Optimistic lock: a save from a stale snapshot fails instead of overwriting newer data. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
