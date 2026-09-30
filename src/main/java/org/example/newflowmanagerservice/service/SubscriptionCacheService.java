package org.example.newflowmanagerservice.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.client.SubscriptionClient;
import org.example.newflowmanagerservice.dto_feign.SubscriptionDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;


import org.springframework.stereotype.Service;

import java.time.Duration;


// Читает из Redis, при отсутствии данных делает REST-запрос через Feign.
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionCacheService {


    private final SubscriptionClient subscriptionClient;


    @Value("${app.subscription.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    @Cacheable(value = "subscriptions", key = "#login", unless = "#result == null")
    public SubscriptionDto getSubscription(String login) {
        log.info("Cache MISS для {}, идём в SubscriptionService", login);
        return subscriptionClient.getSubscription(login);
    }


    @CacheEvict(value = "subscriptions", key = "#login")
    public void evict(String login) {

        log.info("Кеш подписки {} очищен", login);
    }

}