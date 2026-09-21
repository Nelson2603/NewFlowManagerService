package org.example.newflowmanagerservice.repository;

import org.example.newflowmanagerservice.entity.OutboxEvent;
import org.example.newflowmanagerservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent,String>{

    // Найти все PENDING события для отправки
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // Найти события, которые нужно переотправить (с ошибкой)
    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, Integer maxRetries);

    // Найти старые FAILED события для мониторинга
    List<OutboxEvent> findByStatusAndCreatedAtBefore(OutboxStatus status, LocalDateTime dateTime);

    // Удалить отправленные события старше N дней
    int deleteByStatusAndSentAtBefore(OutboxStatus status, LocalDateTime dateTime);
}
