package pd.droidapp.fmgr.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.RectF;
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

    public Bitmap decodeToBitmap(String path, String mimeType, int targetWidth, int targetHeight, FitMode fitMode)
            throws IOException, SVGParseException {
        if (!supports(mimeType)) {
            return null;
        }
        if ("image/svg+xml".equals(mimeType)) {
            return decodeSvgToBitmap(path, targetWidth, targetHeight, fitMode);
        }
        ImageDecoder.Source source = ImageDecoder.createSource(new File(path));
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int sourceWidth = info.getSize().getWidth();
            int sourceHeight = info.getSize().getHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return;
            }
            FitFrame fitFrame = fitMode.fit(sourceWidth, sourceHeight, targetWidth, targetHeight);
            decoder.setTargetSize(fitFrame.scaledWidth, fitFrame.scaledHeight);
            decoder.setCrop(fitFrame.cropRect);
        });
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

        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
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
}
