package org.example.newflowmanagerservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.dto_event.FileConversionResponse;
import org.example.newflowmanagerservice.entity.FileStatus;
import org.example.newflowmanagerservice.service.FileService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileConversionResultConsumer {

    private final FileService fileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "file-conversion-responses", groupId = "flow-manager-group")
    public void consume(String message) {
        log.info("📩 Получен результат конвертации: {}", message);
        try {
            FileConversionResponse response = objectMapper.readValue(message, FileConversionResponse.class);
            String fileId = response.messageId();
            String resultPath = response.resultPath();

            // Обновляем статус файла в БД
            fileService.updateFileStatusByFileId(fileId, FileStatus.SUCCESS, resultPath, null);
            log.info("✅ Статус файла {} обновлён на SUCCESS", fileId);
        } catch (Exception e) {
            log.error("❌ Ошибка обработки результата: {}", e.getMessage(), e);
        }
    }
}