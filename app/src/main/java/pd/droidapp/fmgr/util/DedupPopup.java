package pd.droidapp.fmgr.util;

import static pd.droidapp.fmgr.util.Util.basename;
import static pd.droidapp.fmgr.util.Util.getFileMd5;
import static pd.droidapp.fmgr.util.Util.getRelativePath;
import static pd.droidapp.fmgr.util.Util.getSizeString;

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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;

public class DedupPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<Collection<File>> onConfirm;

    private final PopupWindow popupWindow;
    private final ImageButton smartSelectButton;
    private final ImageView statusIcon;
    private final TextView statusText;
    private final Button okButton;

    private final DupGroupAdapter dupGroupAdapter;

    private Scanner scanner;

    public DedupPopup(Context context, View containerView, File startDirectory) {
        this.context = context;
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        dupGroupAdapter = new DupGroupAdapter(context, startDirectory);
        dupGroupAdapter.whenDupGroupFileClicked((position, file, isChecked) -> {
            containerView.post(this::updateButtons);
        });

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.dedup_popup,
                containerView != null ? (ViewGroup) containerView : null,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        ImageButton closeButton = popupView.findViewById(R.id.button_close);
        smartSelectButton = popupView.findViewById(R.id.smart_select);

        RecyclerView groupsListView = popupView.findViewById(R.id.groups_list);

        statusIcon = popupView.findViewById(R.id.status_icon);
        statusText = popupView.findViewById(R.id.status_text);

        okButton = popupView.findViewById(R.id.button_ok);
        Button cancelButton = popupView.findViewById(R.id.button_cancel);

        closeButton.setOnClickListener(v -> dismiss());

        smartSelectButton.setOnClickListener(v -> {
            dupGroupAdapter.selectedFiles.clear();
            for (FileGroup group : dupGroupAdapter.dupFileGroups) {
                dupGroupAdapter.selectedFiles.addAll(filesToRemove(group));
            }
            containerView.post(() -> {
                updateDupFileGroups();
                updateButtons();
            });
        });

        groupsListView.setLayoutManager(new LinearLayoutManager(context));
        groupsListView.setAdapter(dupGroupAdapter);

        okButton.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.accept(dupGroupAdapter.selectedFiles);
            }
            dismiss();
        });
        cancelButton.setOnClickListener(v -> dismiss());

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

    public void whenConfirmClicked(Consumer<Collection<File>> onConfirm) {
        this.onConfirm = onConfirm;
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
        scanner.whenScanStarted(() -> containerView.post(() -> {
            RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnim.setDuration(1000);
            rotateAnim.setRepeatCount(Animation.INFINITE);
            statusIcon.startAnimation(rotateAnim);
            statusText.setText(R.string.scanning);

            updateButtons();
        }));
        scanner.whenScanUpdated((numFilesScanned) -> containerView.post(() -> {
            updateDupFileGroups();
            updateButtons();
            if (scanner.isCompleted()) {
                statusIcon.clearAnimation();
                statusIcon.setImageResource(R.drawable.baseline_done_24);
            }
            statusText.setText(context.getString(R.string.scanned_x_found_y, numFilesScanned, dupGroupAdapter.dupFileGroups.size()));
        }));

        scanner.start(startDirectory);
    }

    void updateButtons() {
        if (dupGroupAdapter.dupFileGroups.isEmpty()) {
            smartSelectButton.setVisibility(View.GONE);
            okButton.setVisibility(View.GONE);
        } else {
            smartSelectButton.setVisibility(View.VISIBLE);
            okButton.setVisibility(View.VISIBLE);

            int numSelected = dupGroupAdapter.selectedFiles.size();
            if (numSelected > 0) {
                okButton.setEnabled(true);
                okButton.setText(context.getString(R.string.delete_selected_x, numSelected));
            } else {
                okButton.setEnabled(false);
                okButton.setText(context.getString(R.string.delete_selected));
            }
        }
    }

    void updateDupFileGroups() {
        List<FileGroup> dupFileGroups = scanner.copyFileGroups().stream()
                .filter(group -> group.numFiles() > 1)
                .collect(Collectors.toList());
        dupGroupAdapter.invalidate(dupFileGroups);
    }

    private List<File> filesToRemove(FileGroup fileGroup) {
        List<File> dupFiles = fileGroup.copyFiles();
        List<File> filesToRemove = new ArrayList<>(dupFiles.size() - 1);
        File fileToKeep = null;
        for (File f : dupFiles) {
            if (fileToKeep == null) {
                fileToKeep = f;
                continue;
            }
            if (smartCompare(f, fileToKeep) < 0) {
                filesToRemove.add(fileToKeep);
                fileToKeep = f;
            } else {
                filesToRemove.add(f);
            }
        }
        return filesToRemove;
    }

    private int smartCompare(File f1, File f2) {
        long f1Time = f1.lastModified();
        long f2Time = f2.lastModified();
        if (f1Time != f2Time) {
            return -Long.compare(f1Time, f2Time);
        }

        String f1Basename = basename(f1);
        String f2Basename = basename(f2);
        if (!f1Basename.equals(f2Basename)) {
            return f1Basename.length() - f2Basename.length();
        }

        return f1.getAbsolutePath().length() - f2.getAbsolutePath().length();
    }

    public void dismiss() {
        if (scanner != null) {
            scanner.cancel();
        }
        popupWindow.dismiss();
    }

    static class Scanner {

        private static final int UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private Runnable onScanStarted;
        private Consumer<Integer> onScanUpdated;
        private Thread scanThread;
        private Timer scanUpdateTimer;
        final AtomicInteger numFilesScanned = new AtomicInteger(0);
        final Map<String, FileGroup> fileGroups = new ConcurrentHashMap<>();

        public void whenScanStarted(Runnable onScanStarted) {
            this.onScanStarted = onScanStarted;
        }

        public void whenScanUpdated(Consumer<Integer> onScanUpdated) {
            this.onScanUpdated = onScanUpdated;
        }

        public void start(File startDirectory) {
            if (isCancelled() || isCompleted()) {
                return;
            }

            if (onScanStarted != null) {
                onScanStarted.run();
            }

            startTimer();

            scanThread = new Thread(() -> {
                Map<Long, List<File>> reachedFiles = new HashMap<>();
                Stack<File> stack = new Stack<>();
                stack.push(startDirectory);
                while (!cancelled.get() && !stack.isEmpty()) {
                    File[] files = stack.pop().listFiles();
                    if (files == null) {
                        continue;
                    }

                    for (File file : files) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (file.isFile()) {
                            numFilesScanned.incrementAndGet();
                            long size = file.length();
                            if (size == 0) {
                                continue;  // skip empty files
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
                                // not add to save memory
                            }
                        } else if (file.isDirectory()) {
                            stack.push(file);
                        }
                    }
                }
                if (!cancelled.get()) {
                    completed.set(true);
                }
            });
            scanThread.start();
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

        private void startTimer() {
            scanUpdateTimer = new Timer();
            scanUpdateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (cancelled.get()) {
                        clearTimer();
                        return;
                    }
                    if (onScanUpdated != null) {
                        onScanUpdated.accept(numFilesScanned.get());
                    }
                    if (completed.get()) {
                        clearTimer();
                    }
                }
            }, UPDATE_INTERVAL_IN_MILLISECONDS, UPDATE_INTERVAL_IN_MILLISECONDS);
        }

        private void clearTimer() {
            if (scanUpdateTimer != null) {
                scanUpdateTimer.cancel();
                scanUpdateTimer.purge();
                scanUpdateTimer = null;
            }
        }

        public void cancel() {
            cancelled.set(true);
            if (scanThread != null) {
                scanThread.interrupt();
            }
        }

        public boolean isCompleted() {
            return completed.get();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public List<FileGroup> copyFileGroups() {
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

    static class DupGroupAdapter extends RecyclerView.Adapter<DupGroupAdapter.DupGroupViewHolder> {

        private final Context context;
        private final File startDirectory;
        private final Set<File> selectedFiles = new HashSet<>();
        final List<FileGroup> dupFileGroups = new LinkedList<>();
        private OnDupFileClicked onDupGroupFileClicked;

        DupGroupAdapter(Context context, File startDirectory) {
            this.context = context;
            this.startDirectory = startDirectory;
        }

        void whenDupGroupFileClicked(OnDupFileClicked onDupGroupFileClicked) {
            this.onDupGroupFileClicked = onDupGroupFileClicked;
        }

        void invalidate(List<FileGroup> dupFileGroups) {
            this.dupFileGroups.clear();
            this.dupFileGroups.addAll(dupFileGroups);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public DupGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View groupView = LayoutInflater.from(context)
                    .inflate(R.layout.dup_group, parent, false);
            return new DupGroupViewHolder(groupView);
        }

        @Override
        public void onBindViewHolder(@NonNull DupGroupViewHolder viewHolder, int position) {
            FileGroup group = dupFileGroups.get(position);
            List<File> files = group.copyFiles();

            viewHolder.groupTitleText.setText(context.getString(R.string.x_files_y_each, files.size(), getSizeString(files.get(0).length())));

            viewHolder.groupItemsView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

            viewHolder.groupTriangle.setRotation(group.isCollapsed ? -90f : 0f);
            viewHolder.groupTriangle.setOnClickListener(v -> {
                group.isCollapsed = !group.isCollapsed;
                viewHolder.groupItemsView.setVisibility(group.isCollapsed ? View.GONE : View.VISIBLE);

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

            int nowCount = viewHolder.groupItemsView.getChildCount();
            int requiredCount = files.size();

            LayoutInflater layoutInflater = LayoutInflater.from(context);
            for (int i = 0; i < requiredCount; i++) {
                File file = files.get(i);
                CheckBox checkBox;

                if (i < nowCount) {
                    checkBox = (CheckBox) viewHolder.groupItemsView.getChildAt(i);
                } else {
                    checkBox = (CheckBox) layoutInflater.inflate(R.layout.dup_group_item, viewHolder.groupItemsView, false);
                    viewHolder.groupItemsView.addView(checkBox);
                }

                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(selectedFiles.contains(file));
                checkBox.setText(getRelativePath(startDirectory, file));
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedFiles.add(file);
                    } else {
                        selectedFiles.remove(file);
                    }
                    if (onDupGroupFileClicked != null) {
                        onDupGroupFileClicked.accept(position, file, isChecked);
                    }
                });
            }
            if (nowCount > requiredCount) {
                viewHolder.groupItemsView.removeViews(requiredCount, nowCount - requiredCount);
            }
        }

        @Override
        public int getItemCount() {
            return dupFileGroups.size();
        }

        interface OnDupFileClicked {
            void accept(int position, File file, boolean isChecked);
        }

        static class DupGroupViewHolder extends RecyclerView.ViewHolder {

            final ImageView groupTriangle;
            final TextView groupTitleText;
            final LinearLayout groupItemsView;

            DupGroupViewHolder(View groupView) {
                super(groupView);
                groupTriangle = groupView.findViewById(R.id.group_triangle);
                groupTitleText = groupView.findViewById(R.id.group_title);
                groupItemsView = groupView.findViewById(R.id.group_items);
            }
        }
    }
}
