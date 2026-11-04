package pd.droidapp.fmgr.fragment;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.droidapp.fmgr.popup.CreatePopup;
import pd.droidapp.fmgr.popup.DeletePopup;
import pd.droidapp.fmgr.popup.FindDupPopup;
import pd.droidapp.fmgr.popup.FindEmptyPopup;
import pd.droidapp.fmgr.popup.PastePopup;
import pd.droidapp.fmgr.popup.RenamePopup;
import pd.droidapp.fmgr.popup.SearchPopup;
import pd.droidapp.fmgr.repository.ListDirectoryWorker;
import pd.droidapp.fmgr.repository.PropertiesLoader;
import pd.droidapp.fmgr.util.DirectoryScrollState;
import pd.droidapp.fmgr.util.FavoritesStore;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.PathNavigator;
import pd.droidapp.fmgr.view.ActionBar;
import pd.droidapp.fmgr.view.BreadcrumbBar;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemAdapter;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.SelectionBar;
import pd.droidapp.glry.GlryActivity;
import pd.util.FileOps;
import pd.util.FileStat;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.FileIconUtil.getDrawableId;
import static pd.droidapp.fmgr.util.Util.getSizeString;
import static pd.droidapp.fmgr.util.Util.themeColor;

public class BrowseFragment extends Fragment {

    private FavoritesStore favoritesStore;
    private BreadcrumbBar breadcrumbBar;
    private ActionBar actionBar;
    private SelectionBar selectionBar;
    private SwipeRefreshLayout swipeRefreshView;
    private RecyclerView itemsView;
    private FileItemAdapter itemAdapter;
    private ListDirectoryWorker worker;

    private final List<FileProperties> items = new ArrayList<>();
    private final Map<String, Integer> itemPositions = new HashMap<>();
    private final Set<String> selectedPaths = new LinkedHashSet<>();
    private PropertiesLoader propertiesLoader;

    private PathNavigator navigator = new PathNavigator();
    private DirectoryScrollState currentScrollState;
    private String outingPath;

    private boolean askedAllFilesAccess;
    private boolean mightGrantedAllFilesAccess;

    private static final String STATE_NAVIGATOR = "navigator";
    private static final String STATE_SELECTED_ITEMS = "selected_items";
    private static final String DEFAULT_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();

    private static final Comparator<FileProperties> ITEM_COMPARATOR = (p1, p2) -> {
        if (p1.isDirectory != p2.isDirectory) {
            return p1.isDirectory ? -1 : 1;
        }
        return PathOps.singleton.compare(p1.path, p2.path);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        favoritesStore = new FavoritesStore(requireContext());
        breadcrumbBar = new BreadcrumbBar(view.findViewById(R.id.breadcrumbs_bar));
        breadcrumbBar.whenBreadcrumbItemClicked(this::navigateToDirectory);
        breadcrumbBar.whenStarIconClicked(this::toggleFavorite);

        ImageButton homeButton = view.findViewById(R.id.action_home);
        homeButton.setOnClickListener(v -> ((FirstFragment) requireParentFragment()).showHomeFragment());

        actionBar = new ActionBar(view.findViewById(R.id.action_bar));
        actionBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_arrow_left_24) {
                navigateBack();
            } else if (id == R.drawable.ic_arrow_right_24) {
                navigateForward();
            } else if (id == R.drawable.ic_arrow_up_24) {
                navigateUp();
            } else if (id == R.drawable.ic_arrow_clockwise_24) {
                refresh();
            } else if (id == R.drawable.ia_new_directory_24) {
                showCreatePopup(true);
            } else if (id == R.drawable.ia_new_file_24) {
                showCreatePopup(false);
            } else if (id == R.drawable.ia_find_24) {
                showSearchPopup();
            } else if (id == R.string.find_empty) {
                showFindEmptyPopup();
            } else if (id == R.string.find_duplicate) {
                showFindDupPopup();
            }
        });

        selectionBar = new SelectionBar(view.findViewById(R.id.selection_bar));

        swipeRefreshView = view.findViewById(R.id.swipe_refresh);
        swipeRefreshView.setOnRefreshListener(this::refresh);
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        swipeRefreshView.setColorSchemeColors(typedValue.data);

        itemAdapter = new FileItemAdapter();
        itemAdapter.whenItemClicked(this::onItemClicked);
        itemAdapter.whenItemLongClicked(this::toggleSelection);

        itemsView = view.findViewById(R.id.items_list);
        itemsView.setLayoutManager(new LinearLayoutManager(requireContext()));
        itemsView.setAdapter(itemAdapter);
        RecyclerView.ItemAnimator itemAnimator = itemsView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }

        propertiesLoader = new PropertiesLoader();
        propertiesLoader.whenUpdated(updated -> itemsView.post(() -> {
            for (FileProperties item : updated) {
                Integer position = itemPositions.get(item.path);
                if (position == null || items.get(position) != item) {
                    continue;
                }
                itemAdapter.update(position, buildState(item));
            }
        }));

        initSelectionBar();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        renderItems();
        renderActionBar();

        return view;
    }

    private void refresh() {
        refresh(null);
    }

    private void refresh(Runnable onComplete) {
        renderBreadcrumbsBar();
        String directory = navigator.getCurrentDirectory();
        if (currentScrollState != null && !items.isEmpty()) {
            saveScrollState(currentScrollState);
        }
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        items.clear();
        itemPositions.clear();
        propertiesLoader.clear();
        itemAdapter.clear();
        deselectAll();
        if (directory == null) {
            swipeRefreshView.setRefreshing(false);
            renderActionBar();
            return;
        }
        swipeRefreshView.setRefreshing(true);
        renderActionBar();

        ListDirectoryWorker current = new ListDirectoryWorker(); // the guard
        current.whenUpdated(listed -> itemsView.post(() -> {
            if (worker != current) {
                return;
            }
            items.addAll(listed);
        }));
        current.whenStopped(reason -> itemsView.post(() -> {
            if (worker != current) {
                return;
            }
            itemAdapter.render(buildItemStates(), true);
            swipeRefreshView.setRefreshing(false);
            renderActionBar();
            propertiesLoader.clear();
            propertiesLoader.add(new LinkedList<>(items));
            currentScrollState = navigator.getCurrentScrollState();
            if (currentScrollState.scrollPosition >= 0 && currentScrollState.scrollPosition < items.size()) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(currentScrollState.scrollPosition, currentScrollState.scrollOffset);
                }
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }));
        worker = current;
        current.start(directory);
    }

    private void saveScrollState(DirectoryScrollState directoryScrollState) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        View child = layoutManager.getChildAt(0);
        if (child == null) {
            return;
        }
        directoryScrollState.scrollPosition = layoutManager.getPosition(child);
        directoryScrollState.scrollOffset = layoutManager.getDecoratedTop(child) - layoutManager.getPaddingTop();
    }

    private void addItems(Collection<FileProperties> toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        Map<String, FileProperties> byPath = new HashMap<>();
        for (FileProperties item : toAdd) {
            byPath.put(item.path, item);
        }
        for (int i = 0; i < items.size(); i++) {
            FileProperties replacement = byPath.remove(items.get(i).path);
            if (replacement != null) {
                items.set(i, replacement);
            }
        }
        items.addAll(byPath.values());
        items.sort(ITEM_COMPARATOR);
        propertiesLoader.add(new LinkedList<>(toAdd));
        renderItems();
    }

    private void removeItems(Collection<FileProperties> toRemove) {
        Set<String> paths = new HashSet<>();
        for (FileProperties item : toRemove) {
            paths.add(item.path);
        }
        items.removeIf(item -> paths.contains(item.path));
        renderItems();
    }

    private void loadProperties(Collection<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        Set<String> pathsSet = new HashSet<>(paths);
        List<FileProperties> toReload = new LinkedList<>();
        for (FileProperties item : items) {
            if (pathsSet.contains(item.path)) {
                item.computed = false;
                toReload.add(item);
            }
        }
        if (!toReload.isEmpty()) {
            propertiesLoader.add(toReload);
        }
    }

    private void scrollToAndHighlight(String path) {
        scrollToAndHighlight(path, themeColor(requireContext(), com.google.android.material.R.attr.colorSecondary));
    }

    private void scrollToAndHighlight(String path, @ColorInt int peakColor) {
        itemAdapter.render(new FileItemAdapter.ScrollToAndHighlightEvent(path, peakColor));
    }

    private void renderBreadcrumbsBar() {
        String currentDirectory = navigator.getCurrentDirectory();
        breadcrumbBar.render(new BreadcrumbBar.State(currentDirectory, favoritesStore.contains(currentDirectory)));
    }

    private void renderActionBar() {
        boolean loading = swipeRefreshView.isRefreshing();
        actionBar.render(new ActionBar.State(
                new ButtonState[] {
                        new ButtonState(R.drawable.ic_arrow_left_24, true, navigator.canGoBack()),
                        new ButtonState(R.drawable.ic_arrow_right_24, true, navigator.canGoForward()),
                        new ButtonState(R.drawable.ic_arrow_up_24, true, navigator.canGoUp()),
                        new ButtonState(R.drawable.ic_arrow_clockwise_24),
                },
                new ButtonState(R.drawable.ia_new_directory_24, true, !loading),
                new ButtonState(R.drawable.ia_new_file_24, true, !loading),
                new ButtonState(R.drawable.ia_find_24),
                new ButtonState(R.string.find_empty, R.drawable.ia_find_empty_24, true, true),
                new ButtonState(R.string.find_duplicate, R.drawable.ia_find_dup_24, true, true)));
    }

    private void renderItems() {
        itemAdapter.render(buildItemStates());
    }

    private List<FileItemBar.State> buildItemStates() {
        itemPositions.clear();
        List<FileItemBar.State> itemStates = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            FileProperties item = items.get(i);
            itemPositions.put(item.path, i);
            itemStates.add(buildState(item));
        }
        return itemStates;
    }

    private FileItemBar.State buildState(FileProperties item) {
        return new FileItemBar.State(
                item.path,
                PathOps.singleton.basename(item.path),
                getItemDetailsString(item),
                null,
                getDrawableId(requireContext(), item),
                selectedPaths.contains(item.path));
    }

    private String getItemDetailsString(FileProperties item) {
        if (!item.computed) {
            return "...";
        }
        if (item.isDirectory) {
            if (item.numChildren == null) {
                return getString(R.string.error_directory_not_accessible);
            }
            if (item.numChildren == 0) {
                return getString(R.string.empty);
            }
            return getString(R.string.x_items, item.numChildren);
        } else if (item.size != null) {
            return getSizeString(item.size);
        } else {
            return getString(R.string.error);
        }
    }

    private void initSelectionBar() {
        selectionBar.whenButtonClicked(id -> {
            if (id == R.drawable.ia_rename_24) {
                if (selectedPaths.size() == 1) {
                    showRenamePopup(getSelectedItems().get(0));
                }
            } else if (id == R.drawable.ia_copy_24) {
                showPastePopup(getSelectedItems(), true);
            } else if (id == R.drawable.ia_move_24) {
                showPastePopup(getSelectedItems(), false);
            } else if (id == R.drawable.ia_delete_24) {
                showDeletePopup(getSelectedItems());
            } else if (id == R.drawable.ia_select_all_24) {
                selectAll();
            } else if (id == R.drawable.ic_xmark_24) {
                deselectAll();
            }
        });
    }

    private void renderSelectionBar() {
        int numSelected = selectedPaths.size();
        selectionBar.render(new SelectionBar.State(numSelected,
                new ButtonState(R.drawable.ia_rename_24, numSelected == 1),
                new ButtonState(R.drawable.ia_copy_24, numSelected > 0),
                new ButtonState(R.drawable.ia_move_24, numSelected > 0),
                new ButtonState(R.drawable.ia_delete_24, numSelected > 0),
                new ButtonState(R.drawable.ia_select_all_24, numSelected > 0),
                new ButtonState(R.drawable.ic_xmark_24, numSelected > 0)));
    }

    private void select(Collection<String> paths) {
        selectedPaths.addAll(paths);
        for (String path : paths) {
            updateItemSelection(path);
        }
        renderSelectionBar();
    }

    private void toggleSelection(String path) {
        if (!selectedPaths.remove(path)) {
            selectedPaths.add(path);
        }
        updateItemSelection(path);
        renderSelectionBar();
    }

    private void selectAll() {
        selectedPaths.clear();
        for (FileProperties item : items) {
            selectedPaths.add(item.path);
        }
        renderItems();
        renderSelectionBar();
    }

    private void deselectAll() {
        if (selectedPaths.isEmpty()) {
            return;
        }
        selectedPaths.clear();
        renderItems();
        renderSelectionBar();
    }

    private void updateItemSelection(String path) {
        Integer position = itemPositions.get(path);
        if (position == null) {
            return;
        }
        itemAdapter.update(position, buildState(items.get(position)));
    }

    private List<FileProperties> getSelectedItems() {
        List<FileProperties> selected = new LinkedList<>();
        for (FileProperties item : items) {
            if (selectedPaths.contains(item.path)) {
                selected.add(item);
            }
        }
        return selected;
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        PathNavigator savedNavigator = (PathNavigator) savedInstanceState.getSerializable(STATE_NAVIGATOR);
        if (savedNavigator != null) {
            navigator = savedNavigator;
        }
        if (navigator.isCurrentDirectoryAccessible()) {
            refresh();
        }

        List<String> savedSelectedItems = (List<String>) savedInstanceState.getSerializable(STATE_SELECTED_ITEMS);
        if (savedSelectedItems != null) {
            select(savedSelectedItems);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_NAVIGATOR, navigator);
        outState.putSerializable(STATE_SELECTED_ITEMS, new LinkedList<>(selectedPaths));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (navigator.getCurrentDirectory() != null) {
            renderBreadcrumbsBar();
            if (mightGrantedAllFilesAccess) {
                mightGrantedAllFilesAccess = false;
                refresh();
            }
        }
        if (outingPath != null) {
            String path = outingPath;
            outingPath = null;
            scrollToAndHighlight(path, requireContext().getColor(R.color.purple_50));
        }
        askForAllFilesAccessIfNecessary();
    }

    private void askForAllFilesAccessIfNecessary() {
        if (askedAllFilesAccess) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            return;
        }
        askedAllFilesAccess = true;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.all_files_access_title)
                .setMessage(R.string.all_files_access_message)
                .setPositiveButton(R.string.go_to_settings, (DialogInterface dialog, int which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                        startActivity(intent);
                        mightGrantedAllFilesAccess = true;
                    } catch (ActivityNotFoundException e) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                            mightGrantedAllFilesAccess = true;
                        } catch (ActivityNotFoundException ignored) {
                            Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        propertiesLoader.cancel();
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
    }

    /**
     * null target for keeping the current directory (or the default if none).
     */
    public void navigateToDirectory(String target) {
        if (target == null) {
            if (navigator.getCurrentDirectory() != null) {
                return;
            }
            target = DEFAULT_DIRECTORY;
        }
        if (!navigator.navigateTo(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }
        refresh();
    }

    public void navigateBack() {
        String child = navigator.getCurrentDirectory();
        if (!navigator.goBack()) {
            renderActionBar();
            return;
        }
        refresh(() -> scrollToAndHighlight(child, requireContext().getColor(R.color.purple_50)));
    }

    private void navigateForward() {
        String child = navigator.getCurrentDirectory();
        if (!navigator.goForward()) {
            renderActionBar();
            return;
        }
        refresh(() -> scrollToAndHighlight(child, requireContext().getColor(R.color.purple_50)));
    }

    public boolean navigateUp() {
        String child = navigator.getCurrentDirectory();
        if (!navigator.goUp()) {
            renderActionBar();
            return false;
        }
        refresh(() -> scrollToAndHighlight(child, requireContext().getColor(R.color.purple_50)));
        return true;
    }

    public boolean handleSystemBack() {
        if (!selectedPaths.isEmpty()) {
            deselectAll();
            return true;
        }
        return navigateUp();
    }

    private void toggleFavorite() {
        String currentDirectory = navigator.getCurrentDirectory();
        if (currentDirectory == null) {
            return;
        }

        if (favoritesStore.contains(currentDirectory)) {
            favoritesStore.remove(currentDirectory);
        } else {
            favoritesStore.put(currentDirectory);
        }
        breadcrumbBar.render(new BreadcrumbBar.State(currentDirectory, favoritesStore.contains(currentDirectory)));
    }

    private void onItemClicked(String path) {
        if (!selectedPaths.isEmpty()) {
            toggleSelection(path);
            return;
        }
        openItem(path);
    }

    private void openItem(String path) {
        FileStat fileStat = FileOps.singleton.stat(path);
        if (fileStat.isDirectory(true)) {
            navigateToDirectory(path);
        } else if (fileStat.isFile(true)) {
            openFile(path);
        } else {
            Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(String path) {
        if (!FileOps.singleton.stat(path).exists(true)) {
            Toast.makeText(requireContext(), R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer position = itemPositions.get(path);
        FileProperties item = position != null ? items.get(position) : null;
        String detectedMime = item != null && item.computed ? item.mimeType : FileMimeUtil.mimeByContent(path);
        if (GlryActivity.supports(detectedMime)) {
            Intent intent = new Intent(requireContext(), GlryActivity.class);
            intent.putExtra(GlryActivity.EXTRA_PATH, path);
            startActivity(intent);
            return;
        }

        File file = new File(path);
        Uri uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".file_provider",
                file);

        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = "*/*";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (file.canWrite()) {
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        try {
            startActivity(intent);
            outingPath = path;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.error_no_app_to_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void jumpTo(String path) {
        if (!FileOps.singleton.stat(path).exists(true)) {
            Toast.makeText(requireContext(), R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        String parent = PathOps.singleton.dirname(path);
        if (!FileOps.singleton.stat(parent).exists(true)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        // navigate to parent directory
        navigator.navigateTo(parent);

        refresh(() -> scrollToAndHighlight(path));
    }

    private void showPastePopup(Collection<FileProperties> srcItems, boolean isCopy) {
        PastePopup pastePopup = new PastePopup(getView(), isCopy, srcItems, navigator.getCurrentDirectory());
        pastePopup.whenPopupDismissed((added, removed) -> {
            onPopupDismissed(added, removed);
            if (!added.isEmpty() || !removed.isEmpty()) {
                deselectAll();
            }
        });
        pastePopup.show();
    }

    private void showDeletePopup(Collection<FileProperties> srcItems) {
        DeletePopup deletePopup = new DeletePopup(getView(), navigator.getCurrentDirectory(), srcItems);
        deletePopup.whenPopupDismissed((added, removed) -> {
            onPopupDismissed(added, removed);
            if (!added.isEmpty() || !removed.isEmpty()) {
                deselectAll();
            }
        });
        deletePopup.show();
    }

    private void showRenamePopup(FileProperties srcItem) {
        RenamePopup renamePopup = new RenamePopup(getView(), srcItem);
        renamePopup.whenPopupDismissed((added, removed) -> {
            onPopupDismissed(added, removed);
            if (!added.isEmpty() || !removed.isEmpty()) {
                deselectAll();
            }
            if (!added.isEmpty()) {
                scrollToAndHighlight(added.iterator().next().path);
            }
        });
        renamePopup.show();
    }

    private void showCreatePopup(boolean isDirectory) {
        CreatePopup createPopup = new CreatePopup(getView(), navigator.getCurrentDirectory(), isDirectory);
        createPopup.whenPopupDismissed((added, removed) -> {
            onPopupDismissed(added, removed);
            if (!added.isEmpty()) {
                scrollToAndHighlight(added.iterator().next().path);
            }
        });
        createPopup.show();
    }

    private void showSearchPopup() {
        SearchPopup popup = new SearchPopup(getView(), navigator.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpTo);
        popup.whenCopyClicked(items -> showPastePopup(items, true));
        popup.whenCutClicked(items -> showPastePopup(items, false));
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showFindEmptyPopup() {
        FindEmptyPopup popup = new FindEmptyPopup(getView(), navigator.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpTo);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showFindDupPopup() {
        FindDupPopup popup = new FindDupPopup(getView(), navigator.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpTo);
        popup.whenCopyClicked(items -> showPastePopup(items, true));
        popup.whenCutClicked(items -> showPastePopup(items, false));
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void onPopupDismissed(Collection<FileProperties> added, Collection<FileProperties> removed) {
        String currentDirectory = navigator.getCurrentDirectory();
        if (!added.isEmpty()) {
            Map<String, FileProperties> explicit = new LinkedHashMap<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, added, explicit, implicit);
            addItems(explicit.values());
            loadProperties(implicit);
        }
        if (!removed.isEmpty()) {
            for (FileProperties item : removed) {
                selectedPaths.remove(item.path);
            }
            renderSelectionBar();
            Map<String, FileProperties> explicit = new LinkedHashMap<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, removed, explicit, implicit);
            removeItems(explicit.values());
            loadProperties(implicit);
        }
    }

    private void getDirectChildren(String directory, Collection<FileProperties> items,
            Map<String, FileProperties> outExplicit, Set<String> outImplicit) {
        for (FileProperties item : items) {
            String directChild = getDirectChild(directory, item.path);
            if (directChild == null) {
                continue;
            }
            if (directChild.equals(item.path)) {
                outExplicit.put(item.path, item);
            } else {
                outImplicit.add(directChild);
            }
        }
        outImplicit.removeAll(outExplicit.keySet());
    }

    private String getDirectChild(String directory, String path) {
        while (true) {
            String parent = PathOps.singleton.dirname(path);
            if (parent.equals(path)) {
                return null;
            }
            if (parent.equals(directory)) {
                return path;
            }
            path = parent;
        }
    }
}
