package com.instakill.media;

import java.util.Locale;
import java.util.Set;

/**
 * Extensiile acceptate la upload și detectarea foto/video după URL.
 */
public final class MediaTypes {

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".webm");

    private MediaTypes() {
    }

    /** Extensia cu punct, litere mici (ex. ".mp4"), sau "" dacă lipsește. */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String clean = filename;
        int query = clean.indexOf('?');
        if (query >= 0) {
            clean = clean.substring(0, query);
        }
        int dot = clean.lastIndexOf('.');
        int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (dot < 0 || dot < slash) {
            return "";
        }
        return clean.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static boolean isImage(String filename) {
        return IMAGE_EXTENSIONS.contains(extensionOf(filename));
    }

    public static boolean isVideo(String filename) {
        return VIDEO_EXTENSIONS.contains(extensionOf(filename));
    }
}
