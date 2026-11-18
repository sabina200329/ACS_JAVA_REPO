package com.instakill.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessingServiceTest {

    private final ImageProcessingService service = new ImageProcessingService();

    @TempDir
    Path tempDir;

    @Test
    void scalesWideImageTo300pxKeepingAspectRatio() throws IOException {
        Path target = tempDir.resolve("wide.png");

        service.processImage(png(1200, 800), target);

        BufferedImage result = ImageIO.read(target.toFile());
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(200);
    }

    @Test
    void doesNotUpscaleSmallImage() throws IOException {
        Path target = tempDir.resolve("small.png");

        service.processImage(png(200, 100), target);

        BufferedImage result = ImageIO.read(target.toFile());
        assertThat(result.getWidth()).isEqualTo(200);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    void addsWatermarkInBottomRightCorner() throws IOException {
        Path target = tempDir.resolve("watermarked.png");

        service.processImage(png(600, 400), target);

        // Imaginea originală e complet albastră; watermark-ul schimbă pixeli în colțul dreapta-jos.
        BufferedImage result = ImageIO.read(target.toFile());
        int blue = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getRGB(0, 0) | 0x0000FF;
        boolean changed = false;
        for (int x = result.getWidth() - 130; x < result.getWidth() && !changed; x++) {
            for (int y = result.getHeight() - 24; y < result.getHeight(); y++) {
                if ((result.getRGB(x, y) & 0xFFFFFF) != (blue & 0xFFFFFF)) {
                    changed = true;
                    break;
                }
            }
        }
        assertThat(changed).isTrue();
        // colțul stânga-sus rămâne neatins
        assertThat(result.getRGB(0, 0) & 0xFFFFFF).isEqualTo(blue & 0xFFFFFF);
    }

    @Test
    void rejectsNonImageContent() {
        Path target = tempDir.resolve("fake.png");

        assertThatThrownBy(() -> service.processImage(new ByteArrayInputStream("not an image".getBytes()), target))
                .isInstanceOf(IOException.class);
    }

    static ByteArrayInputStream png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, 0x0000FF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
