package pd.droidapp.fmgr.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.getSizeString;
import static pd.droidapp.fmgr.util.Util.animateCollapsed;

public class DedupPopup extends ProcessingPopup {

    private final File startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final DedupFileGroupsAdapter itemsAdapter;

    // callbacks
    private Consumer<File> onJump;
    private Consumer<Collection<File>> onCopy;
    private Consumer<Collection<File>> onCut;
    private PopupOnDismissedListener onPopupDismissed;

    // the single source of truth
    private final FileGrouper fileGrouper = new FileGrouper();
    private FileScanUpdater scanner;
    private int totalScanned;
    private final Collection<File> removedFiles = new LinkedList<>();

    public DedupPopup(View containerView, File startDirectory) {
        super(containerView, R.layout.dedup_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new DedupFileGroupsAdapter(startDirectory, selectionBar.selectedItems);

        titleBar.setTitle(R.string.delete_duplicate_files);

        initSelectionBar();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.abort, () -> true, this::isProcessing, v -> {
            if (scanner != null) {
                scanner.cancel();
            }
        });
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.selectedItems.size() == 1) {
                File file = selectionBar.selectedItems.iterator().next();
                if (onJump != null) {
                    onJump.accept(file);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> {
            if (onCopy != null) {
                onCopy.accept(selectionBar.copySelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> {
            if (onCut != null) {
                onCut.accept(selectionBar.copySelectedItems());
            }
            selfWindow.dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, selectionBar.copySelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                removedFiles.addAll(removed);
                fileGrouper.removeAll(removed);
                itemsAdapter.invalidate(buildFileGroups());
                selectionBar.selectedItems.removeAll(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_smart_select, c -> c > 0, v -> {
            List<File> newlySelected = suggestToSelect(selectionBar.selectedItems);
            selectionBar.selectedItems.addAll(newlySelected);
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
        itemsAdapter.whenItemFileToggled(selectionBar::invalidate);
    }

    @Override
    protected boolean isProcessing() {
        return scanner != null && scanner.isRunning();
    }

    @Override
    protected void onDismissed() {
        if (scanner != null) {
            scanner.cancel();
        }
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), removedFiles);
        }
    }

    public void whenJumpClicked(Consumer<File> onJump) {
        this.onJump = onJump;
    }

    public void whenCopyClicked(Consumer<Collection<File>> onCopy) {
        this.onCopy = onCopy;
    }

    public void whenCutClicked(Consumer<Collection<File>> onCut) {
        this.onCut = onCut;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        doScan();
    }

    private void doScan() {
        scanner = new FileScanUpdater();
        scanner.whenReached(path -> !path.endsWith("/") && new File(path).length() != 0);
        scanner.whenScanStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        scanner.whenScanUpdated((scanned, delta) -> containerView.post(() -> {
            totalScanned += scanned;
            for (String path : delta) {
                fileGrouper.add(new File(path));
            }
            itemsAdapter.invalidate(buildFileGroups());
            selectionBar.invalidate();
            int totalFiles = itemsAdapter.getFileGroups().stream()
                    .mapToInt(g -> g.getFiles().size()).sum();
            statusBar.setText(context.getString(R.string.x_scanned_y_found_groups,
                    totalScanned, totalFiles, itemsAdapter.getGroupCount()));
        }));
        scanner.whenScanStopped(() -> containerView.post(() -> {
            updateButtons();
            if (scanner.isCompleted()) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
        }));
        scanner.start(startDirectory.getPath());
        updateButtons();
    }

    private List<FileGroup> buildFileGroups() {
        List<FileGroup> newFileGroups = new ArrayList<>();
        for (List<FileGrouper.FileProperties> group : fileGrouper.getDupGroups()) {
            List<File> a = new ArrayList<>();
            for (FileGrouper.FileProperties props : group) {
                if (props.file.exists()) {
                    a.add(props.file);
                }
            }
            if (a.size() > 1) {
                newFileGroups.add(new FileGroup(group.get(0).size, group.get(0).md5sum, a));
            }
        }
        return newFileGroups;
    }

    /**
     * Returns files to newly select, leaving already-selected ones untouched.
     * Keeps at most one file per group unselected.
     */
    private List<File> suggestToSelect(Set<File> alreadySelectedFiles) {
        List<File> newlySelectedFiles = new LinkedList<>();
        for (FileGroup group : itemsAdapter.getFileGroups()) {
            List<File> files = group.getFiles();
            List<File> unselected = new ArrayList<>();
            for (File f : files) {
                if (!alreadySelectedFiles.contains(f)) {
                    unselected.add(f);
                }
            }
            if (unselected.size() <= 1) {
                // 0: group fully selected; 1: keep it, nothing else to select
                continue;
            }
            File fileToKeep = unselected.get(0);
            for (int i = 1; i < unselected.size(); i++) {
                File f = unselected.get(i);
                if (smartCompare(f, fileToKeep) < 0) {
                    newlySelectedFiles.add(fileToKeep);
                    fileToKeep = f;
                } else {
                    newlySelectedFiles.add(f);
                }
            }
        }
        return newlySelectedFiles;
    }

    private int smartCompare(File f1, File f2) {
        long f1Time = f1.lastModified();
        long f2Time = f2.lastModified();
        if (f1Time != f2Time) {
            return -Long.compare(f1Time, f2Time);
        }

        String f1Basename = f1.getName();
        String f2Basename = f2.getName();
        if (!f1Basename.equals(f2Basename)) {
            return Integer.compare(f1Basename.length(), f2Basename.length());
        }

        return f1.getAbsolutePath().length() - f2.getAbsolutePath().length();
    }

    private static class FileGroup {

        final long size;
        final String md5sum;
        private final List<File> files;

        FileGroup(long size, String md5sum, List<File> files) {
            this.size = size;
            this.md5sum = md5sum;
            this.files = new ArrayList<>(files);
        }

        public List<File> getFiles() {
            return files;
        }

        public String key() {
            return md5sum + "_" + size;
        }
    }

    private static class DedupFileGroupsAdapter extends RecyclerView.Adapter<DedupFileGroupsAdapter.FileGroupViewHolder> {

        private final File startDirectory;
        private final Set<File> selectedFiles;
        private final List<FileGroup> fileGroups = new LinkedList<>();
        private final Map<String, Boolean> collapsedStates = new HashMap<>();
        private Runnable onItemFileToggled;

        DedupFileGroupsAdapter(File startDirectory, Set<File> selectedFiles) {
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void whenItemFileToggled(Runnable onItemFileToggled) {
            this.onItemFileToggled = onItemFileToggled;
        }

        void invalidate(List<FileGroup> newGroups) {
            List<FileGroup> oldGroups = new LinkedList<>(fileGroups);
            fileGroups.clear();
            fileGroups.addAll(newGroups);
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldGroups.size();
                }

                @Override
                public int getNewListSize() {
                    return fileGroups.size();
                }

                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    return oldGroups.get(oldPos).key().equals(fileGroups.get(newPos).key());
                }

                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    return oldGroups.get(oldPos).getFiles().size() == fileGroups.get(newPos).getFiles().size();
                }
            }).dispatchUpdatesTo(this);
        }

        List<FileGroup> getFileGroups() {
            return fileGroups;
        }

        int getGroupCount() {
            return fileGroups.size();
        }

        private boolean isCollapsed(FileGroup group) {
            return collapsedStates.getOrDefault(group.key(), false);
        }

        private void toggleCollapsed(FileGroup group) {
            collapsedStates.put(group.key(), !isCollapsed(group));
        }

        @NonNull
        @Override
        public FileGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View groupView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.dedup_group, parent, false);
            return new FileGroupViewHolder(groupView);
        }

        @Override
        public void onBindViewHolder(@NonNull FileGroupViewHolder viewHolder, int position) {
            FileGroup group = fileGroups.get(position);
            List<File> files = group.getFiles();

            Context context = viewHolder.itemView.getContext();
            viewHolder.titleTextView.setText(context.getString(R.string.x_files_y_each, files.size(), getSizeString(files.get(0).length())));
            boolean collapsed = isCollapsed(group);
            viewHolder.triangleImageView.setRotation(collapsed ? -90f : 0f);
            viewHolder.filesView.setVisibility(collapsed ? View.GONE : View.VISIBLE);

            viewHolder.titleBarView.setOnClickListener(v -> {
                toggleCollapsed(group);
                animateCollapsed(viewHolder.triangleImageView, viewHolder.filesView, isCollapsed(group));
            });

            int nowCount = viewHolder.filesView.getChildCount();
            int requiredCount = files.size();

            int startIndex = 1;
            for (int g = 0; g < position; g++) {
                startIndex += fileGroups.get(g).getFiles().size();
            }

            LayoutInflater layoutInflater = LayoutInflater.from(context);
            for (int i = 0; i < requiredCount; i++) {
                File file = files.get(i);
                View fileView;

                if (i < nowCount) {
                    fileView = viewHolder.filesView.getChildAt(i);
                } else {
                    fileView = layoutInflater.inflate(R.layout.popup_file_item, viewHolder.filesView, false);
                    viewHolder.filesView.addView(fileView);
                }

                PopupFileItem fileItem = new PopupFileItem(fileView);
                fileItem.setIndex(startIndex + i);
                fileItem.setIcon(R.drawable.i_file_24);
                fileItem.setPath(PathOps.singleton.relativize(startDirectory.getPath(), file.getPath()));
                fileItem.setSelected(selectedFiles.contains(file));

                fileView.setOnClickListener(v -> {
                    if (!selectedFiles.isEmpty()) {
                        toggleSelected(file, position);
                    }
                });
                fileView.setOnLongClickListener(v -> {
                    toggleSelected(file, position);
                    return true;
                });
            }
            if (nowCount > requiredCount) {
                viewHolder.filesView.removeViews(requiredCount, nowCount - requiredCount);
            }
        }

        private void toggleSelected(File file, int position) {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
            } else {
                selectedFiles.add(file);
            }
            notifyItemChanged(position);
            if (onItemFileToggled != null) {
                onItemFileToggled.run();
            }
        }

        @Override
        public int getItemCount() {
            return fileGroups.size();
        }

        static class FileGroupViewHolder extends RecyclerView.ViewHolder {

            final View titleBarView;
            final ImageView triangleImageView;
            final TextView titleTextView;
            final LinearLayout filesView;

            FileGroupViewHolder(View view) {
                super(view);
                titleBarView = view.findViewById(R.id.group_title_bar);
                triangleImageView = view.findViewById(R.id.group_triangle);
                titleTextView = view.findViewById(R.id.group_title);
                filesView = view.findViewById(R.id.group_files);
            }
        }
    }
}
