package org.example.newflowmanagerservice.dto;

import lombok.Builder;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.FileStatus;

import java.time.LocalDateTime;


@Builder
public record FileResponse(
        String fileId,
        String fileName,
        String minioPath,
        FileStatus status,
        String message,
        String originalFileName,
        LocalDateTime createdAt

) {
    // Метод для создания ответа из сущности
    public static FileResponse fromEntity(FileEntity entity) {
        return FileResponse.builder()
                .fileId(entity.getId())
                .fileName(entity.getOriginalFileName())
                .minioPath(entity.getMinioPath())
                .status(entity.getStatus())
                .message("File uploaded successfully")
                .originalFileName(entity.getOriginalFileName())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}