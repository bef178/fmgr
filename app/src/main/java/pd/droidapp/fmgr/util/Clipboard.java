package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Clipboard {

    private List<File> filesToCopy = Collections.emptyList();
    private List<File> filesToCut = Collections.emptyList();

    public synchronized List<File> getFilesToCopy() {
        return new ArrayList<>(filesToCopy);
    }

    public synchronized void setFilesToCopy(List<File> files) {
        filesToCopy = new ArrayList<>(files);
        filesToCut = Collections.emptyList();
    }

    public synchronized boolean toCopy() {
        return !filesToCopy.isEmpty();
    }

    public synchronized List<File> getFilesToCut() {
        return new ArrayList<>(filesToCut);
    }

    public synchronized void setFilesToCut(List<File> files) {
        filesToCopy = Collections.emptyList();
        filesToCut = new ArrayList<>(files);
    }

    public synchronized boolean toCut() {
        return !filesToCut.isEmpty();
    }

    public synchronized void clear() {
        filesToCopy = Collections.emptyList();
        filesToCut = Collections.emptyList();
    }

    public synchronized void removeAllIfSameAsOrDescendantOf(Collection<File> excluded) {
        filesToCopy = removeAllIfSameAsOrDescendantOf(filesToCopy, excluded);
        filesToCut = removeAllIfSameAsOrDescendantOf(filesToCut, excluded);
    }

    private static List<File> removeAllIfSameAsOrDescendantOf(List<File> files, Collection<File> excluded) {
        if (files.isEmpty() || excluded.isEmpty()) {
            return files;
        }
        List<File> survivors = new ArrayList<>();
        for (File file : files) {
            if (!isSameAsOrDescendantOf(file, excluded)) {
                survivors.add(file);
            }
        }
        return survivors;
    }

    private static boolean isSameAsOrDescendantOf(File file, Collection<File> excluded) {
        String path = file.getAbsolutePath();
        for (File file2 : excluded) {
            if (file.equals(file2)) {
                return true;
            }
            String path2 = file2.getAbsolutePath();
            if (path.startsWith(path2 + File.separator)) {
                return true;
            }
        }
        return false;
    }
}
