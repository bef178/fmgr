package pd.droidapp.fmgr.util;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FileGrouper {

    // grouped by file size
    private final Map<Long, List<File>> sizedFiles = new LinkedHashMap<>();
    private final Map<File, FileProperties> fileProperties = new HashMap<>();

    public synchronized void add(File file) {
        if (fileProperties.containsKey(file)) {
            return;
        }

        long size = file.length();
        List<File> a = sizedFiles.computeIfAbsent(size, k -> new LinkedList<>());
        if (a.isEmpty()) {
            a.add(file);
            fileProperties.computeIfAbsent(file, k -> {
                FileProperties fileProps = new FileProperties();
                fileProps.file = file;
                fileProps.size = size;
                return fileProps;
            });
        } else {
            if (a.size() == 1) {
                // delay calc checksum
                File f = a.get(0);
                FileProperties fProps = fileProperties.get(f);
                Objects.requireNonNull(fProps);
                if (fProps.md5sum == null) {
                    fProps.md5sum = Util.getFileMd5(f);
                }
            }

            a.add(file);
            fileProperties.computeIfAbsent(file, k -> {
                FileProperties fileProps = new FileProperties();
                fileProps.file = file;
                fileProps.size = size;
                fileProps.md5sum = Util.getFileMd5(file);
                return fileProps;
            });
        }
    }

    public synchronized void remove(File file) {
        if (fileProperties.containsKey(file)) {
            FileProperties fileProps = Objects.requireNonNull(fileProperties.remove(file));
            List<File> bySize = Objects.requireNonNull(sizedFiles.get(fileProps.size));
            bySize.remove(file);
            if (bySize.isEmpty()) {
                sizedFiles.remove(fileProps.size);
            }
        }
    }

    public synchronized void removeAll(Collection<File> files) {
        for (File file : files) {
            remove(file);
        }
    }

    /**
     * Grouped by both file size and file md5sum, keep the order
     */
    synchronized List<List<FileProperties>> getDupGroups() {
        List<List<FileProperties>> groups = new LinkedList<>();
        for (List<File> bySize : sizedFiles.values()) {
            if (bySize.size() == 1) {
                continue;
            }
            Map<String, List<FileProperties>> byChecksum = new LinkedHashMap<>();
            for (File file : bySize) {
                FileProperties fileProps = Objects.requireNonNull(fileProperties.get(file));
                byChecksum.computeIfAbsent(fileProps.md5sum, k -> new LinkedList<>()).add(fileProps);
            }
            for (List<FileProperties> group : byChecksum.values()) {
                if (group.size() > 1) {
                    groups.add(group);
                }
            }
        }
        return groups;
    }

    public static class FileProperties {
        File file;
        long size;
        String md5sum;
    }
}
