package pd.droidapp.fmgr.popup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.FileStat;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.encode;
import static pd.droidapp.fmgr.util.Util.toFileProperties;
import static pd.util.Int8ArrayExtension.indexOf;

class SearchWorker extends ProcessingWorker {

    private OnUpdatedListener onUpdated;
    private final Set<String> allNameMatched = new HashSet<>();
    private List<FileProperties> matched = new LinkedList<>();
    private int scanned = 0;
    private final Object lock = new Object();

    public void whenUpdated(OnUpdatedListener onUpdated) {
        this.onUpdated = onUpdated;
    }

    public boolean start(String startDirectory, String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        return start(() -> {
            scanNames(startDirectory, query);
            if (isCancelled()) {
                return;
            }
            scanContents(startDirectory, query);
        });
    }

    private void scanNames(String startDirectory, String query) {
        FileOps.singleton.listDirectory(startDirectory, 32, false, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean hit = PathOps.singleton.basename(src).contains(query)
                                && !FileOps.singleton.stat(src).isSymlink();
                        if (hit) {
                            allNameMatched.add(src);
                        }
                        accumulate(src, hit);
                    }
                });
    }

    private void accumulate(String path, boolean hit) {
        synchronized (lock) {
            scanned++;
            if (hit) {
                matched.add(toFileProperties(path));
            }
        }
    }

    private void scanContents(String startDirectory, String query) {
        final String[] charsets = {"UTF-8", "GB18030"};
        List<byte[]> needles = encode(query, charsets);
        if (needles.isEmpty()) {
            return;
        }
        byte[] buffer = new byte[256 * 1024]; // avoid to allocate for every file
        FileOps.singleton.listDirectory(startDirectory, 32, false, cancelRequested,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        boolean hit = !src.endsWith("/")
                                && !allNameMatched.contains(src)
                                && searchSmallFileOrTextFile(src, needles, buffer);
                        accumulate(src, hit);
                    }
                });
    }

    private boolean searchSmallFileOrTextFile(String path, List<byte[]> needles, byte[] buffer) {
        FileStat srcStat = FileOps.singleton.stat(path);
        if (srcStat.isFile(false) && (srcStat.size <= 1024 * 1024 || isTextFile(path))) {
            return searchStreamContent(path, needles, buffer);
        }
        return false;
    }

    private boolean isTextFile(String path) {
        String lowerBasename = PathOps.singleton.basename(path).toLowerCase();
        if (lowerBasename.startsWith(".")) {
            return false;
        }
        return lowerBasename.endsWith(".txt") || lowerBasename.endsWith(".md") || lowerBasename.endsWith(".json") ||
                lowerBasename.endsWith(".xml") || lowerBasename.endsWith(".html") || lowerBasename.endsWith(".css") ||
                lowerBasename.endsWith(".js") || lowerBasename.endsWith(".java") || lowerBasename.endsWith(".kt") ||
                lowerBasename.endsWith(".py") || lowerBasename.endsWith(".c") || lowerBasename.endsWith(".cpp") ||
                lowerBasename.endsWith(".h") || lowerBasename.endsWith(".hpp") || lowerBasename.endsWith(".sh") ||
                lowerBasename.endsWith(".yaml") || lowerBasename.endsWith(".yml") || lowerBasename.endsWith(".properties") ||
                lowerBasename.endsWith(".gradle") || lowerBasename.endsWith(".csv") || lowerBasename.endsWith(".log");
    }

    private boolean searchStreamContent(String path, List<byte[]> needles, byte[] buffer) {
        int carryLength = needles.stream().mapToInt(needle -> needle.length).max().orElse(0);
        try (InputStream inputStream = Files.newInputStream(Paths.get(path))) {
            int startIndex = 0;
            int nRead;
            while ((nRead = inputStream.read(buffer, startIndex, buffer.length - startIndex)) > 0) {
                if (isCancelled()) {
                    return false;
                }
                int endIndex = startIndex + nRead;
                for (byte[] needle : needles) {
                    if (indexOf(buffer, 0, endIndex, needle, 0, needle.length) >= 0) {
                        return true;
                    }
                }
                if (endIndex > carryLength) {
                    // keep the tail
                    System.arraycopy(buffer, endIndex - carryLength, buffer, 0, carryLength);
                    startIndex = carryLength;
                } else {
                    startIndex = endIndex;
                }
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    @Override
    protected void reportUpdated() {
        int nowScanned;
        List<FileProperties> nowMatched;
        synchronized (lock) {
            nowScanned = scanned;
            scanned = 0;
            nowMatched = matched;
            matched = new LinkedList<>();
        }
        if (onUpdated != null) {
            try {
                onUpdated.accept(nowScanned, nowMatched);
            } catch (Throwable ignored) {
            }
        }
    }

    public interface OnUpdatedListener {
        void accept(int scanned, List<FileProperties> matched);
    }
}
