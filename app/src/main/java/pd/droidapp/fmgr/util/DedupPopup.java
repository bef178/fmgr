package pd.droidapp.fmgr.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.util.PathExtension;

import static pd.droidapp.fmgr.util.Util.getSizeString;

public class DedupPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;

    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private Consumer<File> onJump;
    private Consumer<Collection<File>> onCopy;
    private Consumer<Collection<File>> onCut;
    private BiConsumer<Collection<File>, Boolean> onDelete;
    private final DedupFileGroupAdapter dedupFileGroupAdapter;
    private final PopupWindow popupWindow;

    // the single source of truth
    private final FileGrouper fileGrouper = new FileGrouper();
    private int totalScanned;

    private FileScanUpdater fileScanUpdater;

    public DedupPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.dedup_popup,
                (ViewGroup) containerView,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);

        PopupTitleBar titleBar = new PopupTitleBar(popupView.findViewById(R.id.popup_title_bar));
        titleBar.setTitle(R.string.delete_duplicate_files);
        titleBar.whenCloseButtonClicked(v -> dismiss());

        RecyclerView groupsListView = popupView.findViewById(R.id.files_list);

        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(popupView.findViewById(R.id.selection_bar));

        dedupFileGroupAdapter = new DedupFileGroupAdapter(startDirectory, selectionBar.selectedFiles);
        dedupFileGroupAdapter.whenItemFileToggled(selectionBar::invalidate);

        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.selectedFiles.size() == 1) {
                File file = selectionBar.selectedFiles.iterator().next();
                if (onJump != null) {
                    onJump.accept(file);
                }
                dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> {
            if (onCopy != null) {
                onCopy.accept(selectionBar.copySelectedFiles());
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> {
            if (onCut != null) {
                onCut.accept(selectionBar.copySelectedFiles());
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                List<File> selected = selectionBar.copySelectedFiles();
                onDelete.accept(selected, false);
                fileGrouper.removeAll(selected);
                dedupFileGroupAdapter.setFileGroups(getFileGroups());
            }
            selectionBar.clear();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_smart_select, c -> c > 0, v -> {
            List<File> newlySelected = suggestToSelect(selectionBar.selectedFiles);
            selectionBar.selectedFiles.addAll(newlySelected);
            selectionBar.invalidate();
            dedupFileGroupAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.invalidate();
            dedupFileGroupAdapter.notifyDataSetChanged();
        });

        groupsListView.setLayoutManager(new LinearLayoutManager(context));
        groupsListView.setAdapter(dedupFileGroupAdapter);

        popupView.setOnClickListener(v -> dismiss());
        popupArea.setOnClickListener(v -> {});

        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
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

    public void whenDeleteClicked(BiConsumer<Collection<File>, Boolean> onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        containerView.post(() -> {
            popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            doScan();
        });
    }

    private void doScan() {
        fileScanUpdater = new FileScanUpdater();
        fileScanUpdater.whenFileReached(file -> {
            if (file.length() != 0) {
                fileGrouper.add(file);
            }
            return false;
        });
        fileScanUpdater.whenScanStarted(() -> containerView.post(() -> {
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.scanning));
            selectionBar.invalidate();
        }));
        fileScanUpdater.whenScanUpdated((delta, scanned) -> containerView.post(() -> {
            totalScanned += scanned;
            dedupFileGroupAdapter.setFileGroups(getFileGroups());
            selectionBar.invalidate();
            int totalFiles = dedupFileGroupAdapter.getFileGroups().stream()
                    .mapToInt(g -> g.getFiles().size()).sum();
            statusBar.setText(context.getString(R.string.x_scanned_y_found_groups,
                    totalScanned, totalFiles, dedupFileGroupAdapter.getGroupCount()));
        }));
        fileScanUpdater.whenScanStopped(() -> containerView.post(() -> {
            if (fileScanUpdater.isCompleted()) {
                statusBar.markDone();
            }
        }));
        fileScanUpdater.start(startDirectory);
    }

    private List<FileGroup> getFileGroups() {
        List<FileGroup> fileGroups = new ArrayList<>();
        for (List<FileGrouper.FileProperties> group : fileGrouper.getDupGroups()) {
            List<File> a = new ArrayList<>();
            for (FileGrouper.FileProperties props : group) {
                if (props.file.exists()) {
                    a.add(props.file);
                }
            }
            if (a.size() > 1) {
                fileGroups.add(new FileGroup(group.get(0).size, group.get(0).md5sum, a));
            }
        }
        return fileGroups;
    }

    /**
     * Returns files to newly select, leaving already-selected ones untouched.
     * Keeps at most one file per group unselected.
     */
    private List<File> suggestToSelect(Set<File> alreadySelectedFiles) {
        List<File> newlySelectedFiles = new LinkedList<>();
        for (FileGroup group : dedupFileGroupAdapter.getFileGroups()) {
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

    public void dismiss() {
        if (fileScanUpdater != null) {
            fileScanUpdater.cancel();
        }
        popupWindow.dismiss();
    }

    static class FileGroup {

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

    static class DedupFileGroupAdapter extends RecyclerView.Adapter<DedupFileGroupAdapter.FileGroupViewHolder> {

        private final File startDirectory;
        private final Set<File> selectedFiles;
        private final List<FileGroup> fileGroups = new LinkedList<>();
        private final Map<String, Boolean> collapsedStates = new HashMap<>();
        private Runnable onItemFileToggled;

        DedupFileGroupAdapter(File startDirectory, Set<File> selectedFiles) {
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void whenItemFileToggled(Runnable onItemFileToggled) {
            this.onItemFileToggled = onItemFileToggled;
        }

        void setFileGroups(List<FileGroup> newGroups) {
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
                boolean nowCollapsed = isCollapsed(group);
                viewHolder.filesView.setVisibility(nowCollapsed ? View.GONE : View.VISIBLE);

                float targetRotation = nowCollapsed ? -90f : 0f;
                float currentRotation = viewHolder.triangleImageView.getRotation();
                ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
                animator.setDuration(200);
                animator.addUpdateListener(animation -> {
                    float rotation = (float) animation.getAnimatedValue();
                    viewHolder.triangleImageView.setRotation(rotation);
                });
                animator.start();
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
                fileItem.setPath(PathExtension.relativize(startDirectory.getPath(), file.getPath()));
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
