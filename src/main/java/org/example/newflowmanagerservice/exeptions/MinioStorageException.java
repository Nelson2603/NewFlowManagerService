package org.example.newflowmanagerservice.exeptions;

public class MinioStorageException extends RuntimeException {
    public MinioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}