package org.example.newflowmanagerservice.repository;

import org.example.newflowmanagerservice.entity.FileEntity;
import org.example.newflowmanagerservice.entity.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity,String> {

    Optional<FileEntity> findByMinioPath(String minioPath);


    List<FileEntity> findByStatus(FileStatus status);

    boolean existsByMinioPath(String minioPath);
}
