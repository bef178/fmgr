package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Collection;

@FunctionalInterface
public interface PopupOnDismissListener {

    void accept(Collection<File> removedFiles);
}
