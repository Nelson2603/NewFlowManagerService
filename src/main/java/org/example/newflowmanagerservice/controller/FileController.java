package org.example.newflowmanagerservice.controller;

import jakarta.servlet.ServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.example.newflowmanagerservice.dto.FileResponse;
import org.example.newflowmanagerservice.entity.FileStatus;
import org.example.newflowmanagerservice.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class  FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Получен запрос на загрузку файла : {}", file.getOriginalFilename());

        FileResponse response = fileService.uploadFile(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileResponse> getFileInfo(@PathVariable String id){
        log.info("Запрос информации : {}",id);
        return ResponseEntity.ok(fileService.getFileInfo(id));
    }
}

