package org.example.newflowmanagerservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.newflowmanagerservice.exeptions.KafkaPublishException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor

public class KafkaProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendEvent(String topic,String payload){
        try{
            kafkaTemplate.send(topic,payload);
            log.info("Событие отправлено в топик {}: {}",topic,payload);
        }catch (Exception e){
            log.error("Ошибка отправки в топик{} :{}",topic,e.getMessage());
            throw new KafkaPublishException("Не удалось отправить событие в Kafka", e);
        }
    }
}
