package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Clipboard {

    private final List<File> filesToCopy = new LinkedList<>();
    private final List<File> filesToCut = new LinkedList<>();

    public List<File> getCopy() {
        return new ArrayList<>(filesToCopy);
    }

    public void setCopy(List<File> files) {
        clear();
        filesToCopy.addAll(files);
    }

    public boolean isCopy() {
        return !filesToCopy.isEmpty();
    }

    public List<File> getCut() {
        return new ArrayList<>(filesToCut);
    }

    public void setCut(List<File> files) {
        clear();
        filesToCut.addAll(files);
    }

    public boolean isCut() {
        return !filesToCut.isEmpty();
    }

    public void clear() {
        filesToCut.clear();
        filesToCopy.clear();
    }
}
