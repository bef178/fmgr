package pd.droidapp.fmgr.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Clipboard {

    private Map<String, FileProperties> itemsToCopy = Collections.emptyMap();
    private Map<String, FileProperties> itemsToCut = Collections.emptyMap();

    public synchronized List<FileProperties> getItemsToCopy() {
        return new ArrayList<>(itemsToCopy.values());
    }

    public synchronized void setItemsToCopy(Collection<FileProperties> items) {
        itemsToCopy = toMap(items);
        itemsToCut = Collections.emptyMap();
    }

    public synchronized boolean toCopy() {
        return !itemsToCopy.isEmpty();
    }

    public synchronized List<FileProperties> getItemsToCut() {
        return new ArrayList<>(itemsToCut.values());
    }

    public synchronized void setItemsToCut(Collection<FileProperties> items) {
        itemsToCopy = Collections.emptyMap();
        itemsToCut = toMap(items);
    }

    public synchronized boolean toCut() {
        return !itemsToCut.isEmpty();
    }

    public synchronized void clear() {
        itemsToCopy = Collections.emptyMap();
        itemsToCut = Collections.emptyMap();
    }

    public synchronized void removeAllIfSameAsOrDescendantOf(Collection<FileProperties> excluded) {
        itemsToCopy = removeAllIfSameAsOrDescendantOf(itemsToCopy, excluded);
        itemsToCut = removeAllIfSameAsOrDescendantOf(itemsToCut, excluded);
    }

    private static Map<String, FileProperties> toMap(Collection<FileProperties> items) {
        Map<String, FileProperties> map = new LinkedHashMap<>();
        for (FileProperties item : items) {
            map.put(item.path, item);
        }
        return map;
    }

    private static Map<String, FileProperties> removeAllIfSameAsOrDescendantOf(
            Map<String, FileProperties> items, Collection<FileProperties> excluded) {
        if (items.isEmpty() || excluded.isEmpty()) {
            return items;
        }
        Map<String, FileProperties> survivors = new LinkedHashMap<>();
        for (Map.Entry<String, FileProperties> entry : items.entrySet()) {
            if (!isSameAsOrDescendantOf(entry.getKey(), excluded)) {
                survivors.put(entry.getKey(), entry.getValue());
            }
        }
        return survivors;
    }

    private static boolean isSameAsOrDescendantOf(String path, Collection<FileProperties> excluded) {
        for (FileProperties item : excluded) {
            if (path.equals(item.path) || path.startsWith(item.path + "/")) {
                return true;
            }
        }
        return false;
    }
}
