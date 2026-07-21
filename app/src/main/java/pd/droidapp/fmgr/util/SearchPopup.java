package pd.droidapp.fmgr.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

import static pd.util.PathExtension.compare;

public class SearchPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;
    private static final int SEARCH_RESULT_UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<File> onFileClicked;
    private Consumer<Collection<File>> onDelete;

    private final PopupWindow popupWindow;
    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final ImageButton closeButton;
    private final View statusBar;
    private final ImageView searchStatusIcon;
    private final TextView searchStatusText;
    private final View selectionBar;
    private final TextView numSelectedTextView;
    private final ImageButton selectAllButton;
    private final ImageButton selectNoneButton;
    private final ImageButton jumpButton;
    private final ImageButton deleteButton;
    private final SearchResultAdapter searchResultAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::doSearch;
    private Searcher searcher;

    public SearchPopup(Context context, View containerView, File startDirectory) {
        this.context = context;
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.search_popup,
                containerView != null ? (ViewGroup) containerView : null,
                false);

        LinearLayout popupArea = popupView.findViewById(R.id.popup_area);
        searchEdit = popupView.findViewById(R.id.search_edit);
        searchEditClearButton = popupView.findViewById(R.id.search_edit_clear);
        closeButton = popupView.findViewById(R.id.action_close);
        statusBar = popupView.findViewById(R.id.status_bar);
        searchStatusIcon = popupView.findViewById(R.id.status_icon);
        searchStatusText = popupView.findViewById(R.id.status_text);
        selectionBar = popupView.findViewById(R.id.selection_bar);
        numSelectedTextView = popupView.findViewById(R.id.num_selected);
        selectAllButton = popupView.findViewById(R.id.select_all);
        selectNoneButton = popupView.findViewById(R.id.select_none);
        jumpButton = popupView.findViewById(R.id.action_jump);
        deleteButton = popupView.findViewById(R.id.action_delete);
        RecyclerView searchResultItemsList = popupView.findViewById(R.id.files_list);

        searchResultAdapter = new SearchResultAdapter(context, startDirectory);
        searchResultAdapter.whenSelectionChanged(() -> containerView.post(this::updateSelectionBar));
        searchResultItemsList.setLayoutManager(new LinearLayoutManager(context));
        searchResultItemsList.setAdapter(searchResultAdapter);

        closeButton.setOnClickListener(v -> dismiss());

        selectAllButton.setOnClickListener(v -> {
            searchResultAdapter.selectedFiles.clear();
            for (SearchResultItem item : searchResultAdapter.results) {
                searchResultAdapter.selectedFiles.add(item.file);
            }
            containerView.post(() -> {
                searchResultAdapter.notifyDataSetChanged();
                updateSelectionBar();
            });
        });

        selectNoneButton.setOnClickListener(v -> {
            searchResultAdapter.clearSelected();
            containerView.post(this::updateSelectionBar);
        });

        jumpButton.setOnClickListener(v -> {
            if (searchResultAdapter.selectedFiles.size() == 1) {
                File file = searchResultAdapter.selectedFiles.iterator().next();
                if (onFileClicked != null) {
                    onFileClicked.accept(file);
                }
                dismiss();
            }
        });

        deleteButton.setOnClickListener(v -> {
            if (onDelete != null) {
                onDelete.accept(new ArrayList<>(searchResultAdapter.selectedFiles));
            }
            dismiss();
        });

        searchEditClearButton.setOnClickListener(v -> {
            searchEdit.setText("");
        });

        searchEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchRunnable);
                cancelSearch();
                clearSelection();

                String query = s.toString();
                if (query.isEmpty()) {
                    closeButton.setVisibility(View.VISIBLE);
                    searchEditClearButton.setVisibility(View.GONE);
                    clearResults();
                    clearStatus();
                } else {
                    closeButton.setVisibility(View.GONE);
                    searchEditClearButton.setVisibility(View.VISIBLE);
                    clearResults();
                    clearStatus();
                    handler.postDelayed(searchRunnable, SEARCH_START_DELAY_IN_MILLISECONDS);
                }
            }
        });

        popupView.setOnClickListener(v -> dismiss());
        popupArea.setOnClickListener(v -> {
            // dummy to prevent dismiss when click inside
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

    public void whenSearchResultFileClicked(Consumer<File> onFileClicked) {
        this.onFileClicked = onFileClicked;
    }

    public void whenDeleteClicked(Consumer<Collection<File>> onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        if (containerView != null) {
            containerView.post(() -> {
                popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
                searchEdit.requestFocus();
            });
        }
    }

    public void dismiss() {
        cancelSearch();
        popupWindow.dismiss();
    }

    private void doSearch() {
        String query = searchEdit.getText().toString();
        if (query.isEmpty()) {
            return;
        }

        cancelSearch();
        clearResults();

        searcher = new Searcher(SEARCH_RESULT_UPDATE_INTERVAL_IN_MILLISECONDS);
        searcher.whenSearchStarted(() -> containerView.post(() -> {
            statusBar.setVisibility(View.VISIBLE);
            searchStatusIcon.setImageResource(R.drawable.baseline_refresh_24);
            RotateAnimation rotateAnim = new RotateAnimation(0, 360,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnim.setDuration(1000);
            rotateAnim.setRepeatCount(Animation.INFINITE);
            searchStatusIcon.startAnimation(rotateAnim);
            searchStatusText.setText(R.string.search_status_searching);
        }));
        searcher.whenSearchUpdated((numResults) -> containerView.post(() -> {
            searchResultAdapter.invalidate(searcher.copyResults());
            if (searcher.isCompleted()) {
                searchStatusIcon.clearAnimation();
                searchStatusIcon.setImageResource(R.drawable.baseline_done_24);
                searchStatusText.setText(context.getString(R.string.x_scanned_y_found,
                        searcher.numFilesScanned.get(), numResults));
            } else {
                searchStatusText.setText(R.string.search_status_searching);
            }
        }));

        searcher.start(startDirectory, query);
    }

    private void cancelSearch() {
        if (searcher != null) {
            searcher.cancel();
            searcher = null;
        }
        searchStatusIcon.clearAnimation();
    }

    private void clearResults() {
        searchResultAdapter.results.clear();
        searchResultAdapter.notifyDataSetChanged();
    }

    private void clearStatus() {
        statusBar.setVisibility(View.GONE);
        searchStatusIcon.clearAnimation();
        searchStatusIcon.setImageResource(android.R.color.transparent);
        searchStatusText.setText("");
    }

    private void clearSelection() {
        searchResultAdapter.clearSelected();
        selectionBar.setVisibility(View.GONE);
    }

    private void updateSelectionBar() {
        int numSelected = searchResultAdapter.selectedFiles.size();
        if (numSelected > 0) {
            numSelectedTextView.setText(context.getString(R.string.num_selected_format, numSelected));
            selectionBar.setVisibility(View.VISIBLE);
            // jump is only meaningful for a single selection
            jumpButton.setVisibility(numSelected == 1 ? View.VISIBLE : View.GONE);
        } else {
            selectionBar.setVisibility(View.GONE);
        }
    }

    static class Searcher {

        private final int searchResultUpdateIntervalInMilliseconds;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private Runnable onSearchStarted;
        private Consumer<Integer> onSearchUpdated;
        private Thread searchThread;
        private Timer updateTimer;
        final AtomicInteger numFilesScanned = new AtomicInteger(0);
        final AtomicInteger numResults = new AtomicInteger(0);
        final List<SearchResultItem> results = Collections.synchronizedList(new LinkedList<>());

        public Searcher(int searchResultUpdateIntervalInMilliseconds) {
            this.searchResultUpdateIntervalInMilliseconds = searchResultUpdateIntervalInMilliseconds;
        }

        public void whenSearchStarted(Runnable onSearchStarted) {
            this.onSearchStarted = onSearchStarted;
        }

        public void whenSearchUpdated(Consumer<Integer> onSearchUpdated) {
            this.onSearchUpdated = onSearchUpdated;
        }

        public void start(File startDirectory, String query) {
            if (isCancelled() || isCompleted()) {
                return;
            }

            if (onSearchStarted != null) {
                onSearchStarted.run();
            }

            startTimer();

            String queryLower = query.toLowerCase();
            searchThread = new Thread(() -> {
                Comparator<File> pathComparator = (f1, f2) ->
                        compare(f1.getPath(), f2.getPath());

                // First pass: match by name
                Stack<File> stack = new Stack<>();
                stack.push(startDirectory);
                while (!cancelled.get() && !stack.isEmpty()) {
                    File[] files = stack.pop().listFiles();
                    if (files == null) {
                        continue;
                    }
                    Arrays.sort(files, pathComparator);

                    // push subdirectories in reverse so they are visited in ascending order
                    for (int i = files.length - 1; i >= 0; i--) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (files[i].isDirectory()) {
                            stack.push(files[i]);
                        }
                    }
                    for (File file : files) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (file.isFile()) {
                            numFilesScanned.incrementAndGet();
                        }
                        if ((file.isDirectory() || file.isFile())
                                && file.getName().contains(query)) {
                            addResult(new SearchResultItem(file));
                        }
                    }
                }

                // Second pass: match by content (text files whose name did not match)
                stack.push(startDirectory);
                while (!cancelled.get() && !stack.isEmpty()) {
                    File[] files = stack.pop().listFiles();
                    if (files == null) {
                        continue;
                    }
                    Arrays.sort(files, pathComparator);

                    for (int i = files.length - 1; i >= 0; i--) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (files[i].isDirectory()) {
                            stack.push(files[i]);
                        }
                    }
                    for (File file : files) {
                        if (cancelled.get()) {
                            return;
                        }
                        if (file.isFile() && !file.getName().contains(query)) {
                            numFilesScanned.incrementAndGet();
                            if ((isTextFile(file) || isSmallAnonymousFile(file))
                                    && fileContainsText(file, queryLower)) {
                                addResult(new SearchResultItem(file));
                            }
                        }
                    }
                }

                if (!cancelled.get()) {
                    completed.set(true);
                }
            });
            searchThread.start();
        }

        private void addResult(SearchResultItem result) {
            results.add(result);
            numResults.incrementAndGet();
        }

        private void startTimer() {
            updateTimer = new Timer();
            updateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (cancelled.get()) {
                        clearTimer();
                        return;
                    }
                    if (onSearchUpdated != null) {
                        onSearchUpdated.accept(numResults.get());
                    }
                    if (completed.get()) {
                        clearTimer();
                    }
                }
            }, searchResultUpdateIntervalInMilliseconds, searchResultUpdateIntervalInMilliseconds);
        }

        private void clearTimer() {
            if (updateTimer != null) {
                updateTimer.cancel();
                updateTimer.purge();
                updateTimer = null;
            }
        }

        void cancel() {
            cancelled.set(true);
            if (searchThread != null) {
                searchThread.interrupt();
            }
        }

        public boolean isCompleted() {
            return completed.get();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public List<SearchResultItem> copyResults() {
            synchronized (results) {
                return new ArrayList<>(results);
            }
        }

        private boolean isTextFile(File file) {
            String lowerName = file.getName().toLowerCase();
            return lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json") ||
                    lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".css") ||
                    lowerName.endsWith(".js") || lowerName.endsWith(".java") || lowerName.endsWith(".kt") ||
                    lowerName.endsWith(".py") || lowerName.endsWith(".c") || lowerName.endsWith(".cpp") ||
                    lowerName.endsWith(".h") || lowerName.endsWith(".hpp") || lowerName.endsWith(".sh") ||
                    lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.endsWith(".properties") ||
                    lowerName.endsWith(".gradle") || lowerName.endsWith(".csv") || lowerName.endsWith(".log");
        }

        private boolean isSmallAnonymousFile(File file) {
            return !file.getName().contains(".") || file.length() < 1024 * 1024;
        }

        private boolean fileContainsText(File file, String query) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains(query)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
            return false;
        }
    }

    public static class SearchResultItem {

        public final File file;

        public SearchResultItem(File file) {
            this.file = file;
        }
    }

    static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

        private final Context context;
        private final File startDirectory;
        final Set<File> selectedFiles = new HashSet<>();
        private final List<SearchResultItem> results = new LinkedList<>();
        private Runnable onSelectionChanged;

        SearchResultAdapter(Context context, File startDirectory) {
            this.context = context;
            this.startDirectory = startDirectory;
        }

        void invalidate(List<SearchResultItem> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        void whenSelectionChanged(Runnable onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
        }

        boolean hasSelection() {
            return !selectedFiles.isEmpty();
        }

        void clearSelected() {
            selectedFiles.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.popup_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            File file = results.get(position).file;

            viewHolder.indexText.setText(String.valueOf(position + 1));

            if (file.isDirectory()) {
                viewHolder.iconView.setImageResource(R.drawable.i_directory_24);
            } else {
                viewHolder.iconView.setImageResource(R.drawable.i_file_24);
            }

            viewHolder.selectedIcon.setVisibility(selectedFiles.contains(file) ? View.VISIBLE : View.GONE);

            viewHolder.itemView.setOnClickListener(v -> {
                // selecting only starts once something is selected (e.g. via long-press);
                // jumping is done via the selection bar's jump button on a single selection
                if (!selectedFiles.isEmpty()) {
                    toggleSelected(file, position);
                }
            });
            viewHolder.itemView.setOnLongClickListener(v -> {
                toggleSelected(file, position);
                return true;
            });

            viewHolder.pathText.setText(Util.getRelativePath(startDirectory, file));
        }

        private void toggleSelected(File file, int position) {
            if (selectedFiles.contains(file)) {
                selectedFiles.remove(file);
            } else {
                selectedFiles.add(file);
            }
            notifyItemChanged(position);
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            final ImageView iconView;
            final ImageView selectedIcon;
            final TextView pathText;
            final TextView indexText;

            ViewHolder(View itemView) {
                super(itemView);
                indexText = itemView.findViewById(R.id.popup_file_index);
                iconView = itemView.findViewById(R.id.popup_file_icon);
                selectedIcon = itemView.findViewById(R.id.popup_file_selected);
                pathText = itemView.findViewById(R.id.popup_file_name);
            }
        }
    }
}
