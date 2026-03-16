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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class SearchPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;
    private static final int SEARCH_RESULT_UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<File> onSearchResultFileClickedListener;

    private final PopupWindow popupWindow;
    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final ImageButton closeButton;
    private final ImageView searchStatusIcon;
    private final TextView searchStatusText;
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
        closeButton = popupView.findViewById(R.id.button_close);
        searchStatusIcon = popupView.findViewById(R.id.status_icon);
        searchStatusText = popupView.findViewById(R.id.status_text);
        RecyclerView searchResultItemsList = popupView.findViewById(R.id.result_list);

        searchResultAdapter = new SearchResultAdapter(context, startDirectory);
        searchResultAdapter.whenSearchResultItemClicked(searchResultItem -> {
            if (onSearchResultFileClickedListener != null) {
                onSearchResultFileClickedListener.accept(searchResultItem.file);
            }
            dismiss();
        });
        searchResultItemsList.setLayoutManager(new LinearLayoutManager(context));
        searchResultItemsList.setAdapter(searchResultAdapter);

        closeButton.setOnClickListener(v -> dismiss());

        searchEditClearButton.setOnClickListener(v -> {
            searchEdit.setText("");
            cancelSearch();
            clearResults();
            clearStatus();
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

    public void whenSearchResultFileClicked(Consumer<File> onSearchResultItemClickedListener) {
        this.onSearchResultFileClickedListener = onSearchResultItemClickedListener;
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
                if (numResults == 0) {
                    searchStatusText.setText(R.string.search_status_no_results);
                } else {
                    searchStatusText.setText(context.getString(R.string.search_status_results, numResults));
                }
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
        searchStatusIcon.clearAnimation();
        searchStatusIcon.setImageResource(android.R.color.transparent);
        searchStatusText.setText("");
    }

    static class Searcher {

        private final int searchResultUpdateIntervalInMilliseconds;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private Runnable onSearchStarted;
        private Consumer<Integer> onSearchUpdated;
        private Thread searchThread;
        private Timer updateTimer;
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

                        String name = file.getName().toLowerCase();
                        if (file.isDirectory()) {
                            if (name.contains(queryLower)) {
                                addResult(new SearchResultItem(file, true, false));
                            }
                            stack.push(file);
                        } else if (file.isFile()) {
                            if (name.contains(queryLower)) {
                                addResult(new SearchResultItem(file, true, false));
                            }
                        }
                    }
                }

                // Second pass: search content (only for text files)
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

                        if (file.isDirectory()) {
                            stack.push(file);
                        } else if (file.isFile() && !file.getName().toLowerCase().contains(queryLower)) {
                            if ((isTextFile(file) || isSmallAnonymousFile(file)) && fileContainsText(file, queryLower)) {
                                addResult(new SearchResultItem(file, false, true));
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
        public final boolean matchedByName;
        public final boolean matchedByContent;

        public SearchResultItem(File file, boolean matchedByName, boolean matchedByContent) {
            this.file = file;
            this.matchedByName = matchedByName;
            this.matchedByContent = matchedByContent;
        }
    }

    static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

        private final Context context;
        private final File startDirectory;
        private final List<SearchResultItem> results = new LinkedList<>();
        private Consumer<SearchResultItem> onSearchResultItemClickedListener;

        SearchResultAdapter(Context context, File startDirectory) {
            this.context = context;
            this.startDirectory = startDirectory;
        }

        void invalidate(List<SearchResultItem> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        void whenSearchResultItemClicked(Consumer<SearchResultItem> onSearchResultItemClickedListener) {
            this.onSearchResultItemClickedListener = onSearchResultItemClickedListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.file_item_32, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            SearchResultItem searchResultItem = results.get(position);
            File file = searchResultItem.file;

            if (file.isDirectory()) {
                viewHolder.iconView.setImageResource(R.drawable.i_directory_24);
            } else {
                viewHolder.iconView.setImageResource(R.drawable.i_file_24);
            }
            viewHolder.itemView.setOnClickListener(v -> {
                if (onSearchResultItemClickedListener != null) {
                    onSearchResultItemClickedListener.accept(searchResultItem);
                }
            });

            viewHolder.pathView.setText(Util.getRelativePath(startDirectory, file));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            final ImageView iconView;
            final TextView pathView;

            ViewHolder(View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.item_icon);
                pathView = itemView.findViewById(R.id.item_path);
            }
        }
    }
}
