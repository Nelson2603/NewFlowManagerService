package org.example.newflowmanagerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.entity.OutboxEvent;
import org.example.newflowmanagerservice.entity.OutboxStatus;
import org.example.newflowmanagerservice.kafka.KafkaProducerService;
import org.example.newflowmanagerservice.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;

    @Transactional
    public void saveEvent(OutboxEvent event) {
        outboxRepository.save(event);
        log.info("Событие сохранено в Outbox");
    }

    /**
     * Отправляет все PENDING события в Kafka
     * Этот метод вызывается по расписанию
     */
    @Transactional
    public void processPendingEvents() {
        log.info("Начало обработки PENDING событий");

        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            log.debug(" Нет событий для отправки");
            return;
        }

        log.info("📊 Найдено {} событий для отправки", pendingEvents.size());

        int processed = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                // Отправляем в Kafka
                kafkaProducerService.sendEvent(event.getTopic(), event.getPayload());

                // Помечаем как отправленное
                event.markAsSent();
                outboxRepository.save(event);

                processed++;
                log.info("✅ Событие {} отправлено в топик {}", event.getId(), event.getTopic());

            } catch (Exception e) {
                log.error(" Ошибка отправки события {}: {}", event.getId(), e.getMessage());

                event.markAsFailed(e.getMessage());
                outboxRepository.save(event);

                failed++;

                // Если слишком много ошибок - прерываем
                if (failed >= 10) {
                    log.warn("Слишком много ошибок ({}) - прерываем обработку", failed);
                    break;
                }
            }
        }

        log.info(" Обработка завершена. Отправлено: {}, с ошибками: {}", processed, failed);
    }
        // Повторная обработка FAILED событий

    @Transactional
    public void retryFailedEvents() {
        log.info("Повторная обработка FAILED событий");

        List<OutboxEvent> failedEvents = outboxRepository
                .findByStatusAndRetryCountLessThan(OutboxStatus.FAILED, MAX_RETRY_COUNT);

        if (failedEvents.isEmpty()) {
            log.debug("ℹ️ Нет FAILED событий для повторной отправки");
            return;
        }

        log.info("📊 Найдено {} FAILED событий для повторной отправки", failedEvents.size());

        for (OutboxEvent event : failedEvents) {
            try {
                // Возвращаем в PENDING и пытаемся отправить
                event.markForRetry();
                outboxRepository.save(event);

                // Отправляем в Kafka
                kafkaProducerService.sendEvent(event.getTopic(), event.getPayload());

                // Помечаем как отправленное
                event.markAsSent();
                outboxRepository.save(event);

                log.info("✅ FAILED событие {} успешно отправлено", event.getId());


            } catch (Exception e) {
                log.error("❌ Ошибка повторной отправки события {}: {}", event.getId(), e.getMessage());
                event.markAsFailed(e.getMessage());
                outboxRepository.save(event);
            }
        }
    }

    /**
     * Очистка старых SENT событий
     */
    @Transactional
    public void cleanOldEvents() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);

        int deleted = outboxRepository.deleteByStatusAndSentAtBefore(
                OutboxStatus.SENT,
                oneWeekAgo
        );

        if (deleted > 0) {
            log.info("Удалено {} старых SENT событий", deleted);
        }
    }



}

