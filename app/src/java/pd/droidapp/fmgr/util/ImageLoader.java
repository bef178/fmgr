package pd.droidapp.fmgr.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.caverock.androidsvg.RenderOptions;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

public class ImageLoader {

    private static final float SVG_FALLBACK_SIZE = 512f;

    public boolean supports(String mimeType) {
        if (mimeType != null) {
            switch (mimeType) {
                case "image/jpeg":
                case "image/png":
                case "image/webp":
                case "image/gif":
                case "image/bmp":
                case "image/heif":
                case "image/svg+xml":
                    return true;
                case "image/avif":
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
                default:
                    break;
            }
        }
        return false;
    }

    public int[] readImageSourceResolution(String path, String mimeType) {
        if ("image/svg+xml".equals(mimeType)) {
            return readSvgSourceResolution(path);
        }
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(new File(path));
            int[] resolution = new int[2];
            ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                resolution[0] = info.getSize().getWidth();
                resolution[1] = info.getSize().getHeight();
                decoder.setTargetSize(1, 1);
            });
            return resolution;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int[] readSvgSourceResolution(String path) {
        try (InputStream input = new FileInputStream(path)) {
            SVG svg = SVG.getFromInputStream(input);
            float width = svg.getDocumentWidth();
            float height = svg.getDocumentHeight();
            if (width <= 0 || height <= 0) {
                RectF viewBox = svg.getDocumentViewBox();
                if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                    width = viewBox.width();
                    height = viewBox.height();
                } else {
                    width = height = SVG_FALLBACK_SIZE;
                }
            }
            return new int[] {
                    Math.max(1, Math.round(width)),
                    Math.max(1, Math.round(height))};
        } catch (Throwable ignored) {
            return null;
        }
    }

    public DrawableEntry decodeToDrawable(String path, String mimeType, int targetWidth, int targetHeight, FitMode fitMode) {
        if ("image/gif".equals(mimeType) || "image/webp".equals(mimeType)) {
            return decodeAnimatedToDrawable(path, targetWidth, targetHeight, fitMode);
        }
        return decodeToBitmapDrawable(path, mimeType, targetWidth, targetHeight, fitMode);
    }

    private DrawableEntry decodeAnimatedToDrawable(String path, int targetWidth, int targetHeight, FitMode fitMode) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(new File(path));
            int[] resolution = new int[2];
            Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                resolution[0] = info.getSize().getWidth();
                resolution[1] = info.getSize().getHeight();
                FitFrame fitFrame = fitMode.fit(resolution[0], resolution[1], targetWidth, targetHeight);
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSize(fitFrame.scaledWidth, fitFrame.scaledHeight);
                decoder.setCrop(fitFrame.cropRect);
            });
            return new DrawableEntry(resolution[0], resolution[1], drawable);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public DrawableEntry decodeToBitmapDrawable(String path, String mimeType, int targetWidth, int targetHeight, FitMode fitMode) {
        if (!supports(mimeType)) {
            return null;
        }
        try {
            if ("image/svg+xml".equals(mimeType)) {
                Bitmap bitmap = decodeSvgToBitmap(path, targetWidth, targetHeight, fitMode);
                return new DrawableEntry(bitmap.getWidth(), bitmap.getHeight(), bitmap);
            }
            ImageDecoder.Source source = ImageDecoder.createSource(new File(path));
            int[] sourceResolution = new int[2];
            Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                sourceResolution[0] = info.getSize().getWidth();
                sourceResolution[1] = info.getSize().getHeight();
                FitFrame fitFrame = fitMode.fit(sourceResolution[0], sourceResolution[1], targetWidth, targetHeight);
                decoder.setTargetSize(fitFrame.scaledWidth, fitFrame.scaledHeight);
                decoder.setCrop(fitFrame.cropRect);
            });
            return new DrawableEntry(sourceResolution[0], sourceResolution[1], bitmap);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Bitmap decodeSvgToBitmap(String path, int targetWidth, int targetHeight, FitMode fitMode) throws IOException, SVGParseException {
        SVG svg;
        try (InputStream input = new FileInputStream(path)) {
            svg = SVG.getFromInputStream(input);
        }

        float sourceWidth = svg.getDocumentWidth();
        float sourceHeight = svg.getDocumentHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            RectF viewBox = svg.getDocumentViewBox();
            if (viewBox != null && viewBox.width() > 0 && viewBox.height() > 0) {
                sourceWidth = viewBox.width();
                sourceHeight = viewBox.height();
            } else {
                sourceWidth = sourceHeight = SVG_FALLBACK_SIZE;
            }
        }

        FitFrame fitFrame = fitMode.fit(sourceWidth, sourceHeight, targetWidth, targetHeight);
        Bitmap bitmap = Bitmap.createBitmap(fitFrame.cropRect.width(), fitFrame.cropRect.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-fitFrame.cropRect.left, -fitFrame.cropRect.top);
        svg.renderToCanvas(canvas, new RenderOptions().viewPort(0f, 0f, fitFrame.scaledWidth, fitFrame.scaledHeight));
        return bitmap;
    }

    public enum FitMode {
        CENTER_CROP {
            @Override
            FitFrame fit(float sourceWidth, float sourceHeight, int targetWidth, int targetHeight) {
                float scale = Math.max(targetWidth / sourceWidth, targetHeight / sourceHeight);
                int scaledWidth = Math.max(1, Math.round(sourceWidth * scale));
                int scaledHeight = Math.max(1, Math.round(sourceHeight * scale));
                int left = (scaledWidth - targetWidth) / 2;
                int top = (scaledHeight - targetHeight) / 2;
                return new FitFrame(scaledWidth, scaledHeight, new Rect(left, top, left + targetWidth, top + targetHeight));
            }
        },
        FIT_CENTER {
            @Override
            FitFrame fit(float sourceWidth, float sourceHeight, int targetWidth, int targetHeight) {
                float scale = Math.min(targetWidth / sourceWidth, targetHeight / sourceHeight);
                int scaledWidth = Math.max(1, Math.round(sourceWidth * scale));
                int scaledHeight = Math.max(1, Math.round(sourceHeight * scale));
                return new FitFrame(scaledWidth, scaledHeight, new Rect(0, 0, scaledWidth, scaledHeight));
            }
        };

        abstract FitFrame fit(float sourceWidth, float sourceHeight, int targetWidth, int targetHeight);
    }

    private static final class FitFrame {

        final int scaledWidth;
        final int scaledHeight;
        final Rect cropRect;

        FitFrame(int scaledWidth, int scaledHeight, Rect cropRect) {
            this.scaledWidth = scaledWidth;
            this.scaledHeight = scaledHeight;
            this.cropRect = cropRect;
        }
    }

    public static class DrawableEntry {

        public final int sourceWidth;
        public final int sourceHeight;
        public final Drawable drawable;

        public DrawableEntry(int sourceWidth, int sourceHeight, Bitmap bitmap) {
            this(sourceWidth, sourceHeight, new BitmapDrawable(Resources.getSystem(), bitmap));
        }

        private DrawableEntry(int sourceWidth, int sourceHeight, Drawable drawable) {
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.drawable = drawable;
        }

        public Bitmap getBitmap() {
            return drawable instanceof BitmapDrawable
                    ? ((BitmapDrawable) drawable).getBitmap()
                    : null;
        }

        public boolean isDownsampled() {
            Bitmap bitmap = getBitmap();
            if (bitmap != null) {
                return bitmap.getWidth() < sourceWidth || bitmap.getHeight() < sourceHeight;
            }
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            return width > 0 && height > 0 && (width < sourceWidth || height < sourceHeight);
        }
    }
}
