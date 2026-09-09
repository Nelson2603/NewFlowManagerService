package org.example.newflowmanagerservice.dto_event;

import lombok.Builder;

@Builder
public record FileConversionResponse(String messageId,String resultPath) {
}
