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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

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
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.toFileProperties;
import static pd.droidapp.fmgr.util.Util.toNormalizedPaths;

public class BrowseFragment extends Fragment {

    private final Clipboard clipboard = new Clipboard();
    private PathBar pathBar;
    private ActionBar actionBar;
    private SelectionBar selectionBar;
    private RecyclerView itemsView;
    private FileItemsAdapter itemsAdapter;

    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    private boolean askedAllFilesAccess;
    private boolean mightGrantedAllFilesAccess;

    private static final String STATE_CURRENT_DIRECTORY = "current_directory";
    private static final String STATE_BACK_STACK = "back_stack";
    private static final String STATE_FORWARD_STACK = "forward_stack";
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

        pathBar = new PathBar(view.findViewById(R.id.path_bar));
        pathBar.whenBreadcrumbClicked(this::navigateToDirectory);

        ImageButton homeButton = view.findViewById(R.id.action_home);
        homeButton.setOnClickListener(v -> navigateToHome());

        actionBar = new ActionBar(view.findViewById(R.id.action_bar));
        actionBar.addButton(R.drawable.action_back, () -> !backStack.isEmpty(), this::navigateBack);
        actionBar.addButton(R.drawable.action_forward, () -> !forwardStack.isEmpty(), this::navigateForward);
        actionBar.addButton(R.drawable.action_up, () -> getParentDirectory(pathBar.getCurrentDirectory()) != null,
                () -> navigateToDirectory(getParentDirectory(pathBar.getCurrentDirectory())));
        actionBar.addButton(R.drawable.baseline_refresh_24, () -> true,
                () -> doChangeCurrentDirectory(pathBar.getCurrentDirectory()));
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
            selectionBar.addFiles(itemsAdapter.getItems().stream()
                    .map(x -> new File(x.path))
                    .collect(Collectors.toList()));
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

    private void doChangeCurrentDirectory(File directory) {
        pathBar.invalidate(directory);
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        loadItems(directory);
    }

    private void loadItems(File directory) {
        List<String> paths = new LinkedList<>();
        if (directory != null) {
            FileOps.singleton.listDirectory(directory.getPath(), 1, true, null,
                    (action, src, dst, succeeded) -> {
                        if (action == FileOps.Action.MEET) {
                            paths.add(src);
                        }
                    });
        }
        itemsAdapter.set(toFileProperties(paths));
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        File currentDirectory = (File) savedInstanceState.getSerializable(STATE_CURRENT_DIRECTORY);
        if (validateDirectory(currentDirectory)) {
            doChangeCurrentDirectory(currentDirectory);
        }

        List<File> savedBackStack = (List<File>) savedInstanceState.getSerializable(STATE_BACK_STACK);
        if (savedBackStack != null) {
            backStack.addAll(savedBackStack);
        }

        List<File> savedForwardStack = (List<File>) savedInstanceState.getSerializable(STATE_FORWARD_STACK);
        if (savedForwardStack != null) {
            forwardStack.addAll(savedForwardStack);
        }

        List<File> savedSelectedItems = (List<File>) savedInstanceState.getSerializable(STATE_SELECTED_ITEMS);
        if (savedSelectedItems != null) {
            selectionBar.addFiles(savedSelectedItems);
            selectionBar.invalidate();
            itemsAdapter.invalidate(itemsAdapter.getSelectedPaths());
        }

        actionBar.invalidate();
    }

    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_CURRENT_DIRECTORY, pathBar.getCurrentDirectory());
        outState.putSerializable(STATE_BACK_STACK, new LinkedList<>(backStack));
        outState.putSerializable(STATE_FORWARD_STACK, new LinkedList<>(forwardStack));
        outState.putSerializable(STATE_SELECTED_ITEMS, new LinkedList<>(selectionBar.getSelectedFiles()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pathBar.getCurrentDirectory() == null) {
            doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        } else if (mightGrantedAllFilesAccess) {
            mightGrantedAllFilesAccess = false;
            loadItems(pathBar.getCurrentDirectory());
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

    public void navigateToDirectory(File target) {
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        File currentDirectory = pathBar.getCurrentDirectory();
        if (currentDirectory != null && !currentDirectory.equals(target)) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        doChangeCurrentDirectory(target);
    }

    private void navigateToHome() {
        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.navigateToHome();
    }

    public boolean navigateBack() {
        while (!backStack.isEmpty() && !validateDirectory(backStack.peek())) {
            backStack.pop();
        }
        if (backStack.isEmpty()) {
            actionBar.invalidate();
            return false;
        }

        File target = backStack.pop();
        forwardStack.push(pathBar.getCurrentDirectory());
        doChangeCurrentDirectory(target);
        return true;
    }

    private void navigateForward() {
        while (!forwardStack.isEmpty() && !validateDirectory(forwardStack.peek())) {
            forwardStack.pop();
        }
        if (forwardStack.isEmpty()) {
            actionBar.invalidate();
            return;
        }

        backStack.push(pathBar.getCurrentDirectory());
        File target = forwardStack.pop();
        doChangeCurrentDirectory(target);
    }

    private void openItem(String path, boolean isDirectory) {
        if (isDirectory) {
            navigateToDirectory(new File(path));
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

    public File getParentDirectory(File directory) {
        if (directory != null) {
            File parent = directory.getParentFile();
            if (parent != null && parent.exists()
                    && !Environment.getExternalStorageDirectory().equals(directory)) {
                return parent;
            }
        }
        return null;
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
        File currentDirectory = pathBar.getCurrentDirectory();
        if (currentDirectory != null && !currentDirectory.equals(parent)) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        doChangeCurrentDirectory(parent);

        // scroll to and highlight the file
        itemsView.post(() -> {
            int position = itemsAdapter.indexOf(file.getPath());
            if (position >= 0) {
                itemsView.scrollToPosition(position);
                itemsView.postDelayed(() -> itemsAdapter.highlightItem(PathOps.singleton.normalize(path)), 100);
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

        File newFile = new File(pathBar.getCurrentDirectory(), name);
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

        loadItems(pathBar.getCurrentDirectory());
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
        Collection<File> files = new LinkedList<>(selectionBar.getSelectedFiles());
        List<String> paths = itemsAdapter.getSelectedPaths();
        clipboard.setFilesToCut(files);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.invalidate(paths);
    }

    private void markSelectedItemsForCopy() {
        Collection<File> files = new LinkedList<>(selectionBar.getSelectedFiles());
        List<String> paths = itemsAdapter.getSelectedPaths();
        clipboard.setFilesToCopy(files);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.invalidate(paths);
    }

    private void copyToClipboard(Collection<String> paths) {
        clipboard.setFilesToCopy(paths.stream().map(File::new).collect(Collectors.toList()));
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, paths.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void cutToClipboard(Collection<String> paths) {
        clipboard.setFilesToCut(paths.stream().map(File::new).collect(Collectors.toList()));
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, paths.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void showPastePopup() {
        boolean isCopy;
        List<String> srcPaths;
        if (clipboard.toCut()) {
            isCopy = false;
            srcPaths = clipboard.getFilesToCut().stream().map(File::getPath).collect(Collectors.toList());
        } else if (clipboard.toCopy()) {
            isCopy = true;
            srcPaths = clipboard.getFilesToCopy().stream().map(File::getPath).collect(Collectors.toList());
        } else {
            return;
        }

        PastePopup pastePopup = new PastePopup(getView(), isCopy, srcPaths, pathBar.getCurrentDirectory().getPath());
        pastePopup.whenPopupDismissed(this::onPopupDismissed);
        pastePopup.show();
    }

    private void showDeletePopup() {
        DeletePopup deletePopup = new DeletePopup(getView(), itemsAdapter.getSelectedPaths(), false);
        deletePopup.whenPopupDismissed(this::onPopupDismissed);
        deletePopup.show();
    }

    private void showRenamePopup(File file) {
        String currentName = file.getName();
        EditPopup editPopup = new EditPopup(getView(),
                getString(R.string.rename),
                currentName,
                currentName,
                newName -> {
                    newName = newName.trim();
                    if (newName.isEmpty() || newName.equals(currentName) || renameItem(file, newName)) {
                        selectionBar.clear();
                        selectionBar.invalidate();
                        return true;
                    }
                    return false;
                });
        editPopup.show();
    }

    private boolean renameItem(File file, String newName) {
        Integer errResId = checkBasename(newName);
        if (errResId != null) {
            Toast.makeText(requireContext(), errResId, Toast.LENGTH_SHORT).show();
            return false;
        }

        File newFile = new File(file.getParentFile(), newName);
        if (newFile.exists()) {
            Toast.makeText(requireContext(), R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        boolean success = file.renameTo(newFile);
        if (success) {
            Toast.makeText(requireContext(), R.string.renamed, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        loadItems(pathBar.getCurrentDirectory());
        return true;
    }

    private void showSearchPopup() {
        SearchPopup popup = new SearchPopup(getView(), pathBar.getCurrentDirectory().getPath());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDeleteEmptyPopup() {
        DeleteEmptyPopup popup = new DeleteEmptyPopup(getView(), pathBar.getCurrentDirectory().getPath());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDedupPopup() {
        DedupPopup popup = new DedupPopup(getView(), pathBar.getCurrentDirectory().getPath());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void onPopupDismissed(Collection<String> added, Collection<String> removed) {
        String d = pathBar.getCurrentDirectory().getPath();
        String currentDirectory = d.endsWith("/") ? d : d + "/";
        if (!added.isEmpty()) {
            clipboard.clear();
            actionBar.invalidate();
            selectionBar.invalidate();
            Set<String> explicit = new LinkedHashSet<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, added, explicit, implicit);
            itemsAdapter.add(toFileProperties(explicit));
            itemsAdapter.loadProperties(toNormalizedPaths(implicit));
        }
        if (!removed.isEmpty()) {
            Set<File> removedFiles = removed.stream().map(File::new).collect(Collectors.toSet());
            clipboard.removeAllIfSameAsOrDescendantOf(removedFiles);
            actionBar.invalidate();
            selectionBar.remove(removed);
            selectionBar.invalidate();
            Set<String> explicit = new LinkedHashSet<>();
            Set<String> implicit = new LinkedHashSet<>();
            getDirectChildren(currentDirectory, removed, explicit, implicit);
            itemsAdapter.remove(toNormalizedPaths(explicit));
            itemsAdapter.loadProperties(toNormalizedPaths(implicit));
        }
    }

    private void getDirectChildren(String currentDirectory, Collection<String> paths,
            Collection<String> outExplicit, Collection<String> outImplicit) {
        Set<String> explicit = new LinkedHashSet<>();
        Set<String> implicit = new LinkedHashSet<>();
        for (String path : paths) {
            String directChild = getDirectChild(currentDirectory, path);
            if (directChild == null) {
                continue;
            }
            if (directChild.equals(path)) {
                outExplicit.add(path);
                explicit.add(path);
            } else {
                implicit.add(directChild);
            }
        }
        implicit.removeAll(explicit);
        outImplicit.addAll(implicit);
    }

    // `currentDirectory` must end with "/"
    private String getDirectChild(String currentDirectory, String path) {
        while (true) {
            String parent = PathOps.singleton.dirname(path);
            if (!parent.endsWith("/")) {
                parent += "/";
            }
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
