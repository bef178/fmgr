package pd.droidapp.fmgr;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.util.ActionBar;
import pd.droidapp.fmgr.util.Clipboard;
import pd.droidapp.fmgr.util.DedupPopup;
import pd.droidapp.fmgr.util.DeleteEmptyPopup;
import pd.droidapp.fmgr.util.DeletePopup;
import pd.droidapp.fmgr.util.EditPopup;
import pd.droidapp.fmgr.util.PastePopup;
import pd.droidapp.fmgr.util.PathBar;
import pd.droidapp.fmgr.util.Progressor;
import pd.droidapp.fmgr.util.SearchPopup;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.droidapp.fmgr.util.Util;

import static pd.droidapp.fmgr.util.Util.getSizeString;

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

        itemsAdapter = new FileItemsAdapter();

        itemsView = view.findViewById(R.id.file_list);
        itemsView.setLayoutManager(new LinearLayoutManager(requireContext()));
        itemsView.setAdapter(itemsAdapter);

        selectionBar.addButton(R.layout.selection_button_rename, c -> c == 1, v -> {
            if (selectionBar.selectedItems.size() == 1) {
                showRenamePopup(selectionBar.selectedItems.iterator().next());
            }
        });
        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> markSelectedItemsForCopy());
        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> markSelectedItemsForCut());
        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> showDeletePopup());

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addAll(itemsAdapter.getFiles());
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
            selectionBar.addAll(savedSelectedItems);
            selectionBar.invalidate();
            itemsAdapter.invalidateItems(selectionBar.selectedItems);
        }

        actionBar.invalidate();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_CURRENT_DIRECTORY, pathBar.getCurrentDirectory());
        outState.putSerializable(STATE_BACK_STACK, new LinkedList<>(backStack));
        outState.putSerializable(STATE_FORWARD_STACK, new LinkedList<>(forwardStack));
        outState.putSerializable(STATE_SELECTED_ITEMS, new LinkedList<>(selectionBar.selectedItems));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pathBar.getCurrentDirectory() == null) {
            doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        } else if (mightGrantedAllFilesAccess) {
            mightGrantedAllFilesAccess = false;
            itemsAdapter.invalidate(pathBar.getCurrentDirectory());
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

    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    private void doChangeCurrentDirectory(File directory) {
        pathBar.invalidate(directory);
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.invalidate(directory);
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

    private void jumpToFile(File file) {
        if (file == null || !file.exists()) {
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
            int position = itemsAdapter.indexOf(file);
            if (position >= 0) {
                itemsView.scrollToPosition(position);
                itemsView.postDelayed(() -> itemsAdapter.highlightItem(file), 100);
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

        itemsAdapter.invalidate(pathBar.getCurrentDirectory());
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

        if (name.contains("/") || name.contains("\\") || name.contains("\0")) {
            return R.string.error_invalid_name;
        }

        // check for control characters (ASCII 0-31)
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32) {
                return R.string.error_invalid_name;
            }
        }

        // Check for leading/trailing dots or spaces (can cause issues on some systems)
        if (name.startsWith(".") || name.endsWith(".") || name.startsWith(" ") || name.endsWith(" ")) {
            return R.string.error_invalid_name;
        }

        return null;
    }

    private void markSelectedItemsForCut() {
        List<File> files = selectionBar.copySelectedItems();
        clipboard.setFilesToCut(files);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        for (File file : files) {
            int i = itemsAdapter.indexOf(file);
            if (i >= 0) {
                itemsAdapter.notifyItemChanged(i);
            }
        }
    }

    private void markSelectedItemsForCopy() {
        List<File> files = selectionBar.copySelectedItems();
        clipboard.setFilesToCopy(files);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        for (File file : files) {
            int i = itemsAdapter.indexOf(file);
            if (i >= 0) {
                itemsAdapter.notifyItemChanged(i);
            }
        }
    }

    private void copyToClipboard(Collection<File> files) {
        clipboard.setFilesToCopy(new LinkedList<>(files));
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void cutToClipboard(Collection<File> files) {
        clipboard.setFilesToCut(new LinkedList<>(files));
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
    }

    private void showPastePopup() {
        boolean isCopy;
        List<File> srcFiles;
        if (clipboard.toCut()) {
            isCopy = false;
            srcFiles = clipboard.getFilesToCut();
        } else if (clipboard.toCopy()) {
            isCopy = true;
            srcFiles = clipboard.getFilesToCopy();
        } else {
            return;
        }

        PastePopup pastePopup = new PastePopup(getView(), isCopy, srcFiles, pathBar.getCurrentDirectory());
        pastePopup.whenPopupDismissed(this::onPopupDismissed);
        pastePopup.show();
    }

    private void showDeletePopup() {
        DeletePopup deletePopup = new DeletePopup(getView(), selectionBar.copySelectedItems(), false);
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

        itemsAdapter.invalidate(pathBar.getCurrentDirectory());
        return true;
    }

    private void showSearchPopup() {
        SearchPopup popup = new SearchPopup(getView(), pathBar.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDeleteEmptyPopup() {
        DeleteEmptyPopup popup = new DeleteEmptyPopup(getView(), pathBar.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void showDedupPopup() {
        DedupPopup popup = new DedupPopup(getView(), pathBar.getCurrentDirectory());
        popup.whenJumpClicked(this::jumpToFile);
        popup.whenCopyClicked(this::copyToClipboard);
        popup.whenCutClicked(this::cutToClipboard);
        popup.whenPopupDismissed(this::onPopupDismissed);
        popup.show();
    }

    private void onPopupDismissed(Collection<String> addedItems, Collection<File> removedItems) {
        if (!addedItems.isEmpty()) {
            clipboard.clear();
            actionBar.invalidate();
            selectionBar.invalidate();
            File currentDirectory = pathBar.getCurrentDirectory();
            itemsAdapter.addAll(addedItems.stream()
                    .map(File::new)
                    .filter(file -> currentDirectory != null
                            && currentDirectory.equals(file.getParentFile()))
                    .collect(Collectors.toList()));
        }
        if (!removedItems.isEmpty()) {
            clipboard.removeAllIfSameAsOrDescendantOf(removedItems);
            actionBar.invalidate();
            selectionBar.selectedItems.removeAll(removedItems);
            selectionBar.invalidate();
            itemsAdapter.removeAll(removedItems);
        }
    }

    private class FileItemsAdapter extends RecyclerView.Adapter<FileItemsAdapter.FileItemViewHolder> {

        private final Comparator<File> fileComparator = (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            } else {
                return f1.getName().compareTo(f2.getName());
            }
        };

        private final List<FileItem> fileItems = new LinkedList<>();
        private final Progressor<File> progressor = new Progressor<>();

        public List<File> getFiles() {
            return fileItems.stream().map(x -> x.file).collect(Collectors.toList());
        }

        public int indexOf(File file) {
            for (int i = 0; i < fileItems.size(); i++) {
                if (fileItems.get(i).file.equals(file)) {
                    return i;
                }
            }
            return -1;
        }

        public void highlightItem(final File file) {
            progressor.start(file, 1500, new AccelerateDecelerateInterpolator(), (distance, velocity) -> {
                int position = indexOf(file);
                if (position >= 0) {
                    RecyclerView.ViewHolder viewHolder = itemsView.findViewHolderForAdapterPosition(position);
                    if (viewHolder != null) {
                        FileItem item = fileItems.get(position);
                        if (item.getFile().equals(file)) {
                            applyHighlightEffect(((FileItemViewHolder) viewHolder).fileHighlightView, velocity);
                        }
                    }
                }
            });
        }

        private void applyHighlightEffect(View view, Float velocity) {
            TypedValue typedValue = new TypedValue();
            requireContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true);
            final int peakColor = typedValue.data;

            requireContext().getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
            final int baseColor = typedValue.data & 0x00FFFFFF;

            if (velocity == null) {
                velocity = 0f;
            }

            int peakA = Color.alpha(peakColor);
            int peakR = Color.red(peakColor);
            int peakG = Color.green(peakColor);
            int peakB = Color.blue(peakColor);

            int baseA = Color.alpha(baseColor);
            int baseR = Color.red(baseColor);
            int baseG = Color.green(baseColor);
            int baseB = Color.blue(baseColor);

            float x = (float) (velocity / (Math.PI / 2));
            x = Math.max(0f, Math.min(x, 1f));
            float alpha = x * x * (3f - 2f * x); // smoothstep: f(t) = 3t^2-3t^3
            int a = (int) (peakA * alpha + baseA * (1 - alpha));
            int r = (int) (peakR * alpha + baseR * (1 - alpha));
            int g = (int) (peakG * alpha + baseG * (1 - alpha));
            int b = (int) (peakB * alpha + baseB * (1 - alpha));
            int color = Color.argb(a, r, g, b);

            view.setBackgroundColor(color);
        }

        public void addAll(Collection<File> files) {
            List<FileItem> oldItems = new LinkedList<>(fileItems);
            for (File file : files) {
                if (!file.getName().startsWith(".") && fileItems.stream().noneMatch(item -> item.file.equals(file))) {
                    fileItems.add(new FileItem(file));
                }
            }
            fileItems.sort((a, b) -> fileComparator.compare(a.file, b.file));
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return fileItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).file.equals(fileItems.get(newItemPosition).file);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }).dispatchUpdatesTo(this);
        }

        public void removeAll(Collection<File> files) {
            List<FileItem> oldItems = new LinkedList<>(fileItems);
            fileItems.removeIf(item -> files.contains(item.file));
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return fileItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).file.equals(fileItems.get(newItemPosition).file);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return true;
                }
            }).dispatchUpdatesTo(this);
        }

        public void invalidate(File directory) {
            fileItems.clear();
            fileItems.addAll(getFileItems(directory));
            notifyDataSetChanged();
        }

        private List<FileItem> getFileItems(File directory) {
            if (directory == null) {
                return new LinkedList<>();
            }

            File[] files = directory.listFiles();
            if (files == null) {
                // possible not directory or no privilege
                return new LinkedList<>();
            }

            return Arrays.stream(files)
                    .filter(f -> !f.getName().startsWith("."))
                    .sorted(fileComparator)
                    .map(FileItem::new)
                    .collect(Collectors.toList());
        }

        @NonNull
        @Override
        public FileItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            View view = LayoutInflater.from(context).inflate(R.layout.file_item, parent, false);
            return new FileItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileItemViewHolder viewHolder, int position) {
            FileItem item = fileItems.get(position);
            File file = item.getFile();

            viewHolder.fileNameTextView.setText(file.getName());
            if (file.isDirectory()) {
                viewHolder.fileIconImageView.setImageResource(R.drawable.i_directory_24);
                viewHolder.fileDetailsTextView.setText(getDirectoryDetailsString(item.getNumOrdinaryItems(), item.getNumHiddenItems()));
            } else {
                viewHolder.fileIconImageView.setImageResource(R.drawable.i_file_24);
                String sizeText = getSizeString(item.getSize());
                viewHolder.fileDetailsTextView.setText(sizeText);
            }

            if (selectionBar.isSelected(file)) {
                viewHolder.fileSelectedImageView.setVisibility(View.VISIBLE);
            } else {
                viewHolder.fileSelectedImageView.setVisibility(View.GONE);
            }

            applyHighlightEffect(viewHolder.fileHighlightView, progressor.getVelocity(file));

            viewHolder.itemView.setOnClickListener(v -> {
                if (selectionBar.hasSelection()) {
                    toggleSelected(file);
                    return;
                }
                if (file.isDirectory()) {
                    navigateToDirectory(file);
                } else if (file.isFile()) {
                    openFile(file);
                } else {
                    Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
                }
            });

            viewHolder.itemView.setOnLongClickListener(v -> {
                toggleSelected(file);
                return true;
            });

            Util.forwardViewActionsTo(viewHolder.fileNameTextView, viewHolder.itemView);
        }

        private void toggleSelected(File file) {
            selectionBar.toggleSelected(file);
            selectionBar.invalidate();
            invalidateItem(file);
        }

        private void invalidateItem(File item) {
            for (int i = 0; i < fileItems.size(); i++) {
                if (fileItems.get(i).getFile().equals(item)) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }

        public void invalidateItems(Iterable<File> items) {
            for (File item : items) {
                invalidateItem(item);
            }
        }

        private String getDirectoryDetailsString(int numOrdinary, int numHidden) {
            if (numOrdinary < 0 || numHidden < 0) {
                return "Error";
            }

            String ordinaryItemsString;
            {
                if (numOrdinary == 0) {
                    ordinaryItemsString = null;
                } else if (numOrdinary == 1) {
                    ordinaryItemsString = numOrdinary + " item";
                } else {
                    ordinaryItemsString = numOrdinary + " items";
                }
            }

            String hiddenItemsString = numHidden == 0 ? null : numHidden + " hidden";

            if (ordinaryItemsString == null) {
                return hiddenItemsString == null ? "Empty" : hiddenItemsString;
            } else {
                return hiddenItemsString == null ? ordinaryItemsString : ordinaryItemsString + " + " + hiddenItemsString;
            }
        }

        @Override
        public int getItemCount() {
            return fileItems.size();
        }

        class FileItemViewHolder extends RecyclerView.ViewHolder {

            private final View fileHighlightView;
            private final ImageView fileIconImageView;
            private final ImageView fileSelectedImageView;
            private final TextView fileNameTextView;
            private final TextView fileDetailsTextView;

            public FileItemViewHolder(@NonNull View itemView) {
                super(itemView);
                fileHighlightView = itemView.findViewById(R.id.file_highlight);
                fileIconImageView = itemView.findViewById(R.id.file_icon);
                fileSelectedImageView = itemView.findViewById(R.id.file_selected_icon);
                fileNameTextView = itemView.findViewById(R.id.file_name);
                fileDetailsTextView = itemView.findViewById(R.id.file_details);
            }
        }

        class FileItem {

            private final File file;

            private final long size;

            private final int numOrdinaryItems;

            private final int numHiddenItems;

            public FileItem(File file) {
                this.file = file;
                this.size = file.length();

                int ordinarys = 0;
                int hiddens = 0;
                if (file.isDirectory()) {
                    File[] subitems = file.listFiles();
                    if (subitems != null) {
                        for (File subitem : subitems) {
                            if (subitem.getName().startsWith(".")) {
                                hiddens++;
                            } else {
                                ordinarys++;
                            }
                        }
                    }
                }
                this.numOrdinaryItems = ordinarys;
                this.numHiddenItems = hiddens;
            }

            public File getFile() {
                return file;
            }

            public long getSize() {
                return size;
            }

            public int getNumOrdinaryItems() {
                return numOrdinaryItems;
            }

            public int getNumHiddenItems() {
                return numHiddenItems;
            }
        }
    }
}
