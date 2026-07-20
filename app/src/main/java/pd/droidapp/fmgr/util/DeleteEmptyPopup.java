package pd.droidapp.fmgr.util;

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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import pd.droidapp.fmgr.R;

import static pd.droidapp.fmgr.util.Util.getRelativePath;

public class DeleteEmptyPopup {

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private OnConfirm onConfirm;

    private final PopupWindow popupWindow;
    private final View selectionBar;
    private final TextView numSelectedTextView;
    private final ImageButton selectAllButton;
    private final ImageButton deleteButton;
    private final ImageButton deleteParentsButton;
    private final ImageView statusIcon;
    private final TextView statusText;

    private final EmptyItemAdapter emptyItemAdapter;

    private Scanner scanner;

    public DeleteEmptyPopup(Context context, View containerView, File startDirectory) {
        this.context = context;
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        emptyItemAdapter = new EmptyItemAdapter(startDirectory);
        emptyItemAdapter.whenEmptyItemClicked(() -> containerView.post(this::updateButtons));

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.delete_empty_popup,
                containerView != null ? (ViewGroup) containerView : null,
                false);

        View popupArea = popupView.findViewById(R.id.popup_area);
        ImageButton closeButton = popupView.findViewById(R.id.action_close);

        RecyclerView filesListView = popupView.findViewById(R.id.files_list);

        statusIcon = popupView.findViewById(R.id.status_icon);
        statusText = popupView.findViewById(R.id.status_text);

        selectionBar = popupView.findViewById(R.id.selection_bar);
        numSelectedTextView = popupView.findViewById(R.id.num_selected);
        selectAllButton = popupView.findViewById(R.id.select_all);
        deleteButton = popupView.findViewById(R.id.action_delete);
        deleteParentsButton = popupView.findViewById(R.id.action_delete_parents);

        closeButton.setOnClickListener(v -> dismiss());

        selectAllButton.setOnClickListener(v -> {
            emptyItemAdapter.selectedFiles.clear();
            for (EmptyItem item : emptyItemAdapter.emptyItems) {
                emptyItemAdapter.selectedFiles.add(item.file);
            }
            containerView.post(() -> {
                emptyItemAdapter.notifyDataSetChanged();
                updateButtons();
            });
        });

        filesListView.setLayoutManager(new LinearLayoutManager(context));
        filesListView.setAdapter(emptyItemAdapter);

        deleteButton.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.accept(emptyItemAdapter.selectedFiles, false);
            }
            dismiss();
        });

        deleteParentsButton.setOnClickListener(v -> {
            if (onConfirm != null) {
                onConfirm.accept(emptyItemAdapter.selectedFiles, true);
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

    public void whenConfirmClicked(OnConfirm onConfirm) {
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
            updateEmptyItems();
            updateButtons();
            if (scanner.isCompleted()) {
                statusIcon.clearAnimation();
                statusIcon.setImageResource(R.drawable.baseline_done_24);
            }
            statusText.setText(context.getString(R.string.scanned_x_found_y_empty,
                    numFilesScanned, emptyItemAdapter.getItemCount()));
        }));

        scanner.start(startDirectory);
    }

    void updateButtons() {
        int numSelected = emptyItemAdapter.selectedFiles.size();
        if (numSelected > 0) {
            numSelectedTextView.setText(context.getString(R.string.num_selected_format, numSelected));
            selectionBar.setVisibility(View.VISIBLE);
        } else {
            selectionBar.setVisibility(View.GONE);
        }
    }

    void updateEmptyItems() {
        List<EmptyItem> items = scanner.copyEmptyItems();
        Collections.sort(items, (a, b) -> a.file.getAbsolutePath().compareToIgnoreCase(b.file.getAbsolutePath()));
        emptyItemAdapter.invalidate(items);
    }

    public void dismiss() {
        if (scanner != null) {
            scanner.cancel();
        }
        popupWindow.dismiss();
    }

    public interface OnConfirm {

        void accept(Collection<File> files, boolean cascade);
    }

    static class EmptyItem {

        final File file;
        final boolean isDirectory;

        EmptyItem(File file, boolean isDirectory) {
            this.file = file;
            this.isDirectory = isDirectory;
        }
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
        final List<EmptyItem> emptyItems = Collections.synchronizedList(new ArrayList<>());

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
                            if (file.length() == 0) {
                                emptyItems.add(new EmptyItem(file, false));
                            }
                        } else if (file.isDirectory()) {
                            File[] children = file.listFiles();
                            if (children == null || children.length == 0) {
                                emptyItems.add(new EmptyItem(file, true));
                            } else {
                                stack.push(file);
                            }
                        }
                    }
                }
                if (!cancelled.get()) {
                    completed.set(true);
                }
            });
            scanThread.start();
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

        public List<EmptyItem> copyEmptyItems() {
            synchronized (emptyItems) {
                return new ArrayList<>(emptyItems);
            }
        }
    }

    static class EmptyItemAdapter extends RecyclerView.Adapter<EmptyItemAdapter.ViewHolder> {

        private final Set<File> selectedFiles = new HashSet<>();
        final List<EmptyItem> emptyItems = new LinkedList<>();
        private final File startDirectory;
        private Runnable onEmptyItemClicked;

        EmptyItemAdapter(File startDirectory) {
            this.startDirectory = startDirectory;
        }

        void whenEmptyItemClicked(Runnable onEmptyItemClicked) {
            this.onEmptyItemClicked = onEmptyItemClicked;
        }

        void invalidate(List<EmptyItem> emptyItems) {
            this.emptyItems.clear();
            this.emptyItems.addAll(emptyItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.popup_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EmptyItem item = emptyItems.get(position);
            boolean selected = selectedFiles.contains(item.file);

            if (item.isDirectory) {
                holder.icon.setImageResource(R.drawable.i_directory_24);
            } else {
                holder.icon.setImageResource(R.drawable.i_file_24);
            }

            String name = getRelativePath(startDirectory, item.file);
            if (item.isDirectory) {
                name = name + "/";
            }
            holder.nameText.setText(name);

            holder.selectedIcon.setVisibility(selected ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                // selecting only starts once something is selected (e.g. via long-press)
                if (!selectedFiles.isEmpty()) {
                    toggleSelected(item.file, position);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                toggleSelected(item.file, position);
                return true;
            });
        }

        private void toggleSelected(File file, int position) {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
            } else {
                selectedFiles.add(file);
            }
            notifyItemChanged(position);
            if (onEmptyItemClicked != null) {
                onEmptyItemClicked.run();
            }
        }

        @Override
        public int getItemCount() {
            return emptyItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            final ImageView icon;
            final ImageView selectedIcon;
            final TextView nameText;

            ViewHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.popup_file_icon);
                selectedIcon = itemView.findViewById(R.id.popup_file_selected);
                nameText = itemView.findViewById(R.id.popup_file_name);
            }
        }
    }
}
