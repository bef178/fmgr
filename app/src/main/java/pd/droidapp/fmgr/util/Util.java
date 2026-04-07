package pd.droidapp.fmgr.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * `src` must exist<br/>
     * `dst` must not exist<br/>
     */
    public static boolean copyRecursively(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists() || dst.exists()) {
            return false;
        }
        if (abortRequested != null && abortRequested.get()) {
            return false;
        }
        if (src.isDirectory()) {
            if (!dst.mkdirs()) {
                return false;
            }
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    if (abortRequested != null && abortRequested.get()) {
                        return false;
                    }
                    File srcChild = new File(src, child);
                    File dstChild = new File(dst, child);
                    if (!copyRecursively(srcChild, dstChild, abortRequested)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            if (abortRequested != null && abortRequested.get()) {
                return false;
            }
            try (FileInputStream srcStream = new FileInputStream(src);
                 FileOutputStream dstStream = new FileOutputStream(dst)) {
                byte[] buffer = new byte[8192];
                int nRead;
                while ((nRead = srcStream.read(buffer)) > 0) {
                    if (abortRequested != null && abortRequested.get()) {
                        boolean ignored = dst.delete();
                        return false;
                    }
                    dstStream.write(buffer, 0, nRead);
                }
                return true;
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    /**
     * `src` must exist<br/>
     * `dst` must not exist<br/>
     */
    public static boolean move(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists() || dst.exists()) {
            return false;
        }
        // TODO check cross-filesystem and take use of `abortRequested`
        return src.renameTo(dst);
    }

    public static boolean removeRecursively(File src, AtomicBoolean abortRequested) {
        if (abortRequested != null && abortRequested.get()) {
            return false;
        }
        if (src.isDirectory()) {
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (abortRequested != null && abortRequested.get()) {
                        return false;
                    }
                    if (!removeRecursively(child, abortRequested)) {
                        return false;
                    }
                }
            }
        }
        return src.delete();
    }

    public static boolean copySafeReplace(File src, File dst, AtomicBoolean abortRequested) {
        if (!src.exists()) {
            return false;
        }
        File sflDst = null;
        try {
            if (dst.exists()) {
                sflDst = getAlternativeFile(dst.getParentFile(), ".tmp_" + dst.getName());
                if (!move(dst, sflDst, abortRequested)) {
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
                boolean ignored = move(sflDst, dst, abortRequested);
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
                if (!move(dst, sflDst, abortRequested)) {
                    return false;
                }
            }
            if (!move(src, dst, abortRequested)) {
                throw new RuntimeException("failed to rename src to dst, rollback");
            }
            if (sflDst != null) {
                boolean ignored = removeRecursively(sflDst, abortRequested);
            }
            return true;
        } catch (Exception e) {
            if (sflDst != null && sflDst.exists()) {
                boolean ignored = move(sflDst, dst, abortRequested);
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
