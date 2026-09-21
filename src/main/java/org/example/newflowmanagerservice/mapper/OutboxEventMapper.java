package org.example.newflowmanagerservice.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.newflowmanagerservice.dto_event.FileUploadedEvent;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.OutboxEvent;
import org.example.newflowmanagerservice.entity.OutboxStatus;
import org.example.newflowmanagerservice.exeptions.FileOperationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventMapper {

    private final ObjectMapper objectMapper;

    public OutboxEvent toOutboxEvent(FileEntity fileEntity, String topic) {
        try {
            FileUploadedEvent event = FileUploadedEvent.builder()
                    .fileId(fileEntity.getId())
                    .fileName(fileEntity.getFileName())
                    .originalFileName(fileEntity.getOriginalFileName())
                    .minioPath(fileEntity.getMinioPath())
                    .fileSize(fileEntity.getFileSize())
                    .contentType(fileEntity.getContentType())
                    .timestamp(LocalDateTime.now().toString())
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            return OutboxEvent.builder()
                    .aggregateId(fileEntity.getId())
                    .eventType("FILE_UPLOADED")
                    .payload(payload)
                    .topic(topic)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (JsonProcessingException e) {
            log.error("❌ Ошибка сериализации события: {}", e.getMessage(), e);
            throw new FileOperationException("Не удалось создать outbox событие", e);
        }
    }
}