package org.example.newflowmanagerservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.newflowmanagerservice.dto_event.SubscriptionChangedEvent;
import org.example.newflowmanagerservice.service.SubscriptionCacheService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionChangedConsumer {

    private final SubscriptionCacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * groupId — из application.yml.
     * topics — app.kafka.topics.subscription-changed (subscription-changed).
     */
    @KafkaListener(
            topics = "${app.kafka.topics.subscription-changed:subscription-changed}",
            groupId = "${app.kafka.group-id:flow-manager-group}"
    )
    public void onSubscriptionChanged(String message) {
        try {
            SubscriptionChangedEvent event = objectMapper.readValue(
                    message, SubscriptionChangedEvent.class);

            log.info("Получено событие об изменении подписки: login={}, newType={}, reason={}",
                    event.login(), event.newType(), event.reason());

            // Удаляем из Redis, чтобы следующий запрос шёл в SubscriptionService
            cacheService.evict(event.login());

        } catch (Exception e) {
            log.error("Ошибка обработки события subscription-changed: {}", e.getMessage(), e);
        }
    }
}