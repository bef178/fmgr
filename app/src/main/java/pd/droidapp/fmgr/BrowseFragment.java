package pd.droidapp.fmgr;

import android.annotation.SuppressLint;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.util.ActionPopup;
import pd.droidapp.fmgr.util.Clipboard;
import pd.droidapp.fmgr.util.DedupPopup;
import pd.droidapp.fmgr.util.DeleteEmptyPopup;
import pd.droidapp.fmgr.util.EditPopup;
import pd.droidapp.fmgr.util.PastePopup;
import pd.droidapp.fmgr.util.PathBar;
import pd.droidapp.fmgr.util.Progressor;
import pd.droidapp.fmgr.util.SearchPopup;
import pd.droidapp.fmgr.util.SelectionBar;

import static pd.util.FileExtension.removeRecursively;

public class BrowseFragment extends Fragment {

    private final Clipboard clipboard = new Clipboard();
    private ActionBar actionBar;
    private PathBar pathBar;
    private SelectionBar selectionBar;
    private RecyclerView filesView;
    private FileItemAdapter fileItemAdapter;

    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    private boolean askedAllFilesAccess;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        actionBar = new ActionBar(view);

        pathBar = new PathBar(view.findViewById(R.id.path_bar));
        pathBar.whenBreadcrumbClicked(this::navigateToDirectory);

        selectionBar = new SelectionBar(view.findViewById(R.id.selection_bar));

        fileItemAdapter = new FileItemAdapter();

        filesView = view.findViewById(R.id.file_list);
        filesView.setLayoutManager(new LinearLayoutManager(requireContext()));
        filesView.setAdapter(fileItemAdapter);

        selectionBar.addButton(R.layout.selection_button_rename, c -> c == 1, v -> {
            if (selectionBar.selectedFiles.size() == 1) {
                showRenamePopup(selectionBar.selectedFiles.iterator().next());
            }
        });
        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> markSelectedItemsForCopy());
        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> markSelectedItemsForCut());
        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> showDeleteDialog());

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addAll(fileItemAdapter.getFiles());
            selectionBar.invalidate();
            fileItemAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            fileItemAdapter.notifyDataSetChanged();
        });

        doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        selectionBar.clear();
        selectionBar.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        pathBar.invalidate();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
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
                    } catch (ActivityNotFoundException e) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivity(intent);
                        } catch (ActivityNotFoundException ignored) {
                            Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    private void doChangeCurrentDirectory(File directory) {
        pathBar.invalidate(directory);
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        fileItemAdapter.invalidate(directory);
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

    private void navigateBack() {
        if (backStack.isEmpty()) {
            return;
        }

        File target = backStack.peek();
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        backStack.pop();
        forwardStack.push(pathBar.getCurrentDirectory());
        doChangeCurrentDirectory(target);
    }

    private void navigateForward() {
        if (forwardStack.isEmpty()) {
            return;
        }

        File target = forwardStack.peek();
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        backStack.push(pathBar.getCurrentDirectory());
        forwardStack.pop();
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
            if (parent != null && parent.exists()) {
                return parent;
            }
        }
        return null;
    }

    private void showSearchPopup() {
        SearchPopup searchPopup = new SearchPopup(getView(), pathBar.getCurrentDirectory());
        searchPopup.whenSearchResultFileClicked(this::jumpToFile);
        searchPopup.whenDeleteClicked(files -> deleteItems(files, false));
        searchPopup.show();
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
        filesView.post(() -> {
            int position = fileItemAdapter.indexOf(file);
            if (position >= 0) {
                filesView.scrollToPosition(position);
                filesView.postDelayed(() -> fileItemAdapter.highlightFile(file), 400);
            }
        });
    }

    void showCreateDirectoryPopup() {
        EditPopup editPopup = new EditPopup(getView());
        editPopup.show(
                getString(R.string.new_directory),
                "",
                getString(R.string.directory_name),
                name -> {
                    if (createItem(name.trim(), true)) {
                        editPopup.dismiss();
                    }
                });
    }

    private void showCreateFilePopup() {
        EditPopup editPopup = new EditPopup(getView());
        editPopup.show(
                getString(R.string.new_file),
                "",
                getString(R.string.file_name),
                name -> {
                    if (createItem(name.trim(), false)) {
                        editPopup.dismiss();
                    }
                });
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

        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
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
        List<File> files = selectionBar.copySelectedFiles();
        clipboard.setCut(files);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        for (File file : files) {
            int i = fileItemAdapter.indexOf(file);
            if (i >= 0) {
                fileItemAdapter.notifyItemChanged(i);
            }
        }
    }

    private void markSelectedItemsForCopy() {
        List<File> files = selectionBar.copySelectedFiles();
        clipboard.setCopy(files);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clear();
        selectionBar.invalidate();
        for (File file : files) {
            int i = fileItemAdapter.indexOf(file);
            if (i >= 0) {
                fileItemAdapter.notifyItemChanged(i);
            }
        }
    }

    private void showPastePopup() {
        String op;
        List<File> srcFiles;
        if (clipboard.isCut()) {
            op = "move";
            srcFiles = clipboard.getCut();
        } else if (clipboard.isCopy()) {
            op = "copy";
            srcFiles = clipboard.getCopy();
        } else {
            return;
        }

        PastePopup pastePopup = new PastePopup(getView(), op, srcFiles, pathBar.getCurrentDirectory());
        pastePopup.whenDismissed(everExecuted -> {
            if (everExecuted) {
                clipboard.clear();
                actionBar.invalidate();
                fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
            }
        });
        pastePopup.show();
    }

    private void showDeleteDialog() {
        List<File> files = selectionBar.copySelectedFiles();
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.about_to_delete_format, files.size()))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    deleteItems(files, false);
                    selectionBar.clear();
                    selectionBar.invalidate();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteItems(Collection<File> files, boolean prune) {
        Set<File> filesToDelete = new HashSet<>(files);
        int okCount = 0;
        int failedCount = 0;
        Deque<File> pruneCandidates = new ArrayDeque<>();

        for (File file : filesToDelete) {
            if (removeRecursively(file, null)) {
                okCount++;
                if (prune) {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        pruneCandidates.add(parent);
                    }
                }
            } else {
                failedCount++;
            }
        }

        File root = pathBar.getCurrentDirectory();
        while (!pruneCandidates.isEmpty()) {
            File dir = pruneCandidates.poll();
            // never delete the browsed root; only prune directories that became empty
            if (dir == null || dir.equals(root) || !isEmpty(dir)) {
                continue;
            }
            if (dir.delete()) {
                okCount++;
                File parent = dir.getParentFile();
                if (parent != null && !parent.equals(root)) {
                    pruneCandidates.add(parent);
                }
            } else {
                failedCount++;
            }
        }

        Toast.makeText(requireContext(), getString(R.string.deleted_report_format, okCount, failedCount), Toast.LENGTH_SHORT).show();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    private void showRenamePopup(File file) {
        String currentName = file.getName();
        EditPopup editPopup = new EditPopup(getView());
        editPopup.show(
                getString(R.string.rename),
                currentName,
                currentName,
                newName -> {
                    newName = newName.trim();
                    if (newName.isEmpty() || newName.equals(currentName) || renameItem(file, newName)) {
                        editPopup.dismiss();
                        selectionBar.clear();
                        selectionBar.invalidate();
                    }
                });
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

        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
        return true;
    }

    private void showDeleteEmptyDialog() {
        DeleteEmptyPopup popup = new DeleteEmptyPopup(getView(), pathBar.getCurrentDirectory());
        popup.whenDeleteClicked(this::deleteItems);
        popup.show();
    }

    private static boolean isEmpty(File file) {
        if (file.isFile()) {
            return file.length() == 0;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            return children == null || children.length == 0;
        }
        return false;
    }

    public class ActionBar {

        private final ImageButton backButton;
        private final ImageButton forwardButton;
        private final ImageButton upButton;

        public ActionBar(View containerView) {
            ImageButton homeButton = containerView.findViewById(R.id.action_home);
            homeButton.setOnClickListener(v -> navigateToHome());

            backButton = containerView.findViewById(R.id.action_back);
            backButton.setOnClickListener(v -> navigateBack());

            forwardButton = containerView.findViewById(R.id.action_forward);
            forwardButton.setOnClickListener(v -> navigateForward());

            upButton = containerView.findViewById(R.id.action_up);
            upButton.setOnClickListener(v -> navigateToDirectory(getParentDirectory(pathBar.getCurrentDirectory())));

            ImageButton refreshButton = containerView.findViewById(R.id.action_refresh);
            refreshButton.setOnClickListener(v -> doChangeCurrentDirectory(pathBar.getCurrentDirectory()));

            ImageButton moreButton = containerView.findViewById(R.id.action_more);
            moreButton.setOnClickListener(v -> {
                ActionPopup actionPopup = new ActionPopup(requireContext(), v);
                actionPopup.whenSearchClicked(BrowseFragment.this::showSearchPopup);
                actionPopup.setOnNewDirectoryClickedListener(BrowseFragment.this::showCreateDirectoryPopup);
                actionPopup.setOnNewFileClickedListener(BrowseFragment.this::showCreateFilePopup);
                actionPopup.whenDeleteEmptyClicked(BrowseFragment.this::showDeleteEmptyDialog);
                actionPopup.whenDedupFilesClicked(() -> {
                    DedupPopup popup = new DedupPopup(getView(), pathBar.getCurrentDirectory());
                    popup.whenDeleteClicked(BrowseFragment.this::deleteItems);
                    popup.show();
                });
                if (clipboard.isCut()) {
                    actionPopup.setPasteFromCutButtonVisible(true);
                    actionPopup.whenPasteFromCutClicked(BrowseFragment.this::showPastePopup);
                } else if (clipboard.isCopy()) {
                    actionPopup.setPasteFromCopyButtonVisible(true);
                    actionPopup.whenPasteFromCopyClicked(BrowseFragment.this::showPastePopup);
                } else {
                    actionPopup.setPasteFromCutButtonVisible(false);
                    actionPopup.setPasteFromCopyButtonVisible(false);
                }
            });
        }

        public void invalidate() {
            boolean hasParentDirectory = getParentDirectory(pathBar.getCurrentDirectory()) != null;
            upButton.setEnabled(hasParentDirectory);
            backButton.setEnabled(!backStack.isEmpty());
            forwardButton.setEnabled(!forwardStack.isEmpty());
        }
    }

    private class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemViewHolder> {

        private final Comparator<File> fileComparator = (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            } else {
                return f1.getName().compareTo(f2.getName());
            }
        };

        private final List<FileItem> fileItems = new ArrayList<>();
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

        public void highlightFile(final File file) {
            progressor.start(file, 1500, new AccelerateDecelerateInterpolator(), (distance, velocity) -> {
                int position = indexOf(file);
                if (position >= 0) {
                    RecyclerView.ViewHolder viewHolder = filesView.findViewHolderForAdapterPosition(position);
                    if (viewHolder != null) {
                        FileItem item = fileItems.get(position);
                        if (item.getFile().equals(file)) {
                            applyHighlightEffect(viewHolder.itemView, velocity);
                        }
                    }
                }
            });
        }

        private void applyHighlightEffect(View view, Float velocity) {
            final int startColor = getContext().getColor(R.color.purple_200);
            final int endColor = 0;

            if (velocity == null) {
                velocity = 0f;
            }

            int startA = Color.alpha(startColor);
            int startR = Color.red(startColor);
            int startG = Color.green(startColor);
            int startB = Color.blue(startColor);

            int endA = Color.alpha(endColor);
            int endR = Color.red(endColor);
            int endG = Color.green(endColor);
            int endB = Color.blue(endColor);

            float alpha = (float) (velocity / (Math.PI / 2));
            int a = (int) (startA * alpha + endA * (1 - alpha));
            int r = (int) (startR * alpha + endR * (1 - alpha));
            int g = (int) (startG * alpha + endG * (1 - alpha));
            int b = (int) (startB * alpha + endB * (1 - alpha));
            int color = Color.argb(a, r, g, b);

            view.setBackgroundColor(color);
        }

        @SuppressLint("NotifyDataSetChanged")
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
                String sizeText = getFileDetailsString(item.getSize());
                viewHolder.fileDetailsTextView.setText(sizeText);
            }

            if (selectionBar.isSelected(file)) {
                viewHolder.fileSelectedImageView.setVisibility(View.VISIBLE);
            } else {
                viewHolder.fileSelectedImageView.setVisibility(View.GONE);
            }

            applyHighlightEffect(viewHolder.itemView, progressor.getVelocity(file));

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
        }

        private void toggleSelected(File file) {
            selectionBar.toggleSelected(file);
            selectionBar.invalidate();
            for (int i = 0; i < fileItems.size(); i++) {
                if (fileItems.get(i).getFile().equals(file)) {
                    notifyItemChanged(i);
                    break;
                }
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

        private String getFileDetailsString(long size) {
            if (size < 0) {
                return "Error";
            } else if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
            } else {
                return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        @Override
        public int getItemCount() {
            return fileItems.size();
        }

        class FileItemViewHolder extends RecyclerView.ViewHolder {

            private final ImageView fileIconImageView;
            private final ImageView fileSelectedImageView;
            private final TextView fileNameTextView;
            private final TextView fileDetailsTextView;

            public FileItemViewHolder(@NonNull View itemView) {
                super(itemView);
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
