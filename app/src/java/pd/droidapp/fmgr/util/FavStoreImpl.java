package pd.droidapp.fmgr.util;

import java.util.List;

import pd.droidapp.fmgr.util.FavoritesStore.FavItem;

interface FavStoreImpl {

    boolean contains(String path);

    FavItem get(String path);

    List<FavItem> getAll();

    void put(String path);

    void put(String path, String name);

    void remove(String path);
}
