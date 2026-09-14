package pd.droidapp.fmgr.popup;

import java.util.Collection;

@FunctionalInterface
public interface PopupOnDismissedListener {

    void accept(Collection<String> addedItems, Collection<String> removedItems);
}
