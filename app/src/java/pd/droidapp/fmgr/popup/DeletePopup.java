package pd.droidapp.fmgr.popup;

import android.view.View;
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

import static pd.droidapp.fmgr.popup.PopupFileItemBar.BadgeState;

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

    public DeletePopup(View containerView, String startDirectory, Collection<FileProperties> srcItems, boolean prune) {
        super(containerView, R.layout.delete_popup);
        this.srcItems = new LinkedList<>(srcItems);
        this.prune = prune;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, null);

        titleBar.setTitle(R.string.delete);

        initStatusBar();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.start, () -> worker == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> abort());
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void initStatusBar() {
        statusBar.markReady(R.drawable.outline_delete_24);
        statusBar.setText(context.getString(R.string.x_selected, srcItems.size()));
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
        RecyclerView.ItemAnimator itemAnimator = itemsView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
    }

    @Override
    protected boolean isProcessing() {
        return worker != null && worker.isWorking();
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

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        itemsAdapter.append(this.srcItems);
    }

    private void start() {
        worker = new DeleteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.delete_progress_summary,
                    Math.min(totalProgressed + 1, srcItems.size()),
                    srcItems.size(),
                    totalRemoved,
                    totalFailed));
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
                            }
                        }
                    }
                }
            }
            statusBar.setText(context.getString(R.string.delete_progress_summary,
                    Math.min(totalProgressed + 1, srcItems.size()),
                    srcItems.size(),
                    totalRemoved,
                    totalFailed));
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            if (reason == StopReason.COMPLETED) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
            updateButtons();
        }));
        worker.start(srcItems, prune);

        updateButtons();
    }

    private void abort() {
        if (worker != null) {
            worker.cancel();
        }
        updateButtons();
    }
}
