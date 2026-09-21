package org.example.newflowmanagerservice.dto_event;

import lombok.Builder;


@Builder
public record FileUploadedEvent(
        String fileId,
        String fileName,
        String originalFileName,
        String minioPath,
        Long fileSize,
        String contentType,
        String timestamp
) {
}
