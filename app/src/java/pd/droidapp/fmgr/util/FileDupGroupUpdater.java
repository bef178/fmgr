package pd.droidapp.fmgr.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import pd.util.DigestCodec;

class FileDupGroupUpdater {

    private static final int updateInterval = 1000;

    private final Executor mainThread;
    private final FileScanUpdater scanner = new FileScanUpdater(updateInterval);

    private Runnable onGroupStarted;
    private OnGroupUpdatedListener onGroupUpdated;
    private Runnable onGroupStopped;

    private final Map<String, FileProperties> allFileProperties = new HashMap<>();
    private final Map<Long, List<FileProperties>> bySizeFileProperties = new LinkedHashMap<>();
    private final Map<String, List<FileProperties>> byChecksumFileProperties = new LinkedHashMap<>(); // key(checksum_size)
    private final ThreadPoolExecutor checksumThread = new ThreadPoolExecutor(
            0, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private Timer checksumTimer;
    private int pendingChecksums = 0;
    private boolean cancelled = false;
    private boolean onGroupStoppedCalled = false;
    private int totalScanned = 0;
    private int totalGroups = 0;
    private int totalGroupItems = 0;

    public FileDupGroupUpdater(Executor mainThread) {
        this.mainThread = mainThread;
    }

    public void whenGroupStarted(Runnable onGroupStarted) {
        this.onGroupStarted = onGroupStarted;
    }

    public void whenGroupUpdated(OnGroupUpdatedListener onGroupUpdated) {
        this.onGroupUpdated = onGroupUpdated;
    }

    public void whenGroupStopped(Runnable onGroupStopped) {
        this.onGroupStopped = onGroupStopped;
    }

    public boolean start(String startDirectory) {
        scanner.whenReached(path -> !path.endsWith("/"));
        scanner.whenScanStarted(() -> mainThread.execute(() -> {
            if (onGroupStarted != null) {
                onGroupStarted.run();
            }
        }));
        scanner.whenScanUpdated((scanned, matched) -> mainThread.execute(() -> {
            totalScanned += scanned;
            for (String path : matched) {
                add(path);
            }
            reportUpdated();
        }));
        scanner.whenScanStopped(() -> mainThread.execute(() -> {
            if (cancelled || !scanner.isCompleted()) {
                reportStopped();
            } else {
                checksumTimer = new Timer();
                checksumTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mainThread.execute(() -> {
                            if (cancelled) {
                                return;
                            }
                            reportUpdated();
                            if (pendingChecksums == 0) {
                                clearChecksumTimer();
                                reportStopped();
                            }
                        });
                    }
                }, 0, updateInterval);
            }
        }));
        return scanner.start(startDirectory);
    }

    private void add(String path) {
        if (allFileProperties.containsKey(path)) {
            return;
        }

        FileProperties fileProps = new FileProperties(path);
        if (fileProps.size <= 0) {
            return;
        }
        List<FileProperties> bySize = bySizeFileProperties.computeIfAbsent(fileProps.size, k -> new LinkedList<>());
        bySize.add(fileProps);
        allFileProperties.put(path, fileProps);

        if (bySize.size() == 1) {
            return; // checksum deferred until a sibling arrives
        }

        FileProperties first = bySize.get(0);
        if (!first.checksumRequested) {
            first.checksumRequested = true;
            requestChecksum(first);
        }
        fileProps.checksumRequested = true;
        requestChecksum(fileProps);
    }

    private void requestChecksum(FileProperties fileProps) {
        pendingChecksums++;
        try {
            checksumThread.execute(() -> {
                String md5sum = checksum(fileProps.path);
                mainThread.execute(() -> {
                    if (cancelled) {
                        return;
                    }
                    fileProps.md5sum = md5sum;
                    pendingChecksums--;
                    if (md5sum != null && allFileProperties.get(fileProps.path) == fileProps) {
                        addToByChecksumFileProperties(fileProps);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            pendingChecksums--;
        }
    }

    private static String checksum(String path) {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            return DigestCodec.md5().checksum(inputStream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void addToByChecksumFileProperties(FileProperties fileProps) {
        List<FileProperties> group = byChecksumFileProperties.computeIfAbsent(
                fileProps.md5sum + "_" + fileProps.size, k -> new LinkedList<>());
        group.add(fileProps);
        if (group.size() == 2) {
            totalGroups++;
            totalGroupItems += 2;
        } else if (group.size() > 2) {
            totalGroupItems++;
        }
    }

    private void removeFromByChecksumFileProperties(FileProperties fileProps) {
        List<FileProperties> group = Objects.requireNonNull(
                byChecksumFileProperties.get(fileProps.md5sum + "_" + fileProps.size));
        group.remove(fileProps);
        if (group.size() == 1) {
            totalGroups--;
            totalGroupItems -= 2;
        } else if (group.size() >= 2) {
            totalGroupItems--;
        }
    }

    private void reportUpdated() {
        if (onGroupUpdated != null) {
            onGroupUpdated.accept(totalScanned, totalGroups, totalGroupItems);
        }
    }

    private void reportStopped() {
        if (onGroupStoppedCalled) {
            return;
        }
        onGroupStoppedCalled = true;
        if (onGroupStopped != null) {
            onGroupStopped.run();
        }
    }

    private void clearChecksumTimer() {
        if (checksumTimer != null) {
            checksumTimer.cancel();
            checksumTimer.purge();
            checksumTimer = null;
        }
    }

    /**
     * main thread only
     */
    public void remove(Iterable<String> paths) {
        for (String path : paths) {
            FileProperties fileProps = allFileProperties.remove(path);
            if (fileProps == null) {
                continue;
            }
            if (fileProps.md5sum != null) {
                removeFromByChecksumFileProperties(fileProps);
            }
            List<FileProperties> bySize = Objects.requireNonNull(bySizeFileProperties.get(fileProps.size));
            bySize.remove(fileProps);
            if (bySize.isEmpty()) {
                bySizeFileProperties.remove(fileProps.size);
            }
        }
        reportUpdated();
    }

    /**
     * main thread only
     */
    public List<List<FileProperties>> getDupGroups() {
        return byChecksumFileProperties.values().stream().filter(g -> g.size() > 1)
                .collect(Collectors.toList());
    }

    public boolean isRunning() {
        return !cancelled && (scanner.isRunning() || checksumTimer != null);
    }

    public boolean isCompleted() {
        return scanner.isCompleted() && onGroupStoppedCalled;
    }

    /**
     * main thread only
     */
    public void cancel() {
        cancelled = true;
        clearChecksumTimer();
        checksumThread.shutdownNow();
        scanner.cancel();
        reportStopped();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public static class FileProperties {
        String path;
        long size;
        String md5sum;
        private boolean checksumRequested;

        FileProperties(String path) {
            this.path = path;
            try {
                this.size = Files.size(Paths.get(path));
            } catch (IOException e) {
                this.size = -1;
            }
        }
    }

    public interface OnGroupUpdatedListener {

        void accept(int totalScanned, int totalGroups, int totalGroupItems);
    }
}
