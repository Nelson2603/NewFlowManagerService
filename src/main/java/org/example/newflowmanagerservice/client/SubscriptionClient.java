package org.example.newflowmanagerservice.client;

import org.example.newflowmanagerservice.dto_feign.SubscriptionDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "subscription-service")
    public interface SubscriptionClient {

        @GetMapping("/api/subscriptions/{login}")
        SubscriptionDto getSubscription(@PathVariable("login") String login);
    }

