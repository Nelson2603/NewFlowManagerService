package org.example.newflowmanagerservice.mapper;

import org.example.newflowmanagerservice.dto.FileResponse;
import org.example.newflowmanagerservice.entity.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    // Метод для создания ответа из сущности
    public  FileResponse toResponse(FileEntity entity, String message) {
        return new FileResponse(
                entity.getId(),
                entity.getFileName(),
                entity.getMinioPath(),
                entity.getStatus(),
                message,
                entity.getOriginalFileName(),
                entity.getCreatedAt()
        );

    }
    public FileResponse toResponse(FileEntity entity) {
        return toResponse(entity, "File uploaded successfully");
    }
}
