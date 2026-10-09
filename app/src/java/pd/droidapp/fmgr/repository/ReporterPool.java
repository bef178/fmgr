package pd.droidapp.fmgr.repository;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class ReporterPool {

    private static final ScheduledExecutorService scheduler = createScheduler();

    private static ScheduledExecutorService createScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.setKeepAliveTime(60, TimeUnit.SECONDS);
        scheduler.allowCoreThreadTimeOut(true);
        return scheduler;
    }

    static ScheduledFuture<?> schedule(Runnable tick, long intervalMillis) {
        return scheduler.scheduleWithFixedDelay(tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    static void post(Runnable runnable) {
        scheduler.execute(runnable);
    }
}
