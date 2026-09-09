package org.example.newflowmanagerservice.entity;


public enum OutboxStatus {
    PENDING,  // Ожидает отправки
    SENT,     // Отправлено
    FAILED    // Ошибка при отправке
}