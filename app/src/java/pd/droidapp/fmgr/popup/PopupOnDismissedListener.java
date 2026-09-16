package pd.droidapp.fmgr.popup;

import java.util.Collection;

import pd.droidapp.fmgr.util.FileProperties;

@FunctionalInterface
public interface PopupOnDismissedListener {

    void accept(Collection<FileProperties> addedItems, Collection<FileProperties> removedItems);
}
