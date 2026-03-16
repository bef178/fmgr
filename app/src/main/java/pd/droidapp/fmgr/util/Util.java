package pd.droidapp.fmgr.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class Util {

    public static String basename(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getFileMd5(File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return bytesToHexString(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
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
            String basePath = start.getCanonicalPath();
            String filePath = dst.getCanonicalPath();
            if (filePath.startsWith(basePath)) {
                String relative = filePath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.isEmpty() ? dst.getName() : relative;
            }
            return dst.getAbsolutePath();
        } catch (Exception e) {
            return dst.getAbsolutePath();
        }
    }

    public static float getGaussianValue(double mu, double sigma, float amplitude, float fraction) {
        // f(x) = A * exp(-(x-μ)² / (2σ²))
        double exponent = -Math.pow(fraction - mu, 2) / (2 * sigma * sigma);
        return (float) (amplitude * Math.exp(exponent));
    }
}
