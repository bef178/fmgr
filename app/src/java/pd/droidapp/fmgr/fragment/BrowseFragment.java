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
import pd.droidapp.fmgr.popup.DedupPopup;
import pd.droidapp.fmgr.popup.DeleteEmptyPopup;
import pd.droidapp.fmgr.popup.DeletePopup;
import pd.droidapp.fmgr.popup.EditPopup;
import pd.droidapp.fmgr.popup.PastePopup;
import pd.droidapp.fmgr.popup.SearchPopup;
import pd.droidapp.fmgr.util.ActionBar;
import pd.droidapp.fmgr.util.Clipboard;
import pd.droidapp.fmgr.util.FavStore;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.toFileProperties;

public class BrowseFragment extends Fragment {

    private final Clipboard clipboard = new Clipboard();
    private FavStore favStore;
    private PathBar pathBar;
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

        favStore = new FavStore(requireContext());
        pathBar = new PathBar(view.findViewById(R.id.path_bar));
        pathBar.whenBreadcrumbClicked(this::navigateToDirectory);
        pathBar.whenFavIconClicked(this::toggleFavorite);

        ImageButton homeButton = view.findViewById(R.id.action_home);
        homeButton.setOnClickListener(v -> navigateToHome());

        actionBar = new ActionBar(view.findViewById(R.id.action_bar));
        actionBar.addButton(R.drawable.action_back, () -> navigator.canGoBack(), this::navigateBack);
        actionBar.addButton(R.drawable.action_forward, () -> navigator.canGoForward(), this::navigateForward);
        actionBar.addButton(R.drawable.action_up, () -> navigator.canGoUp(), this::navigateUp);
        actionBar.addButton(R.drawable.baseline_refresh_24, () -> true, this::refresh);
        actionBar.addPopupButton(R.drawable.i_directory_add_24, this::showCreateDirectoryPopup);
        actionBar.addPopupButton(R.drawable.i_file_add_24, this::showCreateFilePopup);
        actionBar.addPopupButton(R.drawable.i_paste_go_24, clipboard::toCut, this::showPastePopup);
        actionBar.addPopupButton(R.drawable.baseline_search_24, this::showSearchPopup);
        actionBar.addPopupButton(R.drawable.i_paste_24, clipboard::toCopy, this::showPastePopup);
        actionBar.addPopupButton(R.drawable.i_delete_empty_24, this::showDeleteEmptyPopup);
        actionBar.addPopupButton(R.drawable.i_delete_copy_24, this::showDedupPopup);

        selectionBar = new SelectionBar(view.findViewById(R.id.selection_bar));

        itemsAdapter = new FileItemsAdapter(selectionBar);
        itemsAdapter.whenItemClicked(this::openItem);

        itemsView = view.findViewById(R.id.items_list);
        itemsView.setLayoutManager(new LinearLayoutManager(requireContext()));
        itemsView.setAdapter(itemsAdapter);

        selectionBar.addButton(R.layout.selection_button_rename, c -> c == 1, v -> {
            if (selectionBar.size() == 1) {
                showRenamePopup(selectionBar.getFirst());
            }
        });
        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> markSelectedItemsForCopy());
        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> markSelectedItemsForCut());
        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> showDeletePopup());

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addProps(itemsAdapter.getItems());
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }

        return view;
    }

    private void refresh() {
        String currentDirectory = navigator.getCurrentDirectory();
        pathBar.set(currentDirectory, currentDirectory != null && favStore.contains(currentDirectory));
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        loadItems(currentDirectory);
    }

    private void loadItems(String directory) {
        List<String> paths = new LinkedList<>();
        if (directory != null) {
            FileOps.singleton.listDirectory(directory, 1, true, null,
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
            selectionBar.add(savedSelectedItems);
            selectionBar.invalidate();
            itemsAdapter.invalidate(itemsAdapter.getSelectedItems());
        }

        actionBar.invalidate();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_NAVIGATOR, navigator);
        outState.putSerializable(STATE_SELECTED_ITEMS, new LinkedList<>(selectionBar.getAll()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (navigator.getCurrentDirectory() == null) {
            navigateToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
        } else if (mightGrantedAllFilesAccess) {
            mightGrantedAllFilesAccess = false;
            loadItems(navigator.getCurrentDirectory());
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
            actionBar.invalidate();
            return false;
        }
        refresh();
        return true;
    }

    private void navigateForward() {
        if (!navigator.goForward()) {
            actionBar.invalidate();
            return;
        }
        refresh();
    }

    private void navigateUp() {
        if (!navigator.goUp()) {
            actionBar.invalidate();
            return;
        }
        refresh();
    }

    private void toggleFavorite() {
        String currentDirectory = navigator.getCurrentDirectory();
        if (currentDirectory == null) {
            return;
        }

        if (favStore.contains(currentDirectory)) {
            favStore.remove(currentDirectory);
            Toast.makeText(requireContext(), R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favStore.put(currentDirectory);
            Toast.makeText(requireContext(), R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        pathBar.set(currentDirectory, favStore.contains(currentDirectory));
    }

    private void openItem(String path, boolean isDirectory) {
        if (isDirectory) {
            navigateToDirectory(path);
        } else {
            openFile(new File(path));
        }
    }

    private void openFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

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
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.error_no_app_to_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void jumpToFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(requireContext(), R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        File parent = file.getParentFile();
        if (parent == null || !parent.exists()) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        // navigate to parent directory
        navigator.navigateTo(parent.getPath());
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

        File newFile = new File(navigator.getCurrentDirectory(), name);
        if (newFile.exists()) {
            Toast.makeText(requireContext(), R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        boolean success;
        try {
            if (isDirectory) {
                success = newFile.mkdirs();
                if (success) {
                    Toast.makeText(requireContext(), R.string.directory_created, Toast.LENGTH_SHORT).show();
                }
            } else {
                success = newFile.createNewFile();
                if (success) {
                    Toast.makeText(requireContext(), R.string.file_created, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            success = false;
        }

        if (!success) {
            Toast.makeText(requireContext(), R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        loadItems(navigator.getCurrentDirectory());
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

    private void markSelectedItemsForCut() {
        List<FileProperties> items = itemsAdapter.getSelectedItems();
        clipboard.setItemsToCut(items);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, items.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.invalidate(items);
    }

    private void markSelectedItemsForCopy() {
        List<FileProperties> items = itemsAdapter.getSelectedItems();
        clipboard.setItemsToCopy(items);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, items.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.invalidate(items);
    }

    private void copyToClipboard(Collection<FileProperties> items) {
        clipboard.setItemsToCopy(items);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, items.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void cutToClipboard(Collection<FileProperties> items) {
        clipboard.setItemsToCut(items);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, items.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void showPastePopup() {
        boolean isCopy;
        List<FileProperties> srcItems;
        if (clipboard.toCut()) {
            isCopy = false;
            srcItems = clipboard.getItemsToCut();
        } else if (clipboard.toCopy()) {
            isCopy = true;
            srcItems = clipboard.getItemsToCopy();
        } else {
            return;
        }

        PastePopup pastePopup = new PastePopup(getView(), isCopy, srcItems, navigator.getCurrentDirectory());
        pastePopup.whenPopupDismissed(this::onPopupDismissed);
        pastePopup.show();
    }

    private void showDeletePopup() {
        DeletePopup deletePopup = new DeletePopup(getView(), itemsAdapter.getSelectedItems(), false);
        deletePopup.whenPopupDismissed(this::onPopupDismissed);
        deletePopup.show();
    }

    private void showRenamePopup(String path) {
        String currentName = PathOps.singleton.basename(path);
        EditPopup editPopup = new EditPopup(getView(),
                getString(R.string.rename),
                currentName,
                currentName,
                newName -> {
                    newName = newName.trim();
                    if (newName.isEmpty() || newName.equals(currentName) || renameItem(path, newName)) {
                        selectionBar.clear();
                        selectionBar.invalidate();
                        return true;
                    }
                    return false;
                });
        editPopup.show();
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
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDeleteEmptyPopup() {
        DeleteEmptyPopup popup = new DeleteEmptyPopup(getView(), navigator.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDedupPopup() {
        DedupPopup popup = new DedupPopup(getView(), navigator.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void onPopupDismissed(Collection<FileProperties> added, Collection<FileProperties> removed) {
        String currentDirectory = navigator.getCurrentDirectory();
        if (!added.isEmpty()) {
            clipboard.clear();
            actionBar.invalidate();
            selectionBar.invalidate();
            Map<String, FileProperties> explicit = new LinkedHashMap<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, added, explicit, implicit);
            itemsAdapter.add(explicit.values());
            itemsAdapter.loadProperties(implicit);
        }
        if (!removed.isEmpty()) {
            clipboard.removeAllIfSameAsOrDescendantOf(removed);
            actionBar.invalidate();
            selectionBar.removeProps(removed);
            selectionBar.invalidate();
            Map<String, FileProperties> explicit = new LinkedHashMap<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, removed, explicit, implicit);
            itemsAdapter.remove(explicit.values());
            itemsAdapter.loadProperties(implicit);
        }
    }

    private void getDirectChildren(String currentDirectory, Collection<FileProperties> items,
            Map<String, FileProperties> outExplicit, Set<String> outImplicit) {
        for (FileProperties item : items) {
            String directChild = getDirectChild(currentDirectory, item.path);
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

    private String getDirectChild(String currentDirectory, String path) {
        while (true) {
            String parent = PathOps.singleton.dirname(path);
            if (parent.equals(path)) {
                return null;
            }
            if (parent.equals(currentDirectory)) {
                return path;
            }
            path = parent;
        }
    }
}
