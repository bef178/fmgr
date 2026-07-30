package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Collection;

public interface PopupOnClosed {

    /**
     * pass out removed files for the owner to invalidate its file list
     */
    void onClosed(Collection<File> removedFiles);
}
