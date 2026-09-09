package org.example.newflowmanagerservice.exeptions;

public class FileOperationException extends RuntimeException {
    public FileOperationException(String message) {
        super(message);
    }
    public FileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}