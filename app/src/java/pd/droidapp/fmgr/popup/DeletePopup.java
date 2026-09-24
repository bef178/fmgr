package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.State;

import static pd.droidapp.fmgr.popup.PopupFileItemBar.BadgeState;
import static pd.droidapp.fmgr.util.Util.scrollToIndex;

public class DeletePopup extends ProcessingPopup {

    private final List<FileProperties> srcItems;
    private final boolean prune;

    // views
    private final StatusBar statusBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private DeleteWorker worker;
    private final Collection<FileProperties> netRemoved = new LinkedList<>();
    private int totalRemoved;
    private int totalFailed;
    private int totalProgressed;
    private boolean followProgress = true;
    private boolean touching;

    public DeletePopup(View containerView, String startDirectory, Collection<FileProperties> srcItems, boolean prune) {
        super(containerView);
        this.srcItems = new LinkedList<>(srcItems);
        this.prune = prune;

        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ic_delete_24);
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, false);

        bottomBar.whenButtonClicked(id -> {
            if (id == R.string.delete) {
                start();
            } else if (id == R.string.abort) {
                if (worker != null) {
                    worker.cancel();
                }
                renderBottomBar();
            } else if (id == R.string.close) {
                selfWindow.dismiss();
            }
        });

        initItemsView();
    }

    @Override
    protected void inflateContent() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) areaView.getLayoutParams();
        params.height = context.getResources().getDimensionPixelSize(R.dimen.delete_popup_height);
        areaView.setLayoutParams(params);

        LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        contentParams.height = 0;
        contentParams.weight = 1;
        contentView.setLayoutParams(contentParams);
        LayoutInflater.from(context).inflate(R.layout.delete_popup_content, contentView, true);
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
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
        renderStatusBar(StatusBar.IconState.IDLE);
        renderBottomBar();

        itemsAdapter.append(this.srcItems);
        for (int i = 0; i < srcItems.size(); i++) {
            itemsAdapter.setItemBadge(i, BadgeState.SELECTED);
        }
    }

    private void renderStatusBar(StatusBar.IconState iconState) {
        if (iconState == StatusBar.IconState.IDLE) {
            statusBar.render(new State(iconState, context.getString(R.string.x_selected, srcItems.size())));
            return;
        }
        statusBar.render(new State(iconState, context.getString(R.string.delete_progress_summary,
                Math.min(totalProgressed + 1, srcItems.size()),
                srcItems.size(),
                totalRemoved,
                totalFailed)));
    }

    private void renderBottomBar() {
        boolean isProcessing = worker != null && worker.isWorking();
        bottomBar.render(new PopupBottomBar.State(
                ButtonState.ofText(R.string.delete, context.getString(R.string.delete), worker == null),
                ButtonState.ofText(R.string.abort, context.getString(R.string.abort),
                        isProcessing, isProcessing && !worker.isCancelled()),
                ButtonState.ofText(R.string.close, context.getString(R.string.close),
                        worker != null && !worker.isWorking())));
    }

    private void start() {
        worker = new DeleteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconState.RUNNING);
            itemsAdapter.setItemBadge(0, BadgeState.RUNNING);
        }));
        worker.whenUpdated((removed, failed, progressed) -> containerView.post(() -> {
            netRemoved.addAll(removed);
            totalRemoved += removed.size();
            totalFailed += failed;

            if (!progressed.isEmpty()) {
                Map<String, Integer> itemPathToIndex = new HashMap<>();
                for (int i = 0; i < itemsAdapter.getItemCount(); i++) {
                    itemPathToIndex.put(itemsAdapter.getItems().get(i).path, i);
                }
                int currentProgress = -1;
                for (Map.Entry<String, Boolean> entry : progressed) {
                    String path = entry.getKey();
                    Boolean succeeded = entry.getValue();
                    Integer itemIndex = itemPathToIndex.get(path);
                    if (itemIndex != null) {
                        BadgeState badgeState;
                        if (succeeded == null) {
                            badgeState = BadgeState.STOPPED;
                        } else if (succeeded) {
                            badgeState = BadgeState.DONE;
                        } else {
                            badgeState = BadgeState.FAILED;
                        }
                        itemsAdapter.setItemBadge(itemIndex, badgeState);
                        if (succeeded != null) {
                            totalProgressed++;
                            if (itemIndex + 1 < itemsAdapter.getItemCount()) {
                                itemsAdapter.setItemBadge(itemIndex + 1, BadgeState.RUNNING);
                                currentProgress = itemIndex + 1;
                            }
                        }
                    }
                }
                scrollToCurrentIfFollowing(currentProgress);
            }
            renderStatusBar(StatusBar.IconState.RUNNING);
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            renderStatusBar(reason == StopReason.COMPLETED ? StatusBar.IconState.COMPLETED : StatusBar.IconState.STOPPED);
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
