package com.instakill.media;

import com.instakill.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final Path mediaDir;
    private final ImageProcessingService imageProcessingService;

    public MediaController(@Value("${media.base-path:media}") String mediaBasePath,
                           ImageProcessingService imageProcessingService) throws IOException {
        this.mediaDir = Paths.get(mediaBasePath).toAbsolutePath().normalize();
        this.imageProcessingService = imageProcessingService;
        // Creăm directorul la pornire dacă lipsește.
        Files.createDirectories(mediaDir);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fișierul este gol");
        }

        String extension = MediaTypes.extensionOf(file.getOriginalFilename());
        boolean image = MediaTypes.IMAGE_EXTENSIONS.contains(extension);
        boolean video = MediaTypes.VIDEO_EXTENSIONS.contains(extension);
        if (!image && !video) {
            throw new BadRequestException("Tip de fișier nepermis. Acceptate: "
                    + MediaTypes.IMAGE_EXTENSIONS + " " + MediaTypes.VIDEO_EXTENSIONS);
        }

        Files.createDirectories(mediaDir);
        String filename = UUID.randomUUID() + extension;
        Path target = mediaDir.resolve(filename);

        try (InputStream in = file.getInputStream()) {
            if (image) {
                imageProcessingService.processImage(in, target);
            } else {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            if (image) {
                throw new BadRequestException("Imaginea nu a putut fi procesată: " + e.getMessage());
            }
            throw e;
        }

        return Map.of("mediaUrl", "/media/" + filename);
    }
}
