package pd.droidapp.fmgr.util;

import android.os.Environment;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import pd.util.FileOps;

public class Util {

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

    public static float getGaussianValue(double mu, double sigma, float amplitude, float fraction) {
        // f(x) = A * exp(-(x-μ)² / (2σ²))
        double exponent = -Math.pow(fraction - mu, 2) / (2 * sigma * sigma);
        return (float) (amplitude * Math.exp(exponent));
    }

    public static boolean copySafeReplace(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists()) {
            return false;
        }
        File tmpDst = null;
        try {
            if (dst.exists()) {
                tmpDst = getAlternativeFile(dst.getParentFile(), ".tmp_" + dst.getName());
                if (!moveRecursively(dst, tmpDst, abortRequested)) {
                    return false;
                }
            }
            if (!copyRecursively(src, dst, abortRequested)) {
                throw new RuntimeException("failed to copy src to dst, rollback");
            }
            if (tmpDst != null) {
                boolean ignored = removeRecursively(tmpDst, abortRequested);
            }
            return true;
        } catch (Exception e) {
            if (tmpDst != null && tmpDst.exists()) {
                boolean ignored = moveRecursively(tmpDst, dst, abortRequested);
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

    private static boolean copyRecursively(File src, File dst, AtomicBoolean abortRequested) {
        if (src.isDirectory()) {
            return FileOps.singleton.copyDirectory(src.getAbsolutePath(), dst.getAbsolutePath(), abortRequested, null);
        }
        return FileOps.singleton.copyFile(src.getAbsolutePath(), dst.getAbsolutePath(), abortRequested);
    }

    private static boolean moveRecursively(File src, File dst, AtomicBoolean abortRequested) {
        if (src.isDirectory()) {
            return FileOps.singleton.moveDirectory(src.getAbsolutePath(), dst.getAbsolutePath(), abortRequested, null, null, null);
        }
        return FileOps.singleton.moveFile(src.getAbsolutePath(), dst.getAbsolutePath(), abortRequested);
    }

    private static boolean removeRecursively(File file, AtomicBoolean abortRequested) {
        if (file.isDirectory()) {
            return FileOps.singleton.removeDirectory(file.getAbsolutePath(), true, false, abortRequested, null);
        }
        return FileOps.singleton.removeFile(file.getAbsolutePath());
    }

    /**
     * display path for UI
     * `/storage/emulated/0` => `/`
     * `/storage/emulated/0/xxx` => `/xxx`
     */
    public static String getDisplayPath(File path) {
        if (path == null) {
            return "";
        }
        File root = Environment.getExternalStorageDirectory();
        String rootPath = root.getAbsolutePath();
        String absPath = path.getAbsolutePath();
        if (absPath.equals(rootPath)) {
            return "/";
        }
        if (absPath.startsWith(rootPath + File.separator)) {
            return absPath.substring(rootPath.length());
        }
        return absPath;
    }
}
