package pd.droidapp.fmgr;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
import pd.droidapp.fmgr.util.EditPopup;
import pd.droidapp.fmgr.util.PathBar;

public class BrowseFragment extends Fragment {

    private final Clipboard clipboard = new Clipboard();
    private ActionBar actionBar;
    private PathBar pathBar;
    private SelectionBar selectionBar;
    private FileItemAdapter fileItemAdapter;

    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        actionBar = new ActionBar(view);

        pathBar = new PathBar(requireContext(), view.findViewById(R.id.path_bar));
        pathBar.whenBreadcrumbClicked(this::navigateToDirectory);

        selectionBar = new SelectionBar(view.findViewById(R.id.selection_bar));

        fileItemAdapter = new FileItemAdapter();

        RecyclerView fileListView = view.findViewById(R.id.file_list);
        fileListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileListView.setAdapter(fileItemAdapter);

        doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        selectionBar.clearSelected();
    }

    @Override
    public void onResume() {
        super.onResume();
        pathBar.invalidate();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    private void doChangeCurrentDirectory(File directory) {
        pathBar.invalidate(directory);
        actionBar.invalidate();
        selectionBar.clearSelected();
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

    void showCreateDirectoryPopup() {
        EditPopup editPopup = new EditPopup(requireContext(), getView());
        editPopup.show(
                getString(R.string.new_directory),
                "",
                getString(R.string.directory_name),
                name -> {
                    if (createItem(name, true)) {
                        editPopup.dismiss();
                    }
                });
    }

    private void showCreateFilePopup() {
        EditPopup editPopup = new EditPopup(requireContext(), getView());
        editPopup.show(
                getString(R.string.new_file),
                "",
                getString(R.string.file_name),
                name -> {
                    if (createItem(name, false)) {
                        editPopup.dismiss();
                    }
                });
    }

    private boolean createItem(String name, boolean isDirectory) {
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_empty_name, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (name.contains("/") || name.contains("\\") || name.contains("\0")) {
            Toast.makeText(requireContext(), R.string.error_invalid_name, Toast.LENGTH_SHORT).show();
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

    private void markSelectedItemsForCut() {
        List<File> files = new ArrayList<>(selectionBar.selectedFiles);
        clipboard.setCut(files);
        Toast.makeText(requireContext(), getString(R.string.cut_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clearSelected();
        for (File file : files) {
            int i = fileItemAdapter.indexOf(file);
            if (i >= 0) {
                fileItemAdapter.notifyItemChanged(i);
            }
        }
    }

    private void markSelectedItemsForCopy() {
        List<File> files = new ArrayList<>(selectionBar.selectedFiles);
        clipboard.setCopy(files);
        Toast.makeText(requireContext(), getString(R.string.copied_report_format, files.size()), Toast.LENGTH_SHORT).show();
        actionBar.invalidate();
        selectionBar.clearSelected();
        for (File file : files) {
            int i = fileItemAdapter.indexOf(file);
            if (i >= 0) {
                fileItemAdapter.notifyItemChanged(i);
            }
        }
    }

    private void pasteItemsFromCut() {
        File dstDirectory = pathBar.getCurrentDirectory();

        int okCount = 0;
        int failedCount = 0;

        for (File src : clipboard.getCut()) {
            File dst = new File(dstDirectory, src.getName());
            if (src.renameTo(dst)) {
                okCount++;
            } else {
                failedCount++;
            }
        }

        Toast.makeText(requireContext(), getString(R.string.pasted_report_format, okCount, failedCount), Toast.LENGTH_SHORT).show();
        clipboard.clear();
        actionBar.invalidate();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    private void pasteItemsFromCopy() {
        File dstDirectory = pathBar.getCurrentDirectory();

        int okCount = 0;
        int failedCount = 0;

        for (File src : clipboard.getCopy()) {
            File dst = new File(dstDirectory, src.getName());
            if (copyRecursively(src, dst)) {
                okCount++;
            } else {
                failedCount++;
            }
        }

        Toast.makeText(requireContext(), getString(R.string.pasted_report_format, okCount, failedCount), Toast.LENGTH_SHORT).show();
        clipboard.clear();
        actionBar.invalidate();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    private boolean copyRecursively(File src, File dst) {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                return false;
            }
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    if (!copyRecursively(new File(src, child), new File(dst, child))) {
                        return false;
                    }
                }
            }
        } else {
            try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private void showDeleteDialog() {
        List<File> files = new LinkedList<>(selectionBar.selectedFiles);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.about_to_delete_format, files.size()))
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteItems(files))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteItems(Collection<File> files) {
        Set<File> filesToDelete = new HashSet<>(files);
        int okCount = 0;
        int failedCount = 0;

        for (File file : filesToDelete) {
            if (deleteRecursively(file)) {
                okCount++;
            } else {
                failedCount++;
            }
        }

        Toast.makeText(requireContext(), getString(R.string.deleted_report_format, okCount, failedCount), Toast.LENGTH_SHORT).show();
        selectionBar.clearSelected();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private void showRenamePopup(File file) {
        String currentName = file.getName();
        EditPopup editPopup = new EditPopup(requireContext(), getView());
        editPopup.show(
                getString(R.string.rename),
                currentName,
                currentName,
                newName -> {
                    if (newName.isEmpty() || newName.equals(currentName) || renameItem(file, newName)) {
                        editPopup.dismiss();
                        selectionBar.clearSelected();
                    }
                });
    }

    private boolean renameItem(File file, String newName) {
        if (newName.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_empty_name, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (newName.contains("/") || newName.contains("\\") || newName.contains("\0")) {
            Toast.makeText(requireContext(), R.string.error_invalid_name, Toast.LENGTH_SHORT).show();
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
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.about_to_delete_empty))
                .setPositiveButton(R.string.ok, (dialog, which) -> deleteEmptyItems())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteEmptyItems() {
        int numDeleted = deleteEmptyFilesAndDirectories(pathBar.getCurrentDirectory());
        Toast.makeText(requireContext(), getString(R.string.deleted_empty_report_format, numDeleted), Toast.LENGTH_SHORT).show();
        fileItemAdapter.invalidate(pathBar.getCurrentDirectory());
    }

    private int deleteEmptyFilesAndDirectories(File directory) {
        int n = 0;
        Stack<File> stack = new Stack<>();
        Set<File> visited = new HashSet<>();
        {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() || f.isFile()) {
                        stack.push(f);
                    }
                }
            }
        }

        while (!stack.isEmpty()) {
            File f = stack.pop();
            if (f.isFile()) {
                if (f.length() == 0 && f.delete()) {
                    n++;
                }
            } else if (f.isDirectory()) {
                if (visited.add(f)) {
                    // first visit
                    stack.push(f);
                    File[] files = f.listFiles();
                    if (files != null) {
                        for (File f1 : files) {
                            if (f1.isDirectory() || f1.isFile()) {
                                stack.push(f1);
                            }
                        }
                    }
                } else {
                    // second visit
                    File[] files = f.listFiles();
                    if (files != null && files.length == 0 && f.delete()) {
                        n++;
                    }
                }
            }
        }
        return n;
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
                actionPopup.setOnNewDirectoryClickedListener(BrowseFragment.this::showCreateDirectoryPopup);
                actionPopup.setOnNewFileClickedListener(BrowseFragment.this::showCreateFilePopup);
                actionPopup.whenDeleteEmptyClicked(BrowseFragment.this::showDeleteEmptyDialog);
                actionPopup.whenDedupFilesClicked(() -> {
                    DedupPopup popup = new DedupPopup(requireContext(), getView(), pathBar.getCurrentDirectory());
                    popup.whenConfirmClicked(BrowseFragment.this::deleteItems);
                    popup.show();
                });
                if (clipboard.isCut()) {
                    actionPopup.setPasteFromCutButtonVisible(true);
                    actionPopup.setOnPasteFromCutClickedListener(BrowseFragment.this::pasteItemsFromCut);
                } else if (clipboard.isCopy()) {
                    actionPopup.setPasteFromCopyButtonVisible(true);
                    actionPopup.setOnPasteFromCopyClickedListener(BrowseFragment.this::pasteItemsFromCopy);
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

    public class SelectionBar {

        private final Set<File> selectedFiles = new HashSet<>();

        private final View selectionBarLayout;
        private final TextView numSelectedTextView;
        private final ImageButton deleteButton;
        private final ImageButton renameButton;
        private final ImageButton cutButton;
        private final ImageButton copyButton;

        public SelectionBar(View selectionBarLayout) {
            this.selectionBarLayout = selectionBarLayout;
            numSelectedTextView = selectionBarLayout.findViewById(R.id.num_selected);
            cutButton = selectionBarLayout.findViewById(R.id.action_cut);
            copyButton = selectionBarLayout.findViewById(R.id.action_copy);
            deleteButton = selectionBarLayout.findViewById(R.id.action_delete);
            renameButton = selectionBarLayout.findViewById(R.id.action_rename);
            ImageButton selectAllButton = selectionBarLayout.findViewById(R.id.select_all_icon);
            ImageButton clearSelectionButton = selectionBarLayout.findViewById(R.id.select_close_icon);

            selectAllButton.setOnClickListener(v -> {
                selectedFiles.clear();
                selectedFiles.addAll(fileItemAdapter.getFiles());
                fileItemAdapter.notifyDataSetChanged();
                invalidate();
            });
            clearSelectionButton.setOnClickListener(v -> {
                selectedFiles.clear();
                fileItemAdapter.notifyDataSetChanged();
                invalidate();
            });

            deleteButton.setOnClickListener(v -> showDeleteDialog());

            renameButton.setOnClickListener(v -> {
                if (selectedFiles.size() == 1) {
                    File file = selectedFiles.iterator().next();
                    showRenamePopup(file);
                }
            });

            cutButton.setOnClickListener(v -> markSelectedItemsForCut());

            copyButton.setOnClickListener(v -> markSelectedItemsForCopy());
        }

        public void invalidate() {
            if (selectedFiles.isEmpty()) {
                selectionBarLayout.setVisibility(View.GONE);
            } else {
                numSelectedTextView.setText(getString(R.string.num_selected_format, selectedFiles.size()));
                selectionBarLayout.setVisibility(View.VISIBLE);
                renameButton.setVisibility(selectedFiles.size() == 1 ? View.VISIBLE : View.GONE);
                deleteButton.setVisibility(View.VISIBLE);
                cutButton.setVisibility(View.VISIBLE);
                copyButton.setVisibility(View.VISIBLE);
            }
        }

        boolean isSelected(File file) {
            return selectedFiles.contains(file);
        }

        boolean hasSelection() {
            return !selectedFiles.isEmpty();
        }

        void toggleSelected(File file) {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
            } else {
                selectedFiles.add(file);
            }
            invalidate();
        }

        void clearSelected() {
            selectedFiles.clear();
            invalidate();
        }
    }

    private class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemViewHolder> {

        Comparator<File> fileComparator = (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            } else {
                return f1.getName().compareTo(f2.getName());
            }
        };

        private final List<FileItem> fileItems = new ArrayList<>();

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
