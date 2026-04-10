package pd.droidapp.fmgr.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import pd.util.DigestCodec;
import pd.util.PathExtension;

import static pd.util.FileExtension.copyRecursively;
import static pd.util.FileExtension.moveRecursively;
import static pd.util.FileExtension.removeRecursively;
import static pd.util.PathExtension.relativize;

public class Util {

    public static String getFileMd5(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return DigestCodec.md5().checksum(inputStream);
        } catch (IOException ignored) {
            return null;
        }
    }

    public static String getSizeString(long size) {
        if (size < 1024) {
            return size + "B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        if (exp > 6) {
            exp = 6;
        }
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f%sB", size / Math.pow(1024, exp), unit);
    }

    /**
     * cd `start`; cd `relative` => `dst`
     */
    public static String getRelativePath(File start, File dst) {
        try {
            return relativize(start.getCanonicalPath(), dst.getCanonicalPath());
        } catch (Exception e) {
            return dst.getAbsolutePath();
        }
    }

    public static float getGaussianValue(double mu, double sigma, float amplitude, float fraction) {
        // f(x) = A * exp(-(x-μ)² / (2σ²))
        double exponent = -Math.pow(fraction - mu, 2) / (2 * sigma * sigma);
        return (float) (amplitude * Math.exp(exponent));
    }

    public static boolean copySafeReplace(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists()) {
            return false;
        }
        File sflDst = null;
        try {
            if (dst.exists()) {
                sflDst = getAlternativeFile(dst.getParentFile(), ".tmp_" + dst.getName());
                if (!moveRecursively(dst, sflDst, abortRequested)) {
                    return false;
                }
            }
            if (!copyRecursively(src, dst, abortRequested)) {
                throw new RuntimeException("failed to copy src to dst, rollback");
            }
            if (sflDst != null) {
                boolean ignored = removeRecursively(sflDst, abortRequested);
            }
            return true;
        } catch (Exception e) {
            if (sflDst != null && sflDst.exists()) {
                boolean ignored = moveRecursively(sflDst, dst, abortRequested);
            }
        }
        return false;
    }

    public static boolean moveSafeReplace(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists()) {
            return false;
        }
        File sflDst = null;
        try {
            if (dst.exists()) {
                if (Files.isSameFile(src.toPath(), dst.toPath())) {
                    return true;
                }
                sflDst = getAlternativeFile(dst.getParentFile(), ".tmp_" + dst.getName());
                if (!moveRecursively(dst, sflDst, abortRequested)) {
                    return false;
                }
            }
            if (!moveRecursively(src, dst, abortRequested)) {
                throw new RuntimeException("failed to rename src to dst, rollback");
            }
            if (sflDst != null) {
                boolean ignored = removeRecursively(sflDst, abortRequested);
            }
            return true;
        } catch (Exception e) {
            if (sflDst != null && sflDst.exists()) {
                boolean ignored = moveRecursively(sflDst, dst, abortRequested);
            }
        }
        return false;
    }

    public static File getAlternativeFile(File directory, String basename) {
        if (directory == null) {
            directory = new File("");
        }
        File f = new File(directory, basename);
        if (!f.exists()) {
            return f;
        }

        String name;
        String extension;
        {
            int i = basename.indexOf('.');
            if (i > 0) {
                name = basename.substring(0, i);
                extension = basename.substring(i);
            } else {
                name = basename;
                extension = "";
            }
        }

        int counter = 2;
        File candidate;
        do {
            String newName = name + " (" + counter + ")" + extension;
            candidate = new File(directory, newName);
            counter++;
        } while (candidate.exists());

        return candidate;
    }
}
