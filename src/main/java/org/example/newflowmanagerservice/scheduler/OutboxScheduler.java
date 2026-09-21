package org.example.newflowmanagerservice.scheduler;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.newflowmanagerservice.service.OutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
//отправка PENDING СОБЫТИЙ
public class OutboxScheduler {

    private final OutboxService outboxService;

    //отправка PENDING КАЖДЫЕ 5 СЕК

    @Scheduled(fixedDelayString = "${app.scheduler.process-pending.fixed-delay}")
    @SchedulerLock(name = "processPendingEventsLock", lockAtMostFor = "10s", lockAtLeastFor = "1s")
    public void processPendingEvents() {
        try {
            outboxService.processPendingEvents();
        } catch (Exception e) {
            log.error("ошибка в Scheduled : {} ", e.getMessage());
        }
    }
    //ПОВТОРНАЯ ОТПРАВКА FAILED КАЖДУЮ МИНУТУ

    @Scheduled(fixedDelayString = "${app.scheduler.retry-failed.fixed-delay}")
    @SchedulerLock(name = "retryFailedEventsLock", lockAtMostFor = "1m", lockAtLeastFor = "10s")
    public void retryFailedEvents() {
        try {
            outboxService.retryFailedEvents();
        } catch (Exception e) {
            log.error("Ошибка в scheduled task retryFailedEvents: {}", e.getMessage(), e);
        }

    }

    // ОЧИСТКА СТАРЫХ СООБЩЕНИЙ РАЗ В ДЕНЬ
    @Scheduled(cron = "${app.scheduler.clean-old-events.cron}")
    @SchedulerLock(name = "cleanOldEventsLock", lockAtMostFor = "10m")
    public void cleanOldEvents() {
        try {
            outboxService.cleanOldEvents();
        } catch (Exception e) {
            log.error("❌ Ошибка в scheduled task cleanOldEvents: {}", e.getMessage(), e);
        }
    }
}
