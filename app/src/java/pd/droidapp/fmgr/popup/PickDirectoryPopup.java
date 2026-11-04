package pd.droidapp.fmgr.popup;

import android.view.LayoutInflater;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.repository.ListDirectoryWorker;
import pd.droidapp.fmgr.util.DirectoryScrollState;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemAdapter;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.State;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.PathNavigator.CAPPING_DIRECTORY;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class PickDirectoryPopup extends ProcessingPopup {

    private String targetDirectory;
    private ListDirectoryWorker worker;
    private final Map<String, DirectoryScrollState> scrollStates = new HashMap<>();
    private final String firstSource;

    // views
    private final StatusBar statusBar;
    private final RecyclerView itemsView;
    private final FileItemAdapter itemAdapter;

    // callbacks
    private Consumer<String> onDirectorySelected;

    public PickDirectoryPopup(View containerView, String startDirectory, String firstSource) {
        super(containerView);
        targetDirectory = startDirectory;
        this.firstSource = firstSource;

        // find views
        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ic_directory_24);
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemAdapter = new FileItemAdapter();

        // init views
        bottomBar.whenButtonClicked(id -> {
            if (id == R.id.action_pick_directory) {
                if (onDirectorySelected != null) {
                    onDirectorySelected.accept(targetDirectory);
                }
                selfDialog.dismiss();
            }
        });
        initStatusBar();
        initItemsView();
    }

    @Override
    protected void inflateContentView() {
        super.inflateContentView();
        LayoutInflater.from(context).inflate(R.layout.pick_directory_popup_content, contentView, true);
    }

    private void initStatusBar() {
        statusBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_arrow_up_24) {
                String child = targetDirectory;
                changeDirectory(PathOps.singleton.dirname(child),
                        () -> itemAdapter.render(new FileItemAdapter.ScrollToAndHighlightEvent(
                                child, context.getColor(R.color.purple_50))));
            }
        });
    }

    private void initItemsView() {
        itemAdapter.whenItemClicked(directory -> changeDirectory(directory, null));
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemAdapter);
    }

    private void changeDirectory(String directory, Runnable onCompleted) {
        saveScrollState(targetDirectory);
        targetDirectory = directory;
        renderStatusBar(StatusBar.IconStatus.RUNNING);

        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        itemAdapter.clear();

        List<FileProperties> listed = new LinkedList<>();
        ListDirectoryWorker current = new ListDirectoryWorker(); // the guard
        current.whenUpdated(items -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            for (FileProperties item : items) {
                if (item.isDirectory) {
                    listed.add(item);
                }
            }
        }));
        current.whenStopped(reason -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            renderStatusBar(StatusBar.IconStatus.IDLE);
            itemAdapter.render(buildStates(listed), true);
            restoreScrollState(directory);
            if (onCompleted != null) {
                onCompleted.run();
            }
        }));
        worker = current;
        current.start(directory);
    }

    private List<FileItemBar.State> buildStates(List<FileProperties> items) {
        List<FileItemBar.State> states = new LinkedList<>();
        for (FileProperties item : items) {
            states.add(new FileItemBar.State(item.path, PathOps.singleton.basename(item.path), null, null,
                    R.drawable.ic_directory_24, false));
        }
        return states;
    }

    private void saveScrollState(String directory) {
        if (directory == null || itemAdapter.getItemCount() == 0) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        View child = layoutManager == null ? null : layoutManager.getChildAt(0);
        if (child == null) {
            return;
        }
        DirectoryScrollState scrollState = scrollStates.computeIfAbsent(directory, DirectoryScrollState::new);
        scrollState.scrollPosition = layoutManager.getPosition(child);
        scrollState.scrollOffset = layoutManager.getDecoratedTop(child) - layoutManager.getPaddingTop();
    }

    private void restoreScrollState(String directory) {
        DirectoryScrollState scrollState = scrollStates.get(directory);
        if (scrollState == null || scrollState.scrollPosition < 0 || scrollState.scrollPosition >= itemAdapter.getItemCount()) {
            return;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(scrollState.scrollPosition, scrollState.scrollOffset);
        }
    }

    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        statusBar.render(new State(iconStatus,
                getDisplayPath(targetDirectory),
                new ButtonState(R.drawable.ic_arrow_up_24, true, !CAPPING_DIRECTORY.equals(targetDirectory)
                        && !PathOps.singleton.dirname(targetDirectory).equals(targetDirectory))));
    }

    public void whenDirectorySelected(Consumer<String> onDirectorySelected) {
        this.onDirectorySelected = onDirectorySelected;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        if (worker != null) {
            worker.cancel();
            worker = null; // late callbacks are dropped by the guards
        }
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.pick_directory)));
        bottomBar.render(new PopupBottomBar.State(new ButtonState(R.id.action_pick_directory, R.drawable.ia_pick_directory_24, true, true)));

        changeDirectory(targetDirectory, () -> itemAdapter.render(new FileItemAdapter.ScrollToAndHighlightEvent(
                firstSource, context.getColor(R.color.purple_50))));
    }
}
