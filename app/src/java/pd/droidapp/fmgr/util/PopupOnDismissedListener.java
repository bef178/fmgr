package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Collection;

@FunctionalInterface
public interface PopupOnDismissedListener {

    void accept(Collection<String> addedItems, Collection<File> removedItems);
}
