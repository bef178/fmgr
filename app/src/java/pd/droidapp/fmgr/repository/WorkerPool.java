package pd.droidapp.fmgr.repository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkerPool {

    private static final ExecutorService executor = createExecutor();

    private static ExecutorService createExecutor() {
        // an unbounded queue obsoletes `maxPoolSize`
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                6,
                6,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public static void post(Runnable runnable) {
        executor.execute(runnable);
    }
}
