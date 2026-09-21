package org.example.newflowmanagerservice.dto;

import lombok.Builder;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.FileStatus;

import java.time.LocalDateTime;


public record FileResponse(
        String fileId,
        String fileName,
        String minioPath,
        FileStatus status,
        String message,
        String originalFileName,
        LocalDateTime createdAt

) {
}