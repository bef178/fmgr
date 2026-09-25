package pd.droidapp.fmgr.fragment;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pd.droidapp.fmgr.MainActivity;
import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.DeletePopup;
import pd.droidapp.fmgr.popup.EditPopup;
import pd.droidapp.fmgr.popup.FindDupPopup;
import pd.droidapp.fmgr.popup.FindEmptyPopup;
import pd.droidapp.fmgr.popup.PastePopup;
import pd.droidapp.fmgr.popup.SearchPopup;
import pd.droidapp.fmgr.util.FavoritesStore;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ActionBar;
import pd.droidapp.fmgr.view.BreadcrumbsBar;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.SelectionBar;
import pd.util.FileOps;
import pd.util.FileStat;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

public class BrowseFragment extends Fragment {

    private FavoritesStore favoritesStore;
    private BreadcrumbsBar breadcrumbsBar;
    private ActionBar actionBar;
    private SelectionBar selectionBar;
    private RecyclerView itemsView;
    private FileItemsAdapter itemsAdapter;

    private PathNavigator navigator = new PathNavigator();

    private boolean askedAllFilesAccess;
    private boolean mightGrantedAllFilesAccess;

    private static final String STATE_NAVIGATOR = "navigator";
    private static final String STATE_SELECTED_ITEMS = "selected_items";

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.setBrowseFragment(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        favoritesStore = new FavoritesStore(requireContext());
        breadcrumbsBar = new BreadcrumbsBar(view.findViewById(R.id.breadcrumbs_bar));
        breadcrumbsBar.whenBreadcrumbClicked(this::navigateToDirectory);
        breadcrumbsBar.whenFavIconClicked(this::toggleFavorite);

        ImageButton homeButton = view.findViewById(R.id.action_home);
        homeButton.setOnClickListener(v -> navigateToHome());

        actionBar = new ActionBar(view.findViewById(R.id.action_bar));
        actionBar.whenButtonClicked(id -> {
            if (id == R.drawable.action_back) {
                navigateBack();
            } else if (id == R.drawable.action_forward) {
                navigateForward();
            } else if (id == R.drawable.action_up) {
                navigateUp();
            } else if (id == R.drawable.baseline_refresh_24) {
                refresh();
            } else if (id == R.drawable.i_directory_add_24) {
                showCreateDirectoryPopup();
            } else if (id == R.drawable.i_file_add_24) {
                showCreateFilePopup();
            } else if (id == R.drawable.baseline_search_24) {
                showSearchPopup();
            } else if (id == R.drawable.ic_find_empty_24) {
                showFindEmptyPopup();
            } else if (id == R.drawable.ic_find_dup_24) {
                showFindDupPopup();
            }
        });

        selectionBar = new SelectionBar(view.findViewById(R.id.selection_bar));

        itemsAdapter = new FileItemsAdapter();
        itemsAdapter.whenItemClicked(this::openItem);
        itemsAdapter.whenSelectionChanged(this::renderSelectionBar);

        itemsView = view.findViewById(R.id.items_list);
        itemsView.setLayoutManager(new LinearLayoutManager(requireContext()));
        itemsView.setAdapter(itemsAdapter);

        initSelectionBar();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        renderActionBar();

        return view;
    }

    private void refresh() {
        renderBreadcrumbsBar();
        renderActionBar();
        itemsAdapter.clearSelection();
        loadItems(navigator.getCurrentDirectory());
    }

    private void renderBreadcrumbsBar() {
        String currentDirectory = navigator.getCurrentDirectory();
        breadcrumbsBar.render(new BreadcrumbsBar.State(currentDirectory, favoritesStore.contains(currentDirectory)));
    }

    private void renderActionBar() {
        actionBar.render(new ActionBar.State(
                new ButtonState[] {
                        new ButtonState(R.drawable.action_back, true, navigator.canGoBack()),
                        new ButtonState(R.drawable.action_forward, true, navigator.canGoForward()),
                        new ButtonState(R.drawable.action_up, true, navigator.canGoUp()),
                        new ButtonState(R.drawable.baseline_refresh_24),
                },
                new ButtonState(R.drawable.i_directory_add_24),
                new ButtonState(R.drawable.i_file_add_24),
                new ButtonState(R.drawable.baseline_search_24),
                new ButtonState(R.drawable.ic_find_empty_24),
                new ButtonState(R.drawable.ic_find_dup_24)));
    }

    private void initSelectionBar() {
        selectionBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_edit_24) {
                if (itemsAdapter.getSelectedCount() == 1) {
                    showRenamePopup(itemsAdapter.getSelectedItems().get(0).path);
                }
            } else if (id == R.drawable.ic_copy_24) {
                List<FileProperties> items = itemsAdapter.getSelectedItems();
                itemsAdapter.clearSelection();
                itemsAdapter.invalidate(items);
                showPastePopup(items, true);
            } else if (id == R.drawable.ic_cut_24) {
                List<FileProperties> items = itemsAdapter.getSelectedItems();
                itemsAdapter.clearSelection();
                itemsAdapter.invalidate(items);
                showPastePopup(items, false);
            } else if (id == R.drawable.ic_delete_24) {
                showDeletePopup();
            } else if (id == R.drawable.ic_check_all_24) {
                itemsAdapter.selectAll();
                itemsAdapter.notifyDataSetChanged();
            } else if (id == R.drawable.ic_close_24) {
                itemsAdapter.clearSelection();
                itemsAdapter.notifyDataSetChanged();
            }
        });
    }

    private void renderSelectionBar() {
        int numSelected = itemsAdapter.getSelectedCount();
        selectionBar.render(new SelectionBar.State(numSelected,
                new ButtonState(R.drawable.ic_edit_24, numSelected == 1),
                new ButtonState(R.drawable.ic_copy_24, numSelected > 0),
                new ButtonState(R.drawable.ic_cut_24, numSelected > 0),
                new ButtonState(R.drawable.ic_delete_24, numSelected > 0),
                new ButtonState(R.drawable.ic_check_all_24, numSelected > 0),
                new ButtonState(R.drawable.ic_close_24, numSelected > 0)));
    }

    private void loadItems(String directory) {
        List<String> paths = new LinkedList<>();
        if (directory != null) {
            FileOps.singleton.listDirectory(directory, 1, false, null,
                    (action, src, dst, succeeded) -> {
                        if (action == FileOps.Action.MEET) {
                            paths.add(src);
                        }
                    });
        }
        itemsAdapter.set(toFileProperties(paths));
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
            itemsAdapter.select(savedSelectedItems);
            itemsAdapter.invalidate(itemsAdapter.getSelectedItems());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_NAVIGATOR, navigator);
        outState.putSerializable(STATE_SELECTED_ITEMS, new LinkedList<>(itemsAdapter.getSelectedPaths()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (navigator.getCurrentDirectory() == null) {
            navigateToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
        } else {
            renderBreadcrumbsBar();
            if (mightGrantedAllFilesAccess) {
                mightGrantedAllFilesAccess = false;
                loadItems(navigator.getCurrentDirectory());
            }
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
        itemsAdapter.cancel();
    }

    public void navigateToDirectory(String target) {
        if (!navigator.navigateTo(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }
        refresh();
    }

    private void navigateToHome() {
        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.navigateToHome();
    }

    public boolean navigateBack() {
        if (!navigator.goBack()) {
            renderActionBar();
            return false;
        }
        refresh();
        return true;
    }

    private void navigateForward() {
        if (!navigator.goForward()) {
            renderActionBar();
            return;
        }
        refresh();
    }

    private void navigateUp() {
        if (!navigator.goUp()) {
            renderActionBar();
            return;
        }
        refresh();
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
        breadcrumbsBar.render(new BreadcrumbsBar.State(currentDirectory, favoritesStore.contains(currentDirectory)));
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

        Uri uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".file_provider",
                new File(path));

        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = "*/*";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);

        try {
            startActivity(intent);
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
        refresh();

        // scroll to and highlight the item
        itemsView.post(() -> {
            int position = itemsAdapter.indexOf(path);
            if (position >= 0) {
                itemsView.scrollToPosition(position);
                itemsView.postDelayed(() -> itemsAdapter.highlightItem(path), 100);
            }
        });
    }

    void showCreateDirectoryPopup() {
        EditPopup editPopup = new EditPopup(getView(),
                getString(R.string.new_directory),
                "",
                getString(R.string.directory_name),
                name -> createItem(name.trim(), true));
        editPopup.show();
    }

    private void showCreateFilePopup() {
        EditPopup editPopup = new EditPopup(getView(),
                getString(R.string.new_file),
                "",
                getString(R.string.file_name),
                name -> createItem(name.trim(), false));
        editPopup.show();
    }

    private boolean createItem(String name, boolean isDirectory) {
        Integer errResId = checkBasename(name);
        if (errResId != null) {
            Toast.makeText(requireContext(), errResId, Toast.LENGTH_SHORT).show();
            return false;
        }

        String currentDirectory = navigator.getCurrentDirectory();
        if (currentDirectory == null) {
            Toast.makeText(requireContext(), R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        String newPath = PathOps.singleton.join(currentDirectory, name);
        if (FileOps.singleton.stat(newPath).exists(true)) {
            Toast.makeText(requireContext(), R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        boolean success;
        if (isDirectory) {
            success = FileOps.singleton.createEmptyDirectory(newPath);
            if (success) {
                Toast.makeText(requireContext(), R.string.directory_created, Toast.LENGTH_SHORT).show();
            }
        } else {
            success = FileOps.singleton.save(newPath, new byte[0], false);
            if (success) {
                Toast.makeText(requireContext(), R.string.file_created, Toast.LENGTH_SHORT).show();
            }
        }

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        loadItems(currentDirectory);
        return true;
    }

    /**
     * return `null` or error string resource id
     */
    private Integer checkBasename(String name) {
        if (name == null || name.isEmpty()) {
            return R.string.error_empty_name;
        }

        if (name.equals(".") || name.equals("..")) {
            return R.string.error_invalid_name;
        }

        if (name.contains("/") || name.contains("\\")) {
            return R.string.error_invalid_name;
        }

        // check for control characters (ASCII 0-31)
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32) {
                return R.string.error_invalid_name;
            }
        }

        return null;
    }

    private void showPastePopup(Collection<FileProperties> srcItems, boolean isCopy) {
        PastePopup pastePopup = new PastePopup(getView(), isCopy, srcItems, navigator.getCurrentDirectory());
        pastePopup.whenPopupDismissed(this::onPopupDismissed);
        pastePopup.show();
    }

    private void showDeletePopup() {
        DeletePopup deletePopup = new DeletePopup(getView(), navigator.getCurrentDirectory(), itemsAdapter.getSelectedItems(), false);
        deletePopup.whenPopupDismissed(this::onPopupDismissed);
        deletePopup.show();
    }

    private void showRenamePopup(String path) {
        String currentName = PathOps.singleton.basename(path);
        EditPopup editPopup = new EditPopup(getView(),
                getString(R.string.rename),
                suggestNewName(currentName),
                currentName,
                newName -> {
                    newName = newName.trim();
                    if (newName.isEmpty() || newName.equals(currentName) || renameItem(path, newName)) {
                        itemsAdapter.clearSelection();
                        return true;
                    }
                    return false;
                });
        editPopup.show();
    }

    private static String suggestNewName(String name) {
        {
            String name1 = name.trim();
            if (name1.isEmpty()) {
                return name;
            }
            name = name1;
        }

        String extension = PathOps.singleton.extname(name);
        String core = PathOps.singleton.basename(name, extension);

        {
            String core1 = core.replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1");
            if (!core1.isEmpty()) {
                core = core1;
            }
        }
        {
            String core1 = core;
            String prefix = "";
            if (core1.charAt(0) == '.') {
                prefix = ".";
                core1 = core1.substring(1);
            }
            String core2 = core1.replaceAll("^[A-Za-z0-9.]*@", "");
            if (!core2.isEmpty()) {
                core1 = core2;
            }
            core = prefix + core1;
        }

        if (!extension.isEmpty()) {
            String ext1 = extension
                    .substring(1)
                    .replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1")
                    .trim();
            if (!ext1.isEmpty()) {
                extension = "." + ext1;
            }
        }
        return core + extension;
    }

    private boolean renameItem(String path, String newName) {
        Integer errResId = checkBasename(newName);
        if (errResId != null) {
            Toast.makeText(requireContext(), errResId, Toast.LENGTH_SHORT).show();
            return false;
        }

        String newPath = PathOps.singleton.resolve(PathOps.singleton.dirname(path), newName);
        if (FileOps.singleton.stat(newPath).exists(true)) {
            Toast.makeText(requireContext(), R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (FileOps.singleton.move(path, newPath, null)) {
            Toast.makeText(requireContext(), R.string.renamed, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        FileProperties oldItem = itemsAdapter.remove(path);
        if (oldItem != null) {
            itemsAdapter.add(Collections.singletonList(new FileProperties(newPath, oldItem.isDirectory)));
        }
        return true;
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
            itemsAdapter.add(explicit.values());
            itemsAdapter.loadProperties(implicit);
        }
        if (!removed.isEmpty()) {
            itemsAdapter.deselect(removed);
            Map<String, FileProperties> explicit = new LinkedHashMap<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, removed, explicit, implicit);
            itemsAdapter.remove(explicit.values());
            itemsAdapter.loadProperties(implicit);
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
