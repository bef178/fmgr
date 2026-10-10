package pd.droidapp.fmgr.repository;

import java.util.function.Consumer;

import pd.droidapp.fmgr.repository.ProcessingWorker.StopReason;
import pd.util.FileOps;

public class RenameWorker {

    private Runnable onStarted;
    private Consumer<StopReason> onStopped;

    public void whenStarted(Runnable onStarted) {
        this.onStarted = onStarted;
    }

    public void whenStopped(Consumer<StopReason> onStopped) {
        this.onStopped = onStopped;
    }

    public void start(String src, String dst) {
        WorkerPool.post(() -> {
            ReporterPool.post(() -> {
                if (onStarted != null) {
                    onStarted.run();
                }
            });
            boolean succeeded = FileOps.singleton.move(src, dst, null);
            ReporterPool.post(() -> {
                if (onStopped != null) {
                    onStopped.accept(succeeded ? StopReason.COMPLETED : StopReason.FAILED);
                }
            });
        });
    }
}
