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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class SearchPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;

    private final Context context;
    private final View containerView;
    private final File startDirectory;
    private Consumer<File> onJump;
    private Consumer<Collection<File>> onCopy;
    private Consumer<Collection<File>> onCut;
    private Consumer<Collection<File>> onDelete;

    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final PopupFileItemAdapter itemAdapter;
    private final PopupWindow popupWindow;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::doSearch;
    private Scanner scanner;
    private String lastQuery = "";

    public SearchPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        View popupView = LayoutInflater.from(context).inflate(
                R.layout.search_popup,
                (ViewGroup) containerView,
                false);

        LinearLayout popupArea = popupView.findViewById(R.id.popup_area);
        searchEdit = popupView.findViewById(R.id.search_edit);
        searchEditClearButton = popupView.findViewById(R.id.search_edit_clear);
        PopupTitleBar titleBar = new PopupTitleBar(popupView.findViewById(R.id.popup_title_bar));
        titleBar.setTitle(R.string.search_files);
        titleBar.whenCloseButtonClicked(v -> dismiss());
        statusBar = new StatusBar(popupView.findViewById(R.id.status_bar));

        selectionBar = new SelectionBar(popupView.findViewById(R.id.selection_bar));

        RecyclerView searchResultItemsList = popupView.findViewById(R.id.files_list);

        itemAdapter = new PopupFileItemAdapter(startDirectory, selectionBar.selectedFiles);
        itemAdapter.whenItemFileToggled(selectionBar::invalidate);
        searchResultItemsList.setLayoutManager(new LinearLayoutManager(context));
        searchResultItemsList.setAdapter(itemAdapter);


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
                searchEditClearButton.setEnabled(!s.toString().isEmpty());
                handler.postDelayed(searchRunnable, SEARCH_START_DELAY_IN_MILLISECONDS);
            }
        });

        searchEditClearButton.setOnClickListener(v -> {
            searchEdit.setText("");
            handler.removeCallbacks(searchRunnable);
            doSearch();
        });

        searchEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(searchRunnable);
                doSearch();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

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
                onDelete.accept(selected);
                itemAdapter.removeAll(selected);
            }
            clearSelection();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addAll(itemAdapter.copyItems());
            selectionBar.invalidate();
            itemAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> clearSelection());

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
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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

    public void whenDeleteClicked(Consumer<Collection<File>> onDelete) {
        this.onDelete = onDelete;
    }

    public void show() {
        containerView.post(() -> {
            popupWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
            searchEdit.requestFocus();
            searchEditClearButton.setEnabled(false);
            searchEdit.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);
        });
    }

    public void dismiss() {
        cancelSearch();
        popupWindow.dismiss();
    }

    private void doSearch() {
        String query = searchEdit.getText().toString();
        if (query.isEmpty()) {
            cancelSearch();
            clearSelection();
            itemAdapter.clear();
            lastQuery = "";
            statusBar.hide();
            return;
        }

        if (query.equals(lastQuery)) {
            return;
        }

        cancelSearch();
        clearSelection();
        itemAdapter.clear();

        lastQuery = query;
        scanner = new Scanner();
        scanner.start(query);
    }

    private void cancelSearch() {
        if (scanner != null) {
            scanner.cancel();
            scanner = null;
        }
    }

    private void clearSelection() {
        selectionBar.clear();
        selectionBar.invalidate();
        itemAdapter.notifyDataSetChanged();
    }

    class Scanner {

        private String query;
        private FileScanUpdater nameScanner;
        private int nameScanned;
        private FileScanUpdater contentScanner;
        private int contentScanned;
        private final Set<String> results = Collections.synchronizedSet(new HashSet<>());

        void start(String query) {
            this.query = query;
            results.clear();

            nameScanner = new FileScanUpdater();
            nameScanner.whenDirectoryReached(directory -> {
                if (directory.getName().contains(Scanner.this.query)) {
                    results.add(directory.getPath());
                    return true;
                }
                return false;
            });
            nameScanner.whenFileReached(file -> {
                if (file.getName().contains(Scanner.this.query)) {
                    results.add(file.getPath());
                    return true;
                }
                return false;
            });
            nameScanner.whenScanStarted(() -> containerView.post(() -> {
                statusBar.markRunning();
                statusBar.setText(context.getString(R.string.search_status_searching));
            }));
            nameScanner.whenScanUpdated((delta, scanned) -> containerView.post(() -> {
                nameScanned += scanned;
                itemAdapter.addAll(delta);
                statusBar.setText(context.getString(R.string.search_status_searching));
            }));
            nameScanner.whenScanStopped(() -> containerView.post(() -> {
                if (nameScanner.isCompleted()) {
                    scanContent();
                }
            }));
            nameScanner.start(startDirectory);
        }

        private void scanContent() {
            contentScanner = new FileScanUpdater();
            contentScanner.whenFileReached(file -> {
                if (!results.contains(file.getPath())
                        && (isTextFile(file) || isSmallAnonymousFile(file))
                        && fileContainsText(file, query)) {
                    results.add(file.getPath());
                    return true;
                }
                return false;
            });
            contentScanner.whenScanUpdated((delta, scanned) -> containerView.post(() -> {
                contentScanned += scanned;
                itemAdapter.addAll(delta);
                statusBar.setText(context.getString(R.string.x_scanned_y_found,
                        nameScanned + contentScanned, itemAdapter.getItemCount()));
            }));
            contentScanner.whenScanStopped(() -> containerView.post(() -> {
                if (contentScanner.isCompleted()) {
                    statusBar.markDone();
                }
            }));
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
}
