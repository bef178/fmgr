package pd.droidapp.fmgr.util;

import android.os.Environment;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

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

    public static Path getAlternativeFile(Path directory, String basename) {
        return getAlternativeFile(directory != null ? directory.toFile() : null, basename).toPath();
    }

    public static File getAlternativeFile(String directory, String basename) {
        return getAlternativeFile(new File(directory), basename);
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

    public static String getDisplayPath(String path) {
        if (path == null) {
            return null;
        }
        return getDisplayPath(new File(path));
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
