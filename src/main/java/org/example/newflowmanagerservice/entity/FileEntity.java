package org.example.newflowmanagerservice.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {

    @Id

    private String id;  // UUID

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "minio_path", nullable = false, unique = true)
    private String minioPath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FileStatus status;

    @Column(name = "converted_minio_path")
    private String convertedMinioPath;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //убрал пока что сомнения есть
//    @Column(name = "message_id", unique = true)
//    private String messageId;  // это eventId, который уйдёт в Kafka

    // Метод для изменения статуса с сохранением контекста
    public void markAsSuccess(String convertedMinioPath) {
        this.status = FileStatus.SUCCESS;
        this.convertedMinioPath = convertedMinioPath;
        this.errorMessage = null;
    }

    public void markAsError(String errorMessage) {
        this.status = FileStatus.ERROR;
        this.errorMessage = errorMessage;
    }
}
