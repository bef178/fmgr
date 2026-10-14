package pd.droidapp.fmgr.util;

import java.io.Serializable;

public class DirectoryScrollState implements Serializable {

    public final String directory;
    public int scrollPosition = -1;
    public int scrollOffset;

    public DirectoryScrollState(String directory) {
        this.directory = directory;
    }
}
