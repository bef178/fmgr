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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getFileMd5;
import static pd.droidapp.fmgr.util.Util.getRelativePath;
import static pd.droidapp.fmgr.util.Util.getSizeString;

public class DedupPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;

    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private BiConsumer<Collection<File>, Boolean> onDelete;
    private final DedupGroupAdapter dedupGroupAdapter;
    private final PopupWindow popupWindow;

    private Scanner scanner;

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
        titleBar.setOnCloseClicked(v -> dismiss());

        RecyclerView groupsListView = popupView.findViewById(R.id.files_list);

        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(popupView.findViewById(R.id.selection_bar));

        dedupGroupAdapter = new DedupGroupAdapter(context, startDirectory, selectionBar.selectedFiles);
        dedupGroupAdapter.whenFileClicked((position, file, isChecked) -> selectionBar.invalidate());

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                onDelete.accept(selectionBar.copySelectedFiles(), false);
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_delete_and_prune, c -> c > 0, v -> {
            if (onDelete != null) {
                onDelete.accept(selectionBar.copySelectedFiles(), true);
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_smart_select, c -> c > 0, v -> {
            List<File> newlySelected = suggestToSelect(selectionBar.selectedFiles);
            selectionBar.selectedFiles.addAll(newlySelected);
            dedupGroupAdapter.notifyDataSetChanged();
            selectionBar.invalidate();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            selectionBar.clear();
            dedupGroupAdapter.notifyDataSetChanged();
            selectionBar.invalidate();
        });

        groupsListView.setLayoutManager(new LinearLayoutManager(context));
        groupsListView.setAdapter(dedupGroupAdapter);

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
        scanner = new Scanner();
        scanner.start(startDirectory);
    }

    void updateDedupGroups() {
        List<FileGroup> groups = scanner.copyFileGroups().stream()
                .filter(group -> group.numFiles() > 1)
                .collect(Collectors.toList());
        dedupGroupAdapter.invalidate(groups);
    }

    /**
     * Returns files to newly select, leaving already-selected ones untouched.
     * Keeps at most one file per group unselected.
     */
    private List<File> suggestToSelect(Set<File> alreadySelectedFiles) {
        List<File> newlySelectedFiles = new LinkedList<>();
        for (FileGroup group : dedupGroupAdapter.fileGroups) {
            List<File> files = group.copyFiles();
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
        if (scanner != null) {
            scanner.cancel();
        }
        popupWindow.dismiss();
    }

    class Scanner extends FileScanner {

        final Map<String, FileGroup> fileGroups = new ConcurrentHashMap<>();
        private final Map<Long, List<File>> reachedFiles = new HashMap<>();

        @Override
        protected void onScanStarted() {
            containerView.post(() -> {
                statusBar.markRunning(context.getString(R.string.scanning));
                selectionBar.invalidate();
            });
        }

        @Override
        protected void onScanUpdated(int numFilesScanned) {
            containerView.post(() -> {
                updateDedupGroups();
                selectionBar.invalidate();
                if (isCompleted()) {
                    statusBar.markDone();
                }
                int totalFiles = 0;
                for (FileGroup group : dedupGroupAdapter.fileGroups) {
                    totalFiles += group.numFiles();
                }
                statusBar.setText(context.getString(R.string.x_scanned_y_found_groups,
                        numFilesScanned, totalFiles, dedupGroupAdapter.fileGroups.size()));
            });
        }

        @Override
        protected void onFile(File file) {
            long size = file.length();
            if (size == 0) {
                return;
            }
            List<File> a = reachedFiles.computeIfAbsent(size, n -> new LinkedList<>());
            if (a.isEmpty()) {
                a.add(file);
            } else if (a.size() == 1) {
                addToFileGroup(a.get(0), size);
                addToFileGroup(file, size);
                a.add(file);
            } else {
                addToFileGroup(file, size);
            }
        }

        private void addToFileGroup(File file, long size) {
            String md5 = getFileMd5(file);
            String key = md5 + "_" + size;
            FileGroup group = fileGroups.get(key);
            if (group == null) {
                fileGroups.put(key, new FileGroup(file, size, md5));
            } else {
                group.addFile(file);
            }
        }

        List<FileGroup> copyFileGroups() {
            return new ArrayList<>(fileGroups.values());
        }
    }

    static class FileGroup {

        final String md5;
        final long size;
        private final Set<File> files = Collections.synchronizedSet(new LinkedHashSet<>());
        boolean isCollapsed = false;

        FileGroup(File firstFile, long size, String md5) {
            this.md5 = md5;
            this.size = size;
            files.add(firstFile);
        }

        public void addFile(File file) {
            files.add(file);
        }

        public List<File> copyFiles() {
            synchronized (files) {
                return new ArrayList<>(files);
            }
        }

        public int numFiles() {
            return files.size();
        }
    }

    static class DedupGroupAdapter extends RecyclerView.Adapter<DedupGroupAdapter.DedupGroupViewHolder> {

        private final Context context;
        private final File startDirectory;
        private final Set<File> selectedFiles;
        final List<FileGroup> fileGroups = new LinkedList<>();
        private OnFileClicked onFileClicked;

        DedupGroupAdapter(Context context, File startDirectory, Set<File> selectedFiles) {
            this.context = context;
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void whenFileClicked(OnFileClicked onFileClicked) {
            this.onFileClicked = onFileClicked;
        }

        void invalidate(List<FileGroup> fileGroups) {
            this.fileGroups.clear();
            this.fileGroups.addAll(fileGroups);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DedupGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View groupView = LayoutInflater.from(context)
                    .inflate(R.layout.dedup_group, parent, false);
            return new DedupGroupViewHolder(groupView);
        }

        @Override
        public void onBindViewHolder(@NonNull DedupGroupViewHolder viewHolder, int position) {
            FileGroup group = fileGroups.get(position);
            List<File> files = group.copyFiles();

            viewHolder.titleTextView.setText(context.getString(R.string.x_files_y_each, files.size(), getSizeString(files.get(0).length())));
            viewHolder.triangleImageView.setRotation(group.isCollapsed ? -90f : 0f);
            viewHolder.filesView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

            viewHolder.titleBarView.setOnClickListener(v -> {
                group.isCollapsed = !group.isCollapsed;
                viewHolder.filesView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

                float targetRotation = group.isCollapsed ? -90f : 0f;
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
                startIndex += fileGroups.get(g).numFiles();
            }

            LayoutInflater layoutInflater = LayoutInflater.from(context);
            for (int i = 0; i < requiredCount; i++) {
                File file = files.get(i);
                View groupView;

                if (i < nowCount) {
                    groupView = viewHolder.filesView.getChildAt(i);
                } else {
                    groupView = layoutInflater.inflate(R.layout.popup_file_item, viewHolder.filesView, false);
                    viewHolder.filesView.addView(groupView);
                }

                PopupFileItem fileItem = new PopupFileItem(groupView);
                fileItem.setIndex(startIndex + i);
                fileItem.setIcon(R.drawable.i_file_24);
                fileItem.setPath(getRelativePath(startDirectory, file));
                fileItem.setSelected(selectedFiles.contains(file));

                groupView.setOnClickListener(v -> {
                    // selecting only starts once something is selected (e.g. via long-press)
                    if (!selectedFiles.isEmpty()) {
                        toggleSelected(file, position);
                    }
                });
                groupView.setOnLongClickListener(v -> {
                    toggleSelected(file, position);
                    return true;
                });
            }
            if (nowCount > requiredCount) {
                viewHolder.filesView.removeViews(requiredCount, nowCount - requiredCount);
            }
        }

        private void toggleSelected(File file, int position) {
            boolean isChecked;
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
                isChecked = false;
            } else {
                selectedFiles.add(file);
                isChecked = true;
            }
            notifyItemChanged(position);
            if (onFileClicked != null) {
                onFileClicked.accept(position, file, isChecked);
            }
        }

        @Override
        public int getItemCount() {
            return fileGroups.size();
        }

        interface OnFileClicked {
            void accept(int position, File file, boolean isChecked);
        }

        static class DedupGroupViewHolder extends RecyclerView.ViewHolder {

            final View titleBarView;
            final ImageView triangleImageView;
            final TextView titleTextView;
            final LinearLayout filesView;

            DedupGroupViewHolder(View groupView) {
                super(groupView);
                titleBarView = groupView.findViewById(R.id.group_title_bar);
                triangleImageView = groupView.findViewById(R.id.group_triangle);
                titleTextView = groupView.findViewById(R.id.group_title);
                filesView = groupView.findViewById(R.id.group_files);
            }
        }
    }
}
