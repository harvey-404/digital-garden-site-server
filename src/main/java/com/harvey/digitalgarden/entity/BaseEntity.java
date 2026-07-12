package com.harvey.digitalgarden.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;

@MappedSuperclass
public abstract class BaseEntity {

    @Column(name = "is_deleted", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isDeleted = false;

    @Column(name = "in_dtm", nullable = false)
    private Long inDtm = 0L;

    @Column(name = "update_dtm", nullable = false)
    private Long updateDtm = 0L;

    @PrePersist
    protected void onPersist() {
        long now = Instant.now().getEpochSecond();
        if (this.inDtm == null || this.inDtm == 0L) {
            this.inDtm = now;
        }
        this.updateDtm = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateDtm = Instant.now().getEpochSecond();
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public Long getInDtm() {
        return inDtm;
    }

    public void setInDtm(Long inDtm) {
        this.inDtm = inDtm;
    }

    public Long getUpdateDtm() {
        return updateDtm;
    }

    public void setUpdateDtm(Long updateDtm) {
        this.updateDtm = updateDtm;
    }
}
