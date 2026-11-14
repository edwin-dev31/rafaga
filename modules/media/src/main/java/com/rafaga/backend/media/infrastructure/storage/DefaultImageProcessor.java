package com.rafaga.backend.media.infrastructure.storage;

import com.rafaga.backend.media.domain.service.ImageProcessor;
import com.rafaga.backend.media.domain.service.ProcessingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class DefaultImageProcessor implements ImageProcessor {

    @Value("${media.optimization.max-edge-pixels:4096}")
    private int maxEdgePixels;

    @Value("${media.optimization.min-edge-pixels:640}")
    private int minEdgePixels;

    @Value("${media.optimization.webp-quality:85}")
    private int webpQuality;

    @Value("${media.optimization.webp-quality-step:10}")
    private int webpQualityStep;

    @Value("${media.optimization.webp-quality-floor:60}")
    private int webpQualityFloor;

    @Value("${media.optimization.max-output-size-bytes:5242880}")
    private long maxOutputSizeBytes;

    @Value("${media.optimization.max-input-size-bytes:10485760}")
    private long maxInputSizeBytes;

    private static final byte[][] JPEG_MAGIC = {{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}};
    private static final byte[][] PNG_MAGIC = {{(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47}};
    private static final byte[][] WEBP_MAGIC = {{0x52, 0x49, 0x46, 0x46}, {0x57, 0x45, 0x42, 0x50}};

    @Override
    public ProcessingResult process(InputStream imageStream, String declaredContentType) {
        try {
            // Read entire stream into memory so we can inspect magic bytes
            // and then decode the full image from the beginning
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            imageStream.transferTo(buffer);
            byte[] allBytes = buffer.toByteArray();

            // Security: limit input size to prevent memory exhaustion
            if (allBytes.length > maxInputSizeBytes) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "El archivo supera el límite de " + maxInputSizeBytes + " bytes (" + allBytes.length + " bytes)",
                    "MEDIA_INPUT_TOO_LARGE");
            }

            if (allBytes.length < 12) {
                return new ProcessingResult(null, null, 0, 0, 0, false, "Archivo vacío o demasiado pequeño", "MEDIA_INVALID_FORMAT");
            }

            String actualType = detectContentType(allBytes);
            if (actualType == null) {
                return new ProcessingResult(null, null, 0, 0, 0, false, "Magic bytes no corresponden a una imagen válida", "MEDIA_INVALID_FORMAT");
            }

            if (!actualType.equals(declaredContentType)) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "Content-type declarado (" + declaredContentType + ") no coincide con el real (" + actualType + ")",
                    "MEDIA_CONTENT_TYPE_MISMATCH");
            }

            // Security: use ImageReader to get dimensions BEFORE materializing
            // the full BufferedImage. A small file can declare huge dimensions
            // in its header; ImageReader reads only the metadata, not the pixel data.
            int[] dimensions = getImageDimensions(allBytes, actualType);
            if (dimensions == null) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "No se pudo leer la información de la imagen", "MEDIA_INVALID_FORMAT");
            }
            int width = dimensions[0];
            int height = dimensions[1];

            // Security: limit total pixels to prevent decompression bombs
            long totalPixels = (long) width * height;
            long maxPixels = (long) maxEdgePixels * maxEdgePixels;
            if (totalPixels > maxPixels) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "La imagen tiene demasiados píxeles (" + totalPixels + "), máximo " + maxPixels,
                    "MEDIA_IMAGE_TOO_LARGE");
            }

            // Enforce minimum edge size
            if (width < minEdgePixels && height < minEdgePixels) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "La imagen es demasiado pequeña (" + width + "x" + height + "), mínimo " + minEdgePixels + " píxeles en un lado",
                    "MEDIA_IMAGE_TOO_SMALL");
            }

            // Only now materialize the full BufferedImage — dimensions are safe
            BufferedImage image;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(allBytes)) {
                image = ImageIO.read(bais);
            } catch (Exception e) {
                return new ProcessingResult(null, null, 0, 0, 0, false, "No se pudo leer la imagen: " + e.getMessage(), "MEDIA_INVALID_FORMAT");
            }

            if (image == null) {
                return new ProcessingResult(null, null, 0, 0, 0, false, "No se pudo decodificar la imagen", "MEDIA_INVALID_FORMAT");
            }

            // Resize if exceeds max edge
            if (width > maxEdgePixels || height > maxEdgePixels) {
                int newWidth, newHeight;
                if (width > height) {
                    newWidth = maxEdgePixels;
                    newHeight = (maxEdgePixels * height) / width;
                } else {
                    newHeight = maxEdgePixels;
                    newWidth = (maxEdgePixels * width) / height;
                }
                Image scaled = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                image = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.drawImage(scaled, 0, 0, null);
                g.dispose();
                width = newWidth;
                height = newHeight;
            }

            // Detect if has alpha channel or is a graphic (for lossless)
            boolean hasAlpha = image.getColorModel().hasAlpha();
            boolean isGraphic = !hasAlpha && isFlatGraphic(image);

            // Try to encode with quality ladder
            int quality = webpQuality;
            while (quality >= webpQualityFloor) {
                byte[] result = encodeWebp(image, hasAlpha || isGraphic, quality);
                if (result != null && result.length <= maxOutputSizeBytes) {
                    return new ProcessingResult(result, "image/webp", width, height, result.length, true, null, null);
                }
                quality -= webpQualityStep;
            }

            // Last attempt - try lossy at floor quality
            byte[] lastAttempt = encodeWebp(image, false, webpQualityFloor);
            if (lastAttempt != null) {
                return new ProcessingResult(null, null, 0, 0, 0, false,
                    "La imagen sigue superando el límite de " + maxOutputSizeBytes + " bytes tras la optimización",
                    "MEDIA_TOO_LARGE_AFTER_OPTIMIZATION");
            }

            return new ProcessingResult(null, null, 0, 0, 0, false,
                "No se pudo codificar la imagen en WebP", "MEDIA_ENCODING_ERROR");

        } catch (IOException e) {
            return new ProcessingResult(null, null, 0, 0, 0, false, "Error al procesar la imagen: " + e.getMessage(), "MEDIA_PROCESSING_ERROR");
        }
    }

    private String detectContentType(byte[] header) {
        if (header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 4 && header[0] == (byte) 0x89 && header[1] == (byte) 0x50
            && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            return "image/png";
        }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
            && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B'
            && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /**
     * Read image dimensions from the header using ImageReader without
     * materializing the full pixel buffer. This prevents decompression bombs:
     * a small file can declare huge dimensions in its header; ImageReader
     * reads only the metadata, not the pixel data.
     */
    private int[] getImageDimensions(byte[] data, String contentType) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isFlatGraphic(BufferedImage image) {
        // Simple heuristic: check if image has few unique colors
        // For now, assume branding assets are graphics
        return image.getWidth() * image.getHeight() < 100000;
    }

    private byte[] encodeWebp(BufferedImage image, boolean lossless, int quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Use ImageIO with WebP writer if available, otherwise fallback
            // The webp-imageio library registers itself via SPI
            javax.imageio.ImageWriter writer = null;
            for (var iter = ImageIO.getImageWritersByFormatName("webp"); iter.hasNext(); ) {
                writer = iter.next();
                break;
            }

            if (writer == null) {
                // No WebP encoder available — fail explicitly rather than
                // silently writing PNG under a .webp extension.
                return null;
            }

            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            if (!lossless && param.canWriteCompressed()) {
                param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality / 100.0f);
            }

            javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            ios.flush();
            ios.close();
            writer.dispose();

            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
