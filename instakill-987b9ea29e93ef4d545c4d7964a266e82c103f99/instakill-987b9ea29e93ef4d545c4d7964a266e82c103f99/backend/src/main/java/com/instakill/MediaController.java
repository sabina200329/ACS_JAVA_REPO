package com.instakill;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    @Value("${media.base-path:media}")
    private String mediaBasePath;

    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Creează directorul dacă nu există
            File directory = new File(mediaBasePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generează un nume unic pentru fișier (UUID + extensia originală)
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // Salvează fișierul pe disc
            Path filePath = Paths.get(mediaBasePath, uniqueFilename);
            Files.write(filePath, file.getBytes());

            // Returnează răspunsul cu status 201 Created și calea către fișier
            String mediaUrl = "/media/" + uniqueFilename;
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mediaUrl", mediaUrl));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}