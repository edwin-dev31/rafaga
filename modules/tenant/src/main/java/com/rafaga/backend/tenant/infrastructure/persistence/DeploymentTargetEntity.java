package com.rafaga.backend.tenant.infrastructure.persistence;

import com.rafaga.backend.tenant.domain.model.DeploymentTargetCapacity;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetStatus;
import com.rafaga.backend.tenant.domain.model.DeploymentTargetType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deployment_targets")
public class DeploymentTargetEntity {
    @Id private String id;
    @Enumerated(EnumType.STRING) @Column(name = "target_type") private DeploymentTargetType targetType;
    private String protocol;
    @Column(name = "private_origin") private String privateOrigin;
    @Column(name = "health_path") private String healthPath;
    @Enumerated(EnumType.STRING) @Column(name = "target_status") private DeploymentTargetStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "capacity_state") private DeploymentTargetCapacity capacity;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    protected DeploymentTargetEntity() { }
    public DeploymentTargetEntity(String id, DeploymentTargetType type, String protocol, String origin, String healthPath, Instant now) {
        this.id=id; targetType=type; this.protocol=protocol; privateOrigin=origin; this.healthPath=healthPath;
        status=DeploymentTargetStatus.PREPARING; capacity=DeploymentTargetCapacity.AVAILABLE; createdAt=now; updatedAt=now;
    }
    public void mark(DeploymentTargetStatus next, DeploymentTargetCapacity nextCapacity, Instant now) { status=next; capacity=nextCapacity; updatedAt=now; }
    public String getId() { return id; }
    public DeploymentTargetType getTargetType() { return targetType; }
    public DeploymentTargetStatus getStatus() { return status; }
    public DeploymentTargetCapacity getCapacity() { return capacity; }
    public String getPrivateOrigin() { return privateOrigin; }
    public String getProtocol() { return protocol; }
    public String getHealthPath() { return healthPath; }
    public Instant getUpdatedAt() { return updatedAt; }
}
