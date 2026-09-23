package pd.droidapp.fmgr.popup;

import android.animation.LayoutTransition;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.PasteWorker.ConflictResolution;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.State;
import pd.util.PathOps;

import static pd.droidapp.fmgr.popup.PopupFileItemBar.BadgeState;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.scrollToIndex;

public class PastePopup extends ProcessingPopup {

    private final boolean isCopy;
    private final List<FileProperties> srcItems;
    private String dstDirectory;

    // views
    private final StatusBar statusBar;
    private final TextView targetDirectoryTextView;
    private final HorizontalScrollView targetDirectoryScrollView;
    private final ImageButton changeDirectoryButton;
    private final TextView resolutionSummaryTextView;
    private final RadioGroup resolutionOptionsGroup;
    private final CheckBox mergeDirectoriesCheckBox;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private PasteWorker worker;
    private final Map<String, FileProperties> netAdded = new LinkedHashMap<>();
    private final Map<String, FileProperties> netRemoved = new LinkedHashMap<>();
    private int totalAdded;
    private int totalRemoved;
    private int totalMoved;
    private int totalFailed;
    private int totalProcessed;
    private boolean followProgress = true;
    private boolean touching;

    public PastePopup(View containerView, boolean isCopy, Collection<FileProperties> srcItems, String dstDirectory) {
        super(containerView, R.layout.paste_popup);
        this.isCopy = isCopy;
        this.dstDirectory = dstDirectory;
        this.srcItems = new LinkedList<>(srcItems);

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar), isCopy
                ? R.drawable.ic_copy_24
                : R.drawable.ic_cut_24);
        targetDirectoryTextView = mainAreaView.findViewById(R.id.target_directory);
        targetDirectoryScrollView = mainAreaView.findViewById(R.id.target_directory_scroll);
        changeDirectoryButton = mainAreaView.findViewById(R.id.change_directory);
        resolutionSummaryTextView = mainAreaView.findViewById(R.id.resolution_summary);
        resolutionOptionsGroup = mainAreaView.findViewById(R.id.resolution_options);
        mergeDirectoriesCheckBox = mainAreaView.findViewById(R.id.merge_directories_checkbox);
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(PathOps.singleton.dirname(this.srcItems.get(0).path), false);

        titleBar.setTitle(isCopy ? R.string.copy : R.string.cut);

        renderStatusBar(StatusBar.IconState.IDLE);
        initPasteOptions();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.paste, () -> worker == null, () -> true, v -> start());
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> abort());
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void renderStatusBar(StatusBar.IconState iconState) {
        if (iconState == StatusBar.IconState.IDLE) {
            statusBar.render(new State(iconState, context.getString(R.string.x_selected, srcItems.size())));
            return;
        }
        statusBar.render(new State(iconState, context.getString(R.string.paste_progress_summary,
                Math.min(totalProcessed + 1, srcItems.size()),
                srcItems.size(),
                totalAdded,
                totalRemoved,
                totalMoved,
                totalFailed)));
    }

    private void initPasteOptions() {
        changeDirectoryButton.setOnClickListener(v -> showDirectoryPicker());
        invalidatePasteOptions();
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

    private void showDirectoryPicker() {
        DirectoryPickerPopup popup = new DirectoryPickerPopup(containerView, dstDirectory);
        popup.whenDirectorySelected(d -> {
            dstDirectory = d;
            invalidatePasteOptions();
            targetDirectoryScrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            targetDirectoryScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            // the furthest HorizontalScrollView will scroll to, same figure it clamps by
                            int end = targetDirectoryScrollView.getChildAt(0).getWidth()
                                    - targetDirectoryScrollView.getWidth()
                                    + targetDirectoryScrollView.getPaddingLeft()
                                    + targetDirectoryScrollView.getPaddingRight();
                            if (end <= 0) {
                                return;
                            }
                            targetDirectoryScrollView.smoothScrollTo(end, 0);
                            targetDirectoryScrollView.postDelayed(
                                    () -> targetDirectoryScrollView.smoothScrollTo(0, 0),
                                    600);
                        }
                    });
        });
        popup.show();
    }

    private void invalidatePasteOptions() {
        targetDirectoryTextView.setText(getDisplayPath(dstDirectory));
        boolean inPlace = srcItems.stream()
                .allMatch(item -> dstDirectory.equals(PathOps.singleton.dirname(item.path)));
        mergeDirectoriesCheckBox.setChecked(!inPlace);
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
            onPopupDismissed.accept(netAdded.values(), netRemoved.values());
        }
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        itemsAdapter.append(this.srcItems);
        for (int i = 0; i < srcItems.size(); i++) {
            itemsAdapter.setItemBadge(i, BadgeState.SELECTED);
        }
    }

    private void start() {
        changeDirectoryButton.setVisibility(View.GONE);

        int shortId;
        final ConflictResolution resolution = getSelectedResolution();
        if (resolution == ConflictResolution.OVERWRITE) {
            shortId = R.string.resolution_short_overwrite;
        } else if (resolution == ConflictResolution.RENAME_INCOMING) {
            shortId = R.string.resolution_short_rename;
        } else {
            shortId = R.string.resolution_short_skip;
        }
        String resolutionText = context.getString(shortId);
        CharSequence summary = mergeDirectoriesCheckBox.isChecked()
                ? context.getString(R.string.x_merge_directories, resolutionText)
                : resolutionText;
        LayoutTransition collapseTransition = createResolutionCollapseTransition(summary);
        ((ViewGroup) resolutionOptionsGroup.getParent()).setLayoutTransition(collapseTransition);
        resolutionOptionsGroup.setVisibility(View.GONE);
        mergeDirectoriesCheckBox.setVisibility(View.GONE);

        worker = new PasteWorker();
        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconState.RUNNING);
            itemsAdapter.setItemBadge(0, BadgeState.RUNNING);
        }));
        worker.whenUpdated((added, removed, moved, failed, progressed) -> containerView.post(() -> {
            for (FileProperties item : added) {
                netAdded.put(item.path, item);
                netRemoved.remove(item.path);
            }
            for (Map.Entry<FileProperties, FileProperties> pair : moved) {
                FileProperties src = pair.getKey();
                FileProperties dst = pair.getValue();
                netAdded.put(dst.path, dst);
                netAdded.keySet().removeIf(path -> path.startsWith(src.path + "/"));
                netAdded.remove(src.path);
                netRemoved.put(src.path, src);
                netRemoved.remove(dst.path);
            }
            for (FileProperties item : removed) {
                netAdded.remove(item.path);
                netRemoved.put(item.path, item);
            }
            totalAdded += added.size();
            totalRemoved += removed.size();
            totalMoved += moved.size();
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
                            totalProcessed++;
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
            updateButtons();
        }));
        if (isCopy) {
            worker.startCopy(srcItems, dstDirectory, resolution, mergeDirectoriesCheckBox.isChecked());
        } else {
            worker.startCut(srcItems, dstDirectory, resolution, mergeDirectoriesCheckBox.isChecked());
        }

        updateButtons();
    }

    private LayoutTransition createResolutionCollapseTransition(CharSequence summary) {
        LayoutTransition collapseTransition = new LayoutTransition();
        collapseTransition.setDuration(150);
        // there's 300ms start delay for APPEARING by default
        collapseTransition.disableTransitionType(LayoutTransition.APPEARING);
        collapseTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                if (transitionType == LayoutTransition.DISAPPEARING) {
                    resolutionSummaryTextView.setText(summary);
                    resolutionSummaryTextView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
            }
        });
        return collapseTransition;
    }

    private ConflictResolution getSelectedResolution() {
        int selectedId = resolutionOptionsGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.resolution_option_overwrite) {
            return ConflictResolution.OVERWRITE;
        } else if (selectedId == R.id.resolution_option_rename_incoming) {
            return ConflictResolution.RENAME_INCOMING;
        } else {
            return ConflictResolution.SKIP_INCOMING;
        }
    }

    private void abort() {
        if (worker != null) {
            worker.cancel();
        }
        updateButtons();
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
