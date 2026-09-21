package org.example.newflowmanagerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;


    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // ID файла

    @Column(name = "event_type", nullable = false)
    private String eventType; // "FILE_UPLOADED", "FILE_CONVERTED"

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload; // JSON с данными события

    @Column(name = "topic", nullable = false)
    private String topic; // "to-convert", "file-converted"

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status; // PENDING, SENT, FAILED

    @Column(name = "retry_count")
    private Integer retryCount; // Количество попыток

    @Column(name = "last_error")
    private String lastError; // Последняя ошибка

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public void markAsFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = error;
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    // Методы для изменения статуса
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markForRetry() {
        this.status = OutboxStatus.PENDING;
        this.lastError = null;
    }

    public boolean canRetry() {
        return this.retryCount == null || this.retryCount < 5; // Максимум 5 попыток
    }
}
