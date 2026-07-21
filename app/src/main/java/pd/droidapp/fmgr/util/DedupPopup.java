package pd.droidapp.fmgr.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getFileMd5;
import static pd.droidapp.fmgr.util.Util.getRelativePath;
import static pd.droidapp.fmgr.util.Util.getSizeString;

public class DedupPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<Collection<File>> onDelete;

    private final PopupWindow popupWindow;
    private final View selectionBar;
    private final TextView numSelectedTextView;
    private final ImageButton smartSelectButton;
    private final ImageButton deleteButton;
    private final ImageView statusIcon;
    private final TextView statusText;

    private final DedupGroupAdapter dedupGroupAdapter;

    private Scanner scanner;

    public DedupPopup(Context context, View containerView, File startDirectory) {
        this.context = context;
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        dedupGroupAdapter = new DedupGroupAdapter(context, startDirectory);
        dedupGroupAdapter.whenFileClicked((position, file, isChecked) -> containerView.post(this::updateButtons));

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.dedup_popup,
                containerView != null ? (ViewGroup) containerView : null,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        ImageButton closeButton = popupView.findViewById(R.id.action_close);

        RecyclerView groupsListView = popupView.findViewById(R.id.files_list);

        statusIcon = popupView.findViewById(R.id.status_icon);
        statusText = popupView.findViewById(R.id.status_text);

        selectionBar = popupView.findViewById(R.id.selection_bar);
        numSelectedTextView = popupView.findViewById(R.id.num_selected);
        smartSelectButton = popupView.findViewById(R.id.smart_select);
        deleteButton = popupView.findViewById(R.id.action_delete);

        closeButton.setOnClickListener(v -> dismiss());

        smartSelectButton.setOnClickListener(v -> {
            List<File> newlySelected = suggestToSelect(dedupGroupAdapter.selectedFiles);
            dedupGroupAdapter.selectedFiles.addAll(newlySelected);
            containerView.post(() -> {
                dedupGroupAdapter.notifyDataSetChanged();
                updateButtons();
            });
        });

        groupsListView.setLayoutManager(new LinearLayoutManager(context));
        groupsListView.setAdapter(dedupGroupAdapter);

        deleteButton.setOnClickListener(v -> {
            if (onDelete != null) {
                onDelete.accept(dedupGroupAdapter.selectedFiles);
            }
            dismiss();
        });

        popupView.setOnClickListener(v -> dismiss());
        popupArea.setOnClickListener(v -> {
            // dummy
        });

        popupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24);
    }

    public void whenDeleteClicked(Consumer<Collection<File>> onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        if (containerView != null) {
            containerView.post(() -> {
                popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
                doScan();
            });
        }
    }

    private void doScan() {
        scanner = new Scanner();
        scanner.start(startDirectory);
    }

    void updateButtons() {
        int numSelected = dedupGroupAdapter.selectedFiles.size();
        if (numSelected > 0) {
            numSelectedTextView.setText(context.getString(R.string.num_selected_format, numSelected));
            selectionBar.setVisibility(View.VISIBLE);
        } else {
            selectionBar.setVisibility(View.GONE);
        }
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
                RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                rotateAnim.setDuration(1000);
                rotateAnim.setRepeatCount(Animation.INFINITE);
                statusIcon.startAnimation(rotateAnim);
                statusText.setText(R.string.scanning);
                updateButtons();
            });
        }

        @Override
        protected void onScanUpdated(int numFilesScanned) {
            containerView.post(() -> {
                updateDedupGroups();
                updateButtons();
                if (isCompleted()) {
                    statusIcon.clearAnimation();
                    statusIcon.setImageResource(R.drawable.baseline_done_24);
                }
                int totalFiles = 0;
                for (FileGroup group : dedupGroupAdapter.fileGroups) {
                    totalFiles += group.numFiles();
                }
                statusText.setText(context.getString(R.string.x_scanned_y_found_groups,
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
        private final Set<File> selectedFiles = new HashSet<>();
        final List<FileGroup> fileGroups = new LinkedList<>();
        private OnFileClicked onFileClicked;

        DedupGroupAdapter(Context context, File startDirectory) {
            this.context = context;
            this.startDirectory = startDirectory;
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

            viewHolder.groupTitleText.setText(context.getString(R.string.x_files_y_each, files.size(), getSizeString(files.get(0).length())));

            viewHolder.groupFilesView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

            viewHolder.groupTriangle.setRotation(group.isCollapsed ? -90f : 0f);
            viewHolder.groupHeader.setOnClickListener(v -> {
                group.isCollapsed = !group.isCollapsed;
                viewHolder.groupFilesView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

                float targetRotation = group.isCollapsed ? -90f : 0f;
                float currentRotation = viewHolder.groupTriangle.getRotation();
                ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
                animator.setDuration(200);
                animator.addUpdateListener(animation -> {
                    float rotation = (float) animation.getAnimatedValue();
                    viewHolder.groupTriangle.setRotation(rotation);
                });
                animator.start();
            });

            int nowCount = viewHolder.groupFilesView.getChildCount();
            int requiredCount = files.size();

            int startIndex = 1;
            for (int g = 0; g < position; g++) {
                startIndex += fileGroups.get(g).numFiles();
            }

            LayoutInflater layoutInflater = LayoutInflater.from(context);
            for (int i = 0; i < requiredCount; i++) {
                File file = files.get(i);
                View fileView;

                if (i < nowCount) {
                    fileView = viewHolder.groupFilesView.getChildAt(i);
                } else {
                    fileView = layoutInflater.inflate(R.layout.popup_file, viewHolder.groupFilesView, false);
                    viewHolder.groupFilesView.addView(fileView);
                }

                ImageView icon = fileView.findViewById(R.id.popup_file_icon);
                ImageView selectedIcon = fileView.findViewById(R.id.popup_file_selected);
                TextView nameText = fileView.findViewById(R.id.popup_file_name);
                TextView indexText = fileView.findViewById(R.id.popup_file_index);

                indexText.setText(String.valueOf(startIndex + i));
                icon.setImageResource(R.drawable.i_file_24);
                nameText.setText(getRelativePath(startDirectory, file));
                selectedIcon.setVisibility(selectedFiles.contains(file) ? View.VISIBLE : View.GONE);

                fileView.setOnClickListener(v -> {
                    // selecting only starts once something is selected (e.g. via long-press)
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
                viewHolder.groupFilesView.removeViews(requiredCount, nowCount - requiredCount);
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

            final View groupHeader;
            final ImageView groupTriangle;
            final TextView groupTitleText;
            final LinearLayout groupFilesView;

            DedupGroupViewHolder(View groupView) {
                super(groupView);
                groupHeader = groupView.findViewById(R.id.group_header);
                groupTriangle = groupView.findViewById(R.id.group_triangle);
                groupTitleText = groupView.findViewById(R.id.group_title);
                groupFilesView = groupView.findViewById(R.id.group_files);
            }
        }
    }
}
