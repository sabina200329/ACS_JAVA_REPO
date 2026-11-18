package com.instakill.media;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Procesează imaginile încărcate: le micșorează la maximum 300px lățime
 * (păstrând proporțiile) și adaugă watermark-ul "Instakill Demo" în dreapta-jos.
 *
 * Nu depinde de HTTP sau de baza de date, deci poate fi mutat ușor
 * într-un worker separat (ex. consumator dintr-o coadă de mesaje).
 */
@Service
public class ImageProcessingService {

    public static final int MAX_WIDTH = 300;
    public static final String WATERMARK_TEXT = "Instakill Demo";

    public void processImage(InputStream input, Path target) throws IOException {
        BufferedImage original = ImageIO.read(input);
        if (original == null) {
            throw new IOException("Fișierul nu este o imagine validă");
        }

        // Micșorăm doar dacă e mai lată de 300px; imaginile mici nu se măresc.
        int width = Math.min(original.getWidth(), MAX_WIDTH);

        Thumbnails.of(original)
                .width(width)
                .keepAspectRatio(true)
                .watermark(Positions.BOTTOM_RIGHT, createWatermark(), 0.8f)
                .toFile(target.toFile());
    }

    private BufferedImage createWatermark() {
        BufferedImage watermark = new BufferedImage(130, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = watermark.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        // umbră neagră + text alb, ca să se vadă pe orice fundal
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(WATERMARK_TEXT, 7, 18);
        g.setColor(new Color(255, 255, 255, 220));
        g.drawString(WATERMARK_TEXT, 6, 17);
        g.dispose();
        return watermark;
    }
}
