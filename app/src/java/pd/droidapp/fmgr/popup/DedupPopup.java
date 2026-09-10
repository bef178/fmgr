package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.DedupWorker.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.getSizeString;

public class DedupPopup extends ProcessingPopup {

    private final File startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final FileGroupsAdapter itemsAdapter;

    // callbacks
    private Consumer<File> onJump;
    private Consumer<Collection<File>> onCopy;
    private Consumer<Collection<File>> onCut;
    private PopupOnDismissedListener onPopupDismissed;

    private DedupWorker worker;
    private final Collection<File> removedFiles = new LinkedList<>();

    private final Map<String, List<FileProperties>> byChecksum = new LinkedHashMap<>();
    private final Map<String, FileProperties> byPath = new HashMap<>();
    private int totalScanned;

    public DedupPopup(View containerView, File startDirectory) {
        super(containerView, R.layout.dedup_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new FileGroupsAdapter(startDirectory, selectionBar.selectedItems);

        titleBar.setTitle(R.string.delete_duplicate_files);

        initSelectionBar();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.abort, this::isProcessing, () -> isProcessing() && !worker.isCancelled(), v -> {
            if (worker != null) {
                worker.cancel();
            }
            updateButtons();
        });
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isRunning(), () -> true, v -> selfWindow.dismiss());
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
                selectionBar.selectedItems.removeAll(removed);
                for (File file : removed) {
                    FileProperties props = byPath.remove(file.getPath());
                    if (props == null) {
                        continue;
                    }
                    List<FileProperties> group = byChecksum.get(props.sha256sum);
                    if (group != null) {
                        group.remove(props);
                    }
                }
                refreshGroups();
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
        return worker != null && worker.isRunning();
    }

    @Override
    protected void stopProcessing(Runnable onStopped) {
        if (worker == null || !worker.isRunning()) {
            onStopped.run();
            return;
        }
        worker.whenStopped(onStopped);
        worker.cancel();
    }

    @Override
    protected void onDismissed() {
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
        worker = new DedupWorker();
        totalScanned = 0;
        byChecksum.clear();
        byPath.clear();

        worker.whenStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        worker.whenUpdated((scanned, completed) -> containerView.post(() -> {
            totalScanned += scanned;
            for (FileProperties props : completed) {
                List<FileProperties> group = byChecksum.computeIfAbsent(props.sha256sum, k -> new LinkedList<>());
                group.add(props);
                byPath.put(props.path, props);
                if (group.size() == 2) {
                    // keep the new visible group appended not inserted
                    byChecksum.remove(props.sha256sum);
                    byChecksum.put(props.sha256sum, group);
                }
            }
            refreshGroups();
        }));
        worker.whenStopped(() -> containerView.post(() -> {
            updateButtons();
            if (worker.isCompleted()) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
        }));
        worker.start(startDirectory.getPath());
        updateButtons();
    }

    // derive the group totals from byChecksum, then refresh list and status
    private void refreshGroups() {
        int totalGroups = 0;
        int totalGroupItems = 0;
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                totalGroups++;
                totalGroupItems += group.size();
            }
        }
        itemsAdapter.load(buildFileGroups());
        selectionBar.invalidate();
        statusBar.setText(context.getString(R.string.x_scanned_y_found_groups,
                totalScanned, totalGroups, totalGroupItems));
    }

    private List<FileGroup> buildFileGroups() {
        List<FileGroup> newFileGroups = new LinkedList<>();
        for (List<FileProperties> group : byChecksum.values()) {
            if (group.size() > 1) {
                FileProperties first = group.get(0);
                newFileGroups.add(new FileGroup(
                        first.size,
                        first.sha256sum,
                        group.stream().map(props -> new File(props.path)).collect(Collectors.toList())));
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
            List<File> unselected = new LinkedList<>();
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
        final String sha256sum;
        private final List<File> files;

        FileGroup(long size, String sha256sum, List<File> files) {
            this.size = size;
            this.sha256sum = sha256sum;
            this.files = new LinkedList<>(files);
        }

        public List<File> getFiles() {
            return files;
        }

        public String key() {
            return sha256sum;
        }
    }

    private static class FileGroupsAdapter extends RecyclerView.Adapter<FileGroupsAdapter.FileGroupViewHolder> {

        private final File startDirectory;
        private final Set<File> selectedFiles;
        private final List<FileGroup> fileGroups = new ArrayList<>();
        private final Map<String, Boolean> collapsedStates = new HashMap<>();
        private int[] startIndexes = new int[0];
        private Runnable onItemFileToggled;

        FileGroupsAdapter(File startDirectory, Set<File> selectedFiles) {
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void whenItemFileToggled(Runnable onItemFileToggled) {
            this.onItemFileToggled = onItemFileToggled;
        }

        void load(List<FileGroup> newGroups) {
            List<FileGroup> oldGroups = new ArrayList<>(fileGroups);
            int[] oldStartIndexes = startIndexes;
            fileGroups.clear();
            fileGroups.addAll(newGroups);
            startIndexes = calculateStartIndexes(fileGroups);
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
                    return oldStartIndexes[oldPos] == startIndexes[newPos]
                            && oldGroups.get(oldPos).getFiles().size() == fileGroups.get(newPos).getFiles().size();
                }

                @Nullable
                @Override
                public Object getChangePayload(int oldPos, int newPos) {
                    return Boolean.TRUE;
                }
            }).dispatchUpdatesTo(this);
        }

        private static int[] calculateStartIndexes(List<FileGroup> groups) {
            int[] indexes = new int[groups.size()];
            int index = 1;
            for (int i = 0; i < groups.size(); i++) {
                indexes[i] = index;
                index += groups.get(i).getFiles().size();
            }
            return indexes;
        }

        List<FileGroup> getFileGroups() {
            return fileGroups;
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
            viewHolder.titleTextView.setText(context.getString(R.string.x_files_y_each, files.size(), getSizeString(group.size)));
            boolean collapsed = isCollapsed(group);
            viewHolder.triangleImageView.setRotation(collapsed ? -90f : 0f);
            viewHolder.filesView.setVisibility(collapsed ? View.GONE : View.VISIBLE);

            viewHolder.titleBarView.setOnClickListener(v -> {
                toggleCollapsed(group);
                animateCollapsed(viewHolder.triangleImageView, viewHolder.filesView, isCollapsed(group));
            });

            int nowCount = viewHolder.filesView.getChildCount();
            int requiredCount = files.size();

            int startIndex = startIndexes[position];

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
                fileItem.forwardPathViewClicksTo(fileView);
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
