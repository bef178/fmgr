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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class SearchPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<File> onFileClicked;
    private Consumer<Collection<File>> onDelete;

    private final PopupWindow popupWindow;
    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final ImageButton closeButton;
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final SearchResultAdapter searchResultAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::doSearch;
    private Scanner scanner;

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
        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(context, popupView.findViewById(R.id.selection_bar));

        RecyclerView searchResultItemsList = popupView.findViewById(R.id.files_list);

        searchResultAdapter = new SearchResultAdapter(context, startDirectory, selectionBar.selectedFiles);
        searchResultAdapter.whenSelectionChanged(selectionBar::invalidate);
        searchResultItemsList.setLayoutManager(new LinearLayoutManager(context));
        searchResultItemsList.setAdapter(searchResultAdapter);

        closeButton.setOnClickListener(v -> dismiss());

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
                    statusBar.hide();
                } else {
                    closeButton.setVisibility(View.GONE);
                    searchEditClearButton.setVisibility(View.VISIBLE);
                    clearResults();
                    statusBar.hide();
                    handler.postDelayed(searchRunnable, SEARCH_START_DELAY_IN_MILLISECONDS);
                }
            }
        });

        searchEditClearButton.setOnClickListener(v -> {
            searchEdit.setText("");
        });

        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.selectedFiles.size() == 1) {
                File file = selectionBar.selectedFiles.iterator().next();
                if (onFileClicked != null) {
                    onFileClicked.accept(file);
                }
                dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            if (onDelete != null) {
                onDelete.accept(selectionBar.copySelectedFiles());
            }
            dismiss();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addAll(searchResultAdapter.results);
            selectionBar.invalidate();
            searchResultAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> clearSelection());

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

        scanner = new Scanner();
        scanner.start(query);
    }

    private void cancelSearch() {
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
        }
    }

    private void clearResults() {
        searchResultAdapter.results.clear();
        searchResultAdapter.notifyDataSetChanged();
    }

    private void clearSelection() {
        selectionBar.clear();
        selectionBar.invalidate();
        searchResultAdapter.notifyDataSetChanged();
    }

    class Scanner {

        private String query;
        private FileScanner nameScanner;
        private FileScanner contentScanner;
        final List<File> results = Collections.synchronizedList(new LinkedList<>());

        void start(String query) {
            this.query = query;
            results.clear();

            nameScanner = new FileScanner() {
                @Override
                protected void onFile(File file) {
                    if (file.getName().contains(Scanner.this.query)) {
                        addResult(file);
                    }
                }

                @Override
                protected boolean onDirectory(File directory) {
                    if (directory.getName().contains(Scanner.this.query)) {
                        addResult(directory);
                    }
                    return true;
                }

                @Override
                protected void onScanStarted() {
                    containerView.post(() -> statusBar.markRunning(context.getString(R.string.search_status_searching)));
                }

                @Override
                protected void onScanUpdated(int n) {
                    containerView.post(() -> {
                        searchResultAdapter.invalidate(copyResults());
                        if (isCompleted()) {
                            startContentScan();
                        } else {
                            statusBar.setText(context.getString(R.string.search_status_searching));
                        }
                    });
                }
            };
            nameScanner.start(startDirectory);
        }

        private void startContentScan() {
            int nameScanned = nameScanner.numFilesScanned.get();

            contentScanner = new FileScanner() {
                @Override
                protected void onFile(File file) {
                    if (!isSettled(file)
                            && (isTextFile(file) || isSmallAnonymousFile(file))
                            && fileContainsText(file, query)) {
                        addResult(file);
                    }
                }

                @Override
                protected void onScanUpdated(int n) {
                    containerView.post(() -> {
                        searchResultAdapter.invalidate(copyResults());
                        if (isCompleted()) {
                            statusBar.markDone();
                            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                                    nameScanned + n, results.size()));
                        }
                    });
                }
            };
            contentScanner.start(startDirectory);
        }

        void cancel() {
            if (nameScanner != null) {
                nameScanner.cancel();
            }
            if (contentScanner != null) {
                contentScanner.cancel();
            }
        }

        private void addResult(File result) {
            results.add(result);
        }

        private boolean isSettled(File file) {
            synchronized (results) {
                for (File item : results) {
                    if (item.getPath().equals(file.getPath())) {
                        return true;
                    }
                }
            }
            return false;
        }

        List<File> copyResults() {
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
                    if (line.contains(query)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
            return false;
        }
    }

    static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

        private final Context context;
        private final File startDirectory;
        final Set<File> selectedFiles;
        private final List<File> results = new LinkedList<>();
        private Runnable onSelectionChanged;

        SearchResultAdapter(Context context, File startDirectory, Set<File> selectedFiles) {
            this.context = context;
            this.startDirectory = startDirectory;
            this.selectedFiles = selectedFiles;
        }

        void invalidate(List<File> newResults) {
            results.clear();
            results.addAll(newResults);
            notifyDataSetChanged();
        }

        void whenSelectionChanged(Runnable onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.popup_file, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            File file = results.get(position);

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
