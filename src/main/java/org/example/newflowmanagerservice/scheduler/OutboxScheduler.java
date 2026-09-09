package org.example.newflowmanagerservice.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.service.OutboxService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
//отправка PENDING СОБЫТИЙ
public class OutboxScheduler {

    private final OutboxService outboxService;

              //отправка PENDING КАЖДЫЕ 5 СЕК

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void processPendingEvents() {
        try {
            outboxService.processPendingEvents();
        } catch (Exception e) {
            log.error("ошибка в Scheduled : {} ", e.getMessage());
        }
    }
         //ПОВТОРНАЯ ОТПРАВКА FAILED КАЖДУЮ МИНУТУ

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void retryFailedEvents() {
        try {
            outboxService.retryFailedEvents();
        } catch (Exception e) {
           log.error("Ошибка в scheduled task retryFailedEvents: {}", e.getMessage(), e);
        }

    }

    // ОЧИСТКА СТАРЫХ СООБЩЕНИЙ РАЗ В ДЕНЬ
    @Scheduled(cron = "0 0 3 * * *")//каждый день в 3 ночи
    public void cleanOldEvents() {
        try {
            outboxService.cleanOldEvents();
        } catch (Exception e) {
            log.error("❌ Ошибка в scheduled task cleanOldEvents: {}", e.getMessage(), e);
        }
    }
}
