package org.example.newflowmanagerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.client.SubscriptionClient;
import org.example.newflowmanagerservice.dto_feign.SubscriptionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;


       // Читает из Redis, при отсутствии данных делает REST-запрос через Feign.
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCacheService {

    private static final String KEY_PREFIX = "subscription:";

    private final SubscriptionClient subscriptionClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.subscription.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    public Optional<SubscriptionDto> getSubscription(String login) {
        String key = KEY_PREFIX + login;

        // 1. Пробуем Redis
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                SubscriptionDto dto = objectMapper.readValue(cached, SubscriptionDto.class);
                log.debug("Подписка {} найдена в кеше: type={}", login, dto.type());
                return Optional.of(dto);
            } catch (Exception e) {
                log.warn("Ошибка десериализации кеша для {}: {}", login, e.getMessage());
                redisTemplate.delete(key);
            }
        }

        // 2. Промах кеша — идём в SubscriptionService
        try {
            SubscriptionDto dto = subscriptionClient.getSubscription(login);
            putToCache(login, dto);
            log.info("Подписка {} получена из SubscriptionService и закеширована: type={}",
                    login, dto.type());
            return Optional.of(dto);
        } catch (Exception e) {
            log.warn("Не удалось получить подписку {} из SubscriptionService: {}",
                    login, e.getMessage());
            return Optional.empty();
        }
    }

    public void evict(String login) {
        String key = KEY_PREFIX + login;
        Boolean removed = redisTemplate.delete(key);
        log.info("Кеш подписки {} очищен: удалено={}", login, removed);
    }

    private void putToCache(String login, SubscriptionDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + login,
                    json,
                    Duration.ofMinutes(cacheTtlMinutes)
            );
        } catch (Exception e) {
            log.warn("Не удалось закешировать подписку {}: {}", login, e.getMessage());
        }
    }
}