package org.example.newflowmanagerservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.repository.FileRepository; // Исправлено!
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileRecoveryService {

    private final FileRepository fileRepository;
    private final MinioService minioService;

    private final BlockingQueue<FileEntity> recoveryQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread recoveryThread;

    @PostConstruct
    public void startRecoveryThread() {
        log.info("🔄 Запуск потока восстановления");

        recoveryThread = new Thread(() -> {
            while (running.get()) {
                try {
                    // Берем элемент из очереди (блокирующая операция)
                    FileEntity entity = recoveryQueue.take();
                    processRecovery(entity);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("⚠️ Поток восстановления прерван");
                    break;
                } catch (Exception e) {
                    log.error("❌ Ошибка в потоке восстановления: {}", e.getMessage(), e);
                }
            }
        });

        recoveryThread.setDaemon(true); // Поток-демон, чтобы не блокировать завершение
        recoveryThread.start();
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Остановка сервиса восстановления");
        running.set(false);
        recoveryThread.interrupt();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void addToRecoveryQueue(FileEntity entity) {
        try {
            recoveryQueue.offer(entity, 5, TimeUnit.SECONDS);
            log.info("📦 Файл добавлен в очередь восстановления: {}", entity.getMinioPath());
        } catch (InterruptedException e) {
            log.error("❌ Не удалось добавить файл в очередь: {}", entity.getMinioPath(), e);
            Thread.currentThread().interrupt();
        }
    }

    private void processRecovery(FileEntity entity) {
        try {
            log.info("🔄 Обработка восстановления для файла: {}", entity.getMinioPath());

            // Проверяем, существует ли файл в MinIO
            if (!minioService.fileExists(entity.getMinioPath())) {
                log.warn("⚠️ Файл не найден в MinIO: {}", entity.getMinioPath());
                return;
            }

            // Проверяем, не появилась ли уже запись в БД
            if (fileRepository.existsByMinioPath(entity.getMinioPath())) {
                log.info("ℹ️ Запись для файла уже существует в БД: {}", entity.getMinioPath());
                return;
            }

            // Сохраняем в БД
            fileRepository.save(entity);
            log.info("✅ Восстановлена запись для файла: {}", entity.getMinioPath());


        } catch (Exception e) {
            log.error("❌ Ошибка восстановления для файла {}: {}", entity.getMinioPath(), e.getMessage());
            // Повторная попытка через 5 минут
            scheduleRetry(entity);
        }
    }

    private void scheduleRetry(FileEntity entity) {
        log.info("⏰ Запланирована повторная попытка восстановления через 5 минут: {}", entity.getMinioPath());
        scheduler.schedule(() -> {
            log.info("🔄 Повторная попытка восстановления: {}", entity.getMinioPath());
            recoveryQueue.offer(entity);
        }, 5, TimeUnit.MINUTES);
    }

    /**
     * Получить размер очереди (для мониторинга)
     */
    public int getQueueSize() {
        return recoveryQueue.size();
    }
}