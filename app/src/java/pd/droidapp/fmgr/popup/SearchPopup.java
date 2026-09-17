package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.popup.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.util.SelectionBar;

public class SearchPopup extends ProcessingPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;

    private final String startDirectory;

    // views
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;

    // callbacks
    private Consumer<String> onJump;
    private Consumer<Collection<FileProperties>> onCopy;
    private Consumer<Collection<FileProperties>> onCut;
    private PopupOnDismissedListener onPopupDismissed;

    private SearchWorker worker;
    private String lastQuery = "";
    private int totalScanned;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Collection<FileProperties> netRemoved = new LinkedList<>();

    public SearchPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.search_popup);
        this.startDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        searchEdit = mainAreaView.findViewById(R.id.search_edit);
        searchEditClearButton = mainAreaView.findViewById(R.id.search_edit_clear);
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar);

        titleBar.setTitle(R.string.search);

        initStatusBar();
        initSelectionBar();
        initSearchEdit();
        initItemsView();
    }

    @Override
    protected void initPopupWindow() {
        super.initPopupWindow();
        selfWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.abort, () -> worker == null || isProcessing(), () -> isProcessing() && !worker.isCancelled(), v -> {
            if (worker != null) {
                worker.cancel();
            }
            updateButtons();
        });
        buttonBar.addButton(R.string.close, () -> worker != null && !worker.isWorking(), () -> true, v -> selfWindow.dismiss());
    }

    private void initStatusBar() {
        statusBar.markReady(R.drawable.baseline_search_24);
        statusBar.setText(context.getString(R.string.status_find_and_grep));
    }

    private void initSearchEdit() {
        searchEdit.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchEditClearButton.setEnabled(!s.toString().isEmpty());
                updateButtons();
                handler.removeCallbacks(SearchPopup.this::doSearch);
                handler.postDelayed(SearchPopup.this::doSearch, SEARCH_START_DELAY_IN_MILLISECONDS);
            }
        });

        searchEditClearButton.setOnClickListener(v -> {
            searchEdit.setText("");
            handler.removeCallbacks(SearchPopup.this::doSearch);
            doSearch();
        });

        searchEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(SearchPopup.this::doSearch);
                doSearch();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
    }

    private void initSelectionBar() {
        selectionBar.addButton(R.layout.selection_button_jump, c -> c == 1, v -> {
            if (selectionBar.size() == 1) {
                if (onJump != null) {
                    onJump.accept(itemsAdapter.getSelectedItems().get(0).path);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> {
            if (onCopy != null) {
                onCopy.accept(itemsAdapter.getSelectedItems());
            }
        });

        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> {
            if (onCut != null) {
                onCut.accept(itemsAdapter.getSelectedItems());
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, itemsAdapter.getSelectedItems(), false);
            deletePopup.whenPopupDismissed((added, removed) -> {
                netRemoved.addAll(removed);
                itemsAdapter.remove(removed);
                selectionBar.removeProps(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addProps(itemsAdapter.getItems());
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
    }

    @Override
    protected boolean isProcessing() {
        return worker != null && worker.isWorking();
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        handler.removeCallbacks(this::doSearch);
        if (worker != null) {
            worker.cancel();
            worker = null; // late callbacks are dropped by the guards
        }
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            onPopupDismissed.accept(Collections.emptyList(), netRemoved);
        }
    }

    public void whenJumpClicked(Consumer<String> onJump) {
        this.onJump = onJump;
    }

    public void whenCopyClicked(Consumer<Collection<FileProperties>> onCopy) {
        this.onCopy = onCopy;
    }

    public void whenCutClicked(Consumer<Collection<FileProperties>> onCut) {
        this.onCut = onCut;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onShow() {
        searchEdit.requestFocus();
        searchEditClearButton.setEnabled(false);
        searchEdit.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEdit, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }

    private void doSearch() {
        String query = searchEdit.getText().toString();
        if (query.equals(lastQuery)) {
            return;
        }

        lastQuery = query;

        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        totalScanned = 0;
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.clear();

        if (!query.isEmpty()) {
            worker = createAndStartSearcher(startDirectory, query);
        } else {
            initStatusBar();
        }
        updateButtons();
    }

    private SearchWorker createAndStartSearcher(String startDirectory, String query) {
        SearchWorker current = new SearchWorker(); // the guard
        current.whenStarted(() -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.status_searching));
        }));
        current.whenUpdated((scanned, matched) -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            totalScanned += scanned;
            itemsAdapter.append(matched);
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemsAdapter.getItemCount()));
        }));
        current.whenStopped(reason -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            updateButtons();
            if (reason == StopReason.COMPLETED) {
                statusBar.markDone();
            } else {
                statusBar.markStopped();
            }
        }));
        if (current.start(startDirectory, query)) {
            return current;
        }
        return null;
    }
}
