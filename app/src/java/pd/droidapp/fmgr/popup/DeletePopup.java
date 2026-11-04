package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.repository.DeleteWorker;
import pd.droidapp.fmgr.repository.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemAdapter;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.State;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.FileIconUtil.getDrawableId;
import static pd.droidapp.fmgr.util.Util.scrollToIndex;
import static pd.droidapp.fmgr.view.FileItemBar.ProcessingStatus;

public class DeletePopup extends ProcessingPopup {

    private final String startDirectory;
    private final List<FileProperties> srcItems;
    private final Map<String, ProcessingStatus> badgeStates = new HashMap<>();

    // views
    private final StatusBar statusBar;
    private final RecyclerView itemsView;
    private final FileItemAdapter itemAdapter;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private DeleteWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();
    private int totalRemoved;
    private int totalFailed;
    private int totalProgressed;
    private boolean followProgress = true;
    private boolean touching;

    public DeletePopup(View containerView, String startDirectory, Collection<FileProperties> srcItems) {
        super(containerView);
        this.startDirectory = startDirectory;
        this.srcItems = new LinkedList<>(srcItems);

        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ia_delete_24);
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemAdapter = new FileItemAdapter();

        bottomBar.whenButtonClicked(id -> {
            if (id == R.id.action_delete) {
                start(false);
            } else if (id == R.id.action_delete_up) {
                start(true);
            } else if (id == R.id.action_abort) {
                if (worker != null) {
                    worker.cancel();
                }
                renderBottomBar();
            } else if (id == R.id.action_close) {
                selfDialog.dismiss();
            }
        });

        initItemsView();
    }

    @Override
    protected void inflateContentView() {
        super.inflateContentView();
        LayoutInflater.from(context).inflate(R.layout.delete_popup_content, contentView, true);
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemAdapter);
        RecyclerView.ItemAnimator itemAnimator = itemsView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
        itemsView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        touching = true;
                        followProgress = false;
                        itemsView.stopScroll();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        touching = false;
                        break;
                    default:
                        break;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        if (worker != null) {
            worker.cancel();
        }
        if (worker == null || !worker.whenStopped(reason -> continueDismiss.run())) {
            continueDismiss.run();
        }
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), netRemoved);
        }
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.delete)));
        renderStatusBar(StatusBar.IconStatus.IDLE);
        renderBottomBar();

        renderItems();
    }

    private void renderItems() {
        List<FileItemBar.State> states = new ArrayList<>(srcItems.size());
        for (int i = 0; i < srcItems.size(); i++) {
            states.add(buildState(srcItems.get(i), i + 1));
        }
        itemAdapter.render(states);
    }

    private FileItemBar.State buildState(FileProperties item, int index) {
        return new FileItemBar.State(
                item.path,
                PathOps.singleton.relativize(startDirectory, item.path),
                null,
                String.valueOf(index),
                getDrawableId(context, item),
                true,
                badgeStates.getOrDefault(item.path, ProcessingStatus.NONE));
    }

    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        if (iconStatus == StatusBar.IconStatus.IDLE) {
            statusBar.render(new State(iconStatus, context.getString(R.string.x_selected, srcItems.size())));
            return;
        }
        statusBar.render(new State(iconStatus, context.getString(R.string.delete_progress_summary,
                Math.min(totalProgressed + 1, srcItems.size()),
                srcItems.size(),
                totalRemoved,
                totalFailed)));
    }

    private void renderBottomBar() {
        boolean isProcessing = worker != null && worker.isWorking();
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.id.action_delete_up, R.drawable.ia_delete_up_24, worker == null, true),
                new ButtonState(R.id.action_delete, R.drawable.ia_delete_24, worker == null, true),
                new ButtonState(R.id.action_abort, R.drawable.ic_blockmark_24, isProcessing, isProcessing && !worker.isCancelled()),
                new ButtonState(R.id.action_close, R.drawable.ic_checkmark_24, worker != null && !worker.isWorking(), true)));
    }

    private void start(boolean prune) {
        worker = new DeleteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconStatus.RUNNING);
            badgeStates.put(srcItems.get(0).path, ProcessingStatus.RUNNING);
            itemAdapter.update(0, buildState(srcItems.get(0), 1));
        }));
        worker.whenUpdated((removed, failed, progressed) -> containerView.post(() -> {
            netRemoved.addAll(removed);
            totalRemoved += removed.size();
            totalFailed += failed;

            if (!progressed.isEmpty()) {
                Map<String, Integer> itemPathToIndex = new HashMap<>();
                for (int i = 0; i < srcItems.size(); i++) {
                    itemPathToIndex.put(srcItems.get(i).path, i);
                }
                int currentProgress = -1;
                for (Map.Entry<String, Boolean> entry : progressed) {
                    String path = entry.getKey();
                    Boolean succeeded = entry.getValue();
                    Integer itemIndex = itemPathToIndex.get(path);
                    if (itemIndex != null) {
                        ProcessingStatus badgeState;
                        if (succeeded == null) {
                            badgeState = ProcessingStatus.ABORTED;
                        } else if (succeeded) {
                            badgeState = ProcessingStatus.COMPLETED;
                        } else {
                            badgeState = ProcessingStatus.FAILED;
                        }
                        badgeStates.put(path, badgeState);
                        itemAdapter.update(itemIndex, buildState(srcItems.get(itemIndex), itemIndex + 1));
                        if (succeeded != null) {
                            totalProgressed++;
                            if (itemIndex + 1 < srcItems.size()) {
                                badgeStates.put(srcItems.get(itemIndex + 1).path, ProcessingStatus.RUNNING);
                                itemAdapter.update(itemIndex + 1, buildState(srcItems.get(itemIndex + 1), itemIndex + 2));
                                currentProgress = itemIndex + 1;
                            }
                        }
                    }
                }
                scrollToCurrentIfFollowing(currentProgress);
            }
            renderStatusBar(StatusBar.IconStatus.RUNNING);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            renderStatusBar(reason == StopReason.COMPLETED ? StatusBar.IconStatus.COMPLETED : StatusBar.IconStatus.STOPPED);
            renderBottomBar();
        }));
        worker.start(srcItems, prune);

        renderBottomBar();
    }

    private void scrollToCurrentIfFollowing(int current) {
        if (current < 0) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int theLastEntireVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        if (theLastEntireVisible == RecyclerView.NO_POSITION) {
            return;
        }
        if (!touching
                && itemsView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE
                && current >= theLastEntireVisible
                && current <= layoutManager.findLastVisibleItemPosition()) {
            followProgress = true;
        }
        if (followProgress) {
            scrollToIndex(itemsView, current);
        }
    }
}
