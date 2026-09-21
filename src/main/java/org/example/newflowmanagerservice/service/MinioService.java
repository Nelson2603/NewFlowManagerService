package org.example.newflowmanagerservice.service;

import io.minio.*;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.config.MinioConfig;
import org.example.newflowmanagerservice.exeptions.MinioStorageException;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioConfig.MinioProperties minioProperties;

    /**
     * Загружает файл в MinIO
     *
     * Как работает загрузка файла в MinIO:
     * 1. MinioClient - клиент для взаимодействия с MinIO сервером
     * 2. Bucket - контейнер для хранения объектов (аналог папки на диске)
     * 3. Object - файл, который мы сохраняем с уникальным ключом (путем)
     * 4. UploadObject - загружает файл на сервер
     */
    public void uploadFile(String objectName, InputStream inputStream, long size, String contentType) {
        try {
            //  этот коммит оставил себе как напоминание проблемы которая возникла при тесте ИСПРАВЛЕНИЕ 1: Валидация contentType перед отправкой в MinIO 🚨🚨🚨
            // КОРЕНЬ ОШИБКИ: MinIO требует валидный MIME-тип (например, "text/plain", "image/jpeg").
            // Когда Postman не смог определить тип файла, он отправлял строку "File" или null.
            // MinIO выбрасывал IllegalArgumentException: invalid content type 'File' as per RFC 2045
            // РЕШЕНИЕ: Проверяем contentType и если он некорректный - заменяем на стандартный "application/octet-stream"
            if (contentType == null || contentType.isBlank() || !contentType.contains("/")) {
                contentType = "application/octet-stream";
            }
            //  КОНЕЦ ИСПРАВЛЕНИЯ 1

            // Проверяем, существует ли bucket, если нет - создаем
            if (!minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build())) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .build());
                log.info("✅ Создание Бакета: {}", minioProperties.getBucketName());
            }

            // Загружаем объект в MinIO
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .stream(inputStream, size, -1L) // -1 означает, что размер читается из InputStream
                    .contentType(contentType)       // 🚨 ТЕПЕРЬ сюда всегда попадает валидный тип
                    .build();

            minioClient.putObject(putObjectArgs);


            log.info("Файл успешно загружен в корзину '{}' с именем объекта '{}'",
                    minioProperties.getBucketName(), objectName);

        } catch (MinioException e) {
            log.error("MinIO Ошибка: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось выполнить операцию MinIO", e);
        } catch (Exception e) {
            log.error("Ошибка загрузки файла в MinIO: {}", e.getMessage(), e);
            throw new MinioStorageException("Не удалось загрузить файл в MinIO", e);
        }
    }

    /**
     * Скачивает файл из MinIO
     */
    public InputStream downloadFile(String objectName) {
        try {
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build();

            return minioClient.getObject(getObjectArgs);

        } catch (Exception e) {
            log.error("Ошибка скачивания файла из MinIO: {}", e.getMessage(), e);
            throw new MinioStorageException("Не удалось скачать файл из MinIO", e);
        }
    }

    public boolean fileExists(String objectName) {
        try {
            // Пытаемся получить статистику объекта
            // Если объект существует - операция успешна
            StatObjectArgs statArgs = StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build();

            minioClient.statObject(statArgs);
            log.debug("✅ Файл существует в MinIO: {}", objectName);
            return true;

        } catch (MinioException e) {
            // Проверяем, является ли ошибка "NoSuchKey" (объект не найден)
            if (e.getMessage() != null && e.getMessage().contains("NoSuchKey")) {
                log.debug("📁 Файл не найден в MinIO: {}", objectName);
                return false;
            }
            // Другие ошибки логируем
            log.error("❌ Ошибка при проверке существования файла {}: {}", objectName, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка при проверке файла {}: {}", objectName, e.getMessage());
            return false;
        }
    }
}