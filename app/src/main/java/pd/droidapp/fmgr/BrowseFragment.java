package pd.droidapp.fmgr;

import android.annotation.SuppressLint;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.fav.FavItemStore;
import pd.droidapp.fmgr.util.ActionPopup;
import pd.droidapp.fmgr.util.EditPopup;

public class BrowseFragment extends Fragment {

    private ActionBar actionBar;
    private PathBar pathBar;
    private FileItemAdapter fileItemAdapter;

    private FavItemStore favItemStore;

    private File currentDirectory;
    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        favItemStore = new FavItemStore(requireContext());

        actionBar = new ActionBar(view);

        pathBar = new PathBar(view);

        fileItemAdapter = new FileItemAdapter();

        RecyclerView fileListView = view.findViewById(R.id.file_list);
        fileListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileListView.setAdapter(fileItemAdapter);

        doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        pathBar.invalidate();
        fileItemAdapter.invalidate(currentDirectory);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    private void doChangeCurrentDirectory(File directory) {
        currentDirectory = directory;
        pathBar.invalidate();
        actionBar.invalidate();
        fileItemAdapter.invalidate(directory);
    }

    public void navigateToDirectory(File target) {
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

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
        forwardStack.push(currentDirectory);
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

        backStack.push(currentDirectory);
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

    private void toggleFavorite() {
        if (favItemStore.contains(currentDirectory)) {
            favItemStore.remove(currentDirectory);
            Toast.makeText(requireContext(), R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
        } else {
            favItemStore.put(currentDirectory);
            Toast.makeText(requireContext(), R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
        }
        pathBar.favIcon.setSelected(favItemStore.contains(currentDirectory));
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

        File newFile = new File(currentDirectory, name);
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

        fileItemAdapter.invalidate(currentDirectory);
        return true;
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
            upButton.setOnClickListener(v -> navigateToDirectory(getParentDirectory(currentDirectory)));

            ImageButton refreshButton = containerView.findViewById(R.id.action_refresh);
            refreshButton.setOnClickListener(v -> fileItemAdapter.invalidate(currentDirectory));

            ImageButton moreButton = containerView.findViewById(R.id.action_more);
            moreButton.setOnClickListener(v -> {
                ActionPopup actionPopup = new ActionPopup(requireContext(), v);
                actionPopup.setOnNewDirectoryClickListener(BrowseFragment.this::showCreateDirectoryPopup);
                actionPopup.setOnNewFileClickListener(BrowseFragment.this::showCreateFilePopup);
            });
        }

        public void invalidate() {
            boolean hasParentDirectory = getParentDirectory(currentDirectory) != null;
            upButton.setEnabled(hasParentDirectory);
            backButton.setEnabled(!backStack.isEmpty());
            forwardButton.setEnabled(!forwardStack.isEmpty());
        }
    }

    public class PathBar {

        private final LinearLayout breadcrumbLayout;
        private final ImageButton favIcon;

        public PathBar(View containerView) {
            breadcrumbLayout = containerView.findViewById(R.id.breadcrumb_layout);

            favIcon = containerView.findViewById(R.id.fav_icon);
            favIcon.setOnClickListener(v -> toggleFavorite());
        }

        public void invalidate() {
            breadcrumbLayout.removeAllViews();

            favIcon.setSelected(favItemStore.contains(currentDirectory));

            Context context = requireContext();
            LayoutInflater inflater = LayoutInflater.from(context);
            File f = currentDirectory;
            while (f != null) {
                if (f.getName().isEmpty()) {
                    // the root directory deserves a breadcrumb
                    breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
                } else {
                    if (breadcrumbLayout.getChildCount() > 0) {
                        breadcrumbLayout.addView(createSeparatorTextView(context), 0);
                    }
                    breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
                }
                f = f.getParentFile();
            }
        }

        private TextView createBreadcrumbItemView(LayoutInflater inflater, File f) {
            TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb_item, breadcrumbLayout, false);
            textView.setText(f.getName().isEmpty() ? "/" : f.getName());
            textView.setOnClickListener(v -> navigateToDirectory(f));
            return textView;
        }

        private TextView createSeparatorTextView(Context context) {
            TextView textView = new TextView(context);
            textView.setText("/");
            textView.setTextSize(12);
            textView.setTextColor(context.getColor(android.R.color.darker_gray));
            return textView;
        }
    }

    private class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemViewHolder> {

        private final List<FileItem> fileItems = new ArrayList<>();

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
                    .sorted((f1, f2) -> {
                        if (f1.isDirectory() && !f2.isDirectory()) {
                            return -1;
                        } else if (!f1.isDirectory() && f2.isDirectory()) {
                            return 1;
                        } else {
                            return f1.getName().compareToIgnoreCase(f2.getName());
                        }
                    })
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
            viewHolder.fileNameTextView.setText(item.getFile().getName());
            if (item.getFile().isDirectory()) {
                viewHolder.fileIconImageView.setImageResource(R.drawable.i_directory_24);
                viewHolder.fileDetailsTextView.setText(getDirectoryDetailsString(item.getNumOrdinaryItems(), item.getNumHiddenItems()));
            } else {
                viewHolder.fileIconImageView.setImageResource(R.drawable.i_file_24);
                String sizeText = getFileDetailsString(item.getSize());
                viewHolder.fileDetailsTextView.setText(sizeText);
            }

            viewHolder.itemView.setOnClickListener(v -> {
                File file = item.getFile();
                if (file.isDirectory()) {
                    navigateToDirectory(file);
                } else if (file.isFile()) {
                    openFile(file);
                } else {
                    Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
                }
            });
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
            private final TextView fileNameTextView;
            private final TextView fileDetailsTextView;

            public FileItemViewHolder(@NonNull View itemView) {
                super(itemView);
                fileIconImageView = itemView.findViewById(R.id.file_icon);
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
