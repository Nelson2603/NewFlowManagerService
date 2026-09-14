package org.example.newflowmanagerservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private String groupId;
    private Topics topics = new Topics();

    @Data
    public static class Topics {
        private String toConvert;
        private String conversionResponses;
    }
}