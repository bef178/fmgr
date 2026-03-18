package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Clipboard {

    private List<File> filesToCopy = Collections.emptyList();
    private List<File> filesToCut = Collections.emptyList();

    public synchronized List<File> getCopy() {
        return new ArrayList<>(filesToCopy);
    }

    public synchronized void setCopy(List<File> files) {
        filesToCut = Collections.emptyList();
        filesToCopy = new ArrayList<>(files);
    }

    public synchronized boolean isCopy() {
        return !filesToCopy.isEmpty();
    }

    public synchronized List<File> getCut() {
        return new ArrayList<>(filesToCut);
    }

    public synchronized void setCut(List<File> files) {
        filesToCopy = Collections.emptyList();
        filesToCut = new ArrayList<>(files);
    }

    public synchronized boolean isCut() {
        return !filesToCut.isEmpty();
    }

    public synchronized void clear() {
        filesToCut = Collections.emptyList();
        filesToCopy = Collections.emptyList();
    }
}
