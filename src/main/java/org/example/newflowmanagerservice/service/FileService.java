package org.example.newflowmanagerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.dto.FileResponse;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.FileStatus;
import org.example.newflowmanagerservice.entity.OutboxEvent;
import org.example.newflowmanagerservice.exeptions.FileOperationException;
import org.example.newflowmanagerservice.exeptions.MinioStorageException;
import org.example.newflowmanagerservice.exeptions.ResourceNotFoundException;
import org.example.newflowmanagerservice.mapper.OutboxEventMapper;
import org.example.newflowmanagerservice.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioService minioService;
    private final FileRepository fileRepository;
    private final FileRecoveryService recoveryService;
    private final OutboxEventMapper outboxEventMapper;
    private final OutboxService outboxService;

    private static final String KAFKA_TOPIC_TO_CONVERT = "to-convert";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * Загрузка файла: MinIO → БД + Outbox (в одной транзакции)
     */
    public FileResponse uploadFile(MultipartFile file) {
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
            return FileResponse.fromEntity(savedEntity);

        } catch (Exception e) {
            log.error("❌ Ошибка сохранения в БД. Файл уже в MinIO: {}", minioPath, e);

            // Добавляем в очередь восстановления (файл есть в MinIO, но нет записи в БД)
            recoveryService.addToRecoveryQueue(fileEntity);

            // Возвращаем ответ клиенту, что файл загружен, но информация будет обработана позже
            return FileResponse.builder()
                    .fileId(fileEntity.getId())
                    .fileName(fileEntity.getOriginalFileName())
                    .originalFileName(fileEntity.getOriginalFileName())
                    .minioPath(fileEntity.getMinioPath())
                    .status(FileStatus.IN_PROCESS)
                    .message("Файл загружен, информация будет обработана в фоне")
                    .createdAt(LocalDateTime.now())
                    .build();
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
                KAFKA_TOPIC_TO_CONVERT
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

        return FileResponse.fromEntity(entity);
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
     * Сохранение в БД с повторными попытками (используется при восстановлении)
     */
    private FileEntity saveWithRetry(FileEntity fileEntity) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRIES) {
            try {
                attempts++;
                log.info("🔄 Попытка {} сохранения в БД", attempts);
                return fileRepository.save(fileEntity);

            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ Ошибка сохранения в БД (попытка {}): {}", attempts, e.getMessage());

                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new FileOperationException("Не удалось сохранить в БД после " + MAX_RETRIES + " попыток", lastException);
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
}