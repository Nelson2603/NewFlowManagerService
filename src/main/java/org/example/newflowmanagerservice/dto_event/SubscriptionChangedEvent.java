package org.example.newflowmanagerservice.dto_event;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

//СОБЫТИЕ ОБ ИЗМЕНЕНИЕ ПОДПИСКИ КОГДА ПОНИЖАЕТСЯ PAID → FREE.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionChangedEvent(
        String login,
        String newType,
        String reason
) {
}