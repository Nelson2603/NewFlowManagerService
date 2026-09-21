package org.example.newflowmanagerservice.dto;

import lombok.Builder;



public record FileRequest(
        String fileName,
        String contentType,
        Long fileSize
) {
}
