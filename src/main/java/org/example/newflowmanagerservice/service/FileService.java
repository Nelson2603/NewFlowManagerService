package org.example.newflowmanagerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.config.KafkaProperties;
import org.example.newflowmanagerservice.dto.FileResponse;
import org.example.newflowmanagerservice.dto_feign.SubscriptionDto;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.FileStatus;
import org.example.newflowmanagerservice.entity.OutboxEvent;
import org.example.newflowmanagerservice.exeptions.FileOperationException;
import org.example.newflowmanagerservice.exeptions.MinioStorageException;
import org.example.newflowmanagerservice.exeptions.ResourceNotFoundException;
import org.example.newflowmanagerservice.mapper.FileMapper;
import org.example.newflowmanagerservice.mapper.OutboxEventMapper;
import org.example.newflowmanagerservice.repository.FileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioService minioService;
    private final FileMapper fileMapper;
    private final FileRepository fileRepository;
    private final FileRecoveryService recoveryService;
    private final OutboxEventMapper outboxEventMapper;
    private final OutboxService outboxService;
    private final KafkaProperties kafkaProperties;
    private final SubscriptionCacheService subscriptionCacheService;

    @Value("${app.subscription.free-file-size-limit-bytes:104857600}")
    private long freeFileSizeLimitBytes;


    /**
     * Загрузка файла: MinIO → БД + Outbox (в одной транзакции)
     */
    public FileResponse uploadFile(MultipartFile file,String userLogin) {
        checkSubscriptionLimit(userLogin, file.getSize());

        // 1. Генерация ID и пути
        String fileId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String minioPath = String.format("%d-%s%s",
                System.currentTimeMillis(),
                fileId,
                extension);

        log.info("🚀 Начало загрузки файла: {} -> {}", originalFileName, minioPath);

        long size = 0;
        String contentType = null;

        // 2. Сохраняем файл в MinIO
        try (InputStream inputStream = file.getInputStream()) {
            size = file.getSize();
            contentType = file.getContentType();

            minioService.uploadFile(minioPath, inputStream, size, contentType);
            log.info("✅ Файл загружен в MinIO: {}", minioPath);

        } catch (Exception e) {
            log.error("❌ Ошибка загрузки в MinIO: {}", e.getMessage(), e);
            throw new MinioStorageException("Не удалось загрузить файл в MinIO", e);
        }

        // 3. Создаём сущность для БД
        FileEntity fileEntity = FileEntity.builder()
                .id(fileId)
                .fileName(minioPath)
                .originalFileName(originalFileName)
                .minioPath(minioPath)
                .fileSize(size)
                .contentType(contentType)
                .status(FileStatus.IN_PROCESS)
                .createdAt(LocalDateTime.now())
                .build();

        // 4. Сохраняем в БД + Outbox в одной транзакции
        try {
            FileEntity savedEntity = saveFileWithOutbox(fileEntity);
            log.info("💾 Данные о файле и Outbox событие сохранены");
            return fileMapper.toResponse(savedEntity);

        } catch (Exception e) {
            log.error("❌ Ошибка сохранения в БД. Файл уже в MinIO: {}", minioPath, e);

            // Добавляем в очередь восстановления (файл есть в MinIO, но нет записи в БД)
            recoveryService.addToRecoveryQueue(fileEntity);

            // Возвращаем ответ клиенту, что файл загружен, но информация будет обработана позже
            return fileMapper.toResponse(fileEntity,
                    "файл загружен , инфа будет отработана в фоне");
        }
    }

    /**
     * Сохранение файла и Outbox события в одной транзакции.
     * Если транзакция откатится, файл уже в MinIO, но запись в БД не появится.
     */

    @Transactional
    public FileEntity saveFileWithOutbox(FileEntity fileEntity) {
        // 1. Сохраняем запись о файле
        FileEntity savedEntity = fileRepository.save(fileEntity);

        // 2. Создаём Outbox-событие
        OutboxEvent outboxEvent = outboxEventMapper.toOutboxEvent(
                savedEntity,
                kafkaProperties.getTopics().getToConvert()
        );

        // 3. Сохраняем Outbox-событие (в той же транзакции)
        outboxService.saveEvent(outboxEvent);

        return savedEntity;
    }

    /**
     * Получение информации о файле по ID
     */
    public FileResponse getFileInfo(String fileId) {
        log.info("📋 Запрос информации о файле: {}", fileId);

        FileEntity entity = fileRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.warn("⚠️ Файл не найден с ID: {}", fileId);
                    return new ResourceNotFoundException("Файл не найден с ID: " + fileId);
                });

        return fileMapper.toResponse(entity);
    }

    /**
     * Обновление статуса файла (для обработки результата конвертации из Kafka)
     */
    @Transactional
    public void updateFileStatus(String minioPath, FileStatus status, String convertedPath, String errorMessage) {
        log.info("🔄 Обновление статуса файла: {} -> {}", minioPath, status);

        FileEntity entity = fileRepository.findByMinioPath(minioPath)
                .orElseThrow(() -> {
                    log.warn("⚠️ Файл не найден по пути: {}", minioPath);
                    return new ResourceNotFoundException("Файл не найден по пути: " + minioPath);
                });

        if (status == FileStatus.SUCCESS) {
            entity.markAsSuccess(convertedPath);
            log.info("✅ Статус обновлен на SUCCESS: {}", minioPath);
        } else if (status == FileStatus.ERROR) {
            entity.markAsError(errorMessage);
            log.error("❌ Статус обновлен на ERROR: {}", minioPath);
        }

        fileRepository.save(entity);
        log.info("💾 Статус сохранен в БД");
    }



    /**
     * Извлечение расширения файла
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    @Transactional
    public void updateFileStatusByFileId(String fileId, FileStatus status, String convertedPath, String errorMessage) {
        log.info("🔄 Обновление статуса файла: {} -> {}", fileId, status);

        FileEntity entity = fileRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.warn("⚠️ Файл не найден с ID: {}", fileId);
                    return new ResourceNotFoundException("Файл не найден с ID: " + fileId);
                });

        if (status == FileStatus.SUCCESS) {
            entity.markAsSuccess(convertedPath);   // используем метод сущности
            log.info("✅ Статус обновлен на SUCCESS для файла {}", fileId);
        } else if (status == FileStatus.ERROR) {
            entity.markAsError(errorMessage);      // используем метод сущности
            log.error("❌ Статус обновлен на ERROR для файла {}", fileId);
        }

        fileRepository.save(entity);
        log.info("💾 Статус сохранен в БД");
    }
          //Метод проверки подписки и размера файла
    private void checkSubscriptionLimit(String login, long fileSize) {
        if (fileSize <= freeFileSizeLimitBytes) {
            log.debug("Файл {} байт ≤ {} — подписка не требуется",
                    fileSize, freeFileSizeLimitBytes);
            return;
        }

        log.info("Файл {} байт > {} — проверяем подписку для {}",
                fileSize, freeFileSizeLimitBytes, login);

        Optional<SubscriptionDto> maybeSub = subscriptionCacheService.getSubscription(login);

        if (maybeSub.isEmpty()) {
            log.warn("Подписка для {} не найдена", login);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Подписка не найдена. Файлы больше 100 МБ доступны только по платной подписке."
            );
        }

        SubscriptionDto sub = maybeSub.get();
        boolean isPaid = "PAID".equals(sub.type());
        boolean notExpired = sub.expiresAt() != null
                && sub.expiresAt().isAfter(LocalDateTime.now());

        if (!isPaid || !notExpired) {
            log.warn("Отказано в загрузке для {}: type={}, expiresAt={}",
                    login, sub.type(), sub.expiresAt());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Платная подписка истекла или отсутствует. Файлы больше 100 МБ недоступны."
            );
        }

        log.info("Разрешено: {} имеет активную PAID-подписку до {}",
                login, sub.expiresAt());
    }

}