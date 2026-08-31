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

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class SearchPopup {

    private static final int SEARCH_START_DELAY_IN_MILLISECONDS = 1000;

    private final Context context;
    private final View containerView;
    private final File startDirectory;

    // views
    private final View selfView;
    private final PopupWindow selfWindow;
    private final LinearLayout mainAreaView;
    private final PopupTitleBar titleBar;
    private final EditText searchEdit;
    private final ImageButton searchEditClearButton;
    private final StatusBar statusBar;
    private final SelectionBar selectionBar;
    private final RecyclerView itemsView;
    private final PopupFileItemsAdapter itemsAdapter;
    private final PopupButtonBar buttonBar;

    // callbacks
    private Consumer<File> onJump;
    private Consumer<Collection<File>> onCopy;
    private Consumer<Collection<File>> onCut;
    private PopupOnDismissListener onDismiss;

    private FileSearchUpdater searcher;
    private String lastQuery = "";
    private int totalScanned;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Collection<File> removedFiles = new LinkedList<>();

    public SearchPopup(View containerView, File startDirectory) {
        this.context = Objects.requireNonNull(containerView, "containerView").getContext();
        this.containerView = containerView;
        this.startDirectory = startDirectory;

        selfView = LayoutInflater.from(context).inflate(
                R.layout.search_popup,
                (ViewGroup) containerView,
                false);
        selfWindow = new PopupWindow(selfView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true) {
            @Override
            public void dismiss() {
                if (searcher != null && !searcher.isStopped()) {
                    return;
                }
                super.dismiss();
            }
        };
        mainAreaView = selfView.findViewById(R.id.popup_area);

        titleBar = new PopupTitleBar(mainAreaView.findViewById(R.id.popup_title_bar));
        searchEdit = mainAreaView.findViewById(R.id.search_edit);
        searchEditClearButton = mainAreaView.findViewById(R.id.search_edit_clear);
        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar));
        selectionBar = new SelectionBar(mainAreaView.findViewById(R.id.selection_bar));
        itemsView = mainAreaView.findViewById(R.id.files_list);
        itemsAdapter = new PopupFileItemsAdapter(startDirectory, selectionBar.selectedItems);
        buttonBar = new PopupButtonBar(mainAreaView.findViewById(R.id.popup_button_bar));

        initPopupWindow();
        enableClosePopupOnOutsideTouch();
        initPopupTitleBar();
        initSearchEdit();
        initSelectionBar();
        initItemsView();
        initPopupButtonBar();
    }

    private void initPopupWindow() {
        selfWindow.setOutsideTouchable(false);
        selfWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        selfWindow.setElevation(24);
        selfWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        selfWindow.setOnDismissListener(() -> {
            stopSearch();
            if (onDismiss != null) {
                onDismiss.accept(removedFiles);
            }
        });
    }

    private void enableClosePopupOnOutsideTouch() {
        selfView.setOnClickListener(v -> selfWindow.dismiss());
        mainAreaView.setOnClickListener(v -> {});
    }

    private void initPopupTitleBar() {
        titleBar.setTitle(R.string.search_files);
        titleBar.whenCloseButtonClicked(v -> selfWindow.dismiss());
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
            if (selectionBar.selectedItems.size() == 1) {
                File file = selectionBar.selectedItems.iterator().next();
                stopSearch();
                if (onJump != null) {
                    onJump.accept(file);
                }
                selfWindow.dismiss();
            }
        });

        selectionBar.addButton(R.layout.selection_button_copy, c -> c > 0, v -> {
            if (onCopy != null) {
                onCopy.accept(selectionBar.copySelectedItems());
            }
        });

        selectionBar.addButton(R.layout.selection_button_cut, c -> c > 0, v -> {
            if (onCut != null) {
                onCut.accept(selectionBar.copySelectedItems());
            }
        });

        selectionBar.addButton(R.layout.selection_button_delete, c -> c > 0, v -> {
            DeletePopup deletePopup = new DeletePopup(containerView, selectionBar.copySelectedItems(), false);
            deletePopup.whenDismissClicked(removed -> {
                removedFiles.addAll(removed);
                itemsAdapter.removeAll(removed);
                selectionBar.selectedItems.removeAll(removed);
                selectionBar.invalidate();
            });
            deletePopup.show();
        });

        selectionBar.addButton(R.layout.selection_button_select_all, c -> c > 0, v -> {
            selectionBar.clear();
            selectionBar.addAll(itemsAdapter.copyItems());
            selectionBar.invalidate();
            itemsAdapter.notifyDataSetChanged();
        });

        selectionBar.addButton(R.layout.selection_button_select_clear, c -> c > 0, v -> {
            List<File> selected = selectionBar.copySelectedItems();
            selectionBar.clear();
            selectionBar.invalidate();
            itemsAdapter.invalidateItems(selected);
        });
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
        itemsAdapter.whenItemFileToggled(selectionBar::invalidate);
    }

    private void initPopupButtonBar() {
        buttonBar.addButton(R.string.start, () -> true, () -> {
            String query = searchEdit.getText().toString();
            return !query.isEmpty() && !query.equals(lastQuery);
        }, v -> {
            handler.removeCallbacks(this::doSearch);
            doSearch();
        });
        buttonBar.addButton(R.string.abort, () -> true, this::isSearching, v -> {
            if (searcher != null) {
                searcher.cancel();
            }
        });
        buttonBar.addButton(R.string.close, () -> true, () -> !isSearching(), v -> selfWindow.dismiss());
        updateButtons();
    }

    private void updateButtons() {
        buttonBar.invalidate();
        titleBar.enableCloseButton(!isSearching());
    }

    private boolean isSearching() {
        return searcher != null && !searcher.isStopped();
    }

    private void stopSearch() {
        handler.removeCallbacks(this::doSearch);
        if (searcher != null) {
            searcher.cancel();
            searcher = null; // late callbacks are dropped by the guards
        }
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

    public void whenDismissClicked(PopupOnDismissListener onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void show() {
        containerView.post(() -> {
            selfWindow.showAtLocation(containerView, Gravity.NO_GRAVITY, 0, 0);
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

    private void doSearch() {
        String query = searchEdit.getText().toString();
        if (query.equals(lastQuery)) {
            return;
        }

        if (searcher != null) {
            searcher.cancel();
            searcher = null;
        }
        clear();
        lastQuery = query;
        if (!query.isEmpty()) {
            totalScanned = 0;
            searcher = createAndStartSearcher(startDirectory.getPath(), query);
        }
        updateButtons();
    }

    private FileSearchUpdater createAndStartSearcher(String startDirectory, String query) {
        FileSearchUpdater current = new FileSearchUpdater(); // the guard
        current.whenSearchStarted(() -> containerView.post(() -> {
            if (searcher != current) {
                return;
            }
            statusBar.markRunning();
            statusBar.setText(context.getString(R.string.search_status_searching));
        }));
        current.whenSearchUpdated((scanned, matched) -> containerView.post(() -> {
            if (searcher != current) {
                return;
            }
            totalScanned += scanned;
            itemsAdapter.addAll(matched);
            statusBar.setText(context.getString(R.string.x_scanned_y_found,
                    totalScanned, itemsAdapter.getItemCount()));
        }));
        current.whenSearchStopped(() -> containerView.post(() -> {
            if (searcher != current) {
                return;
            }
            updateButtons();
            if (current.isCompleted()) {
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

    private void clear() {
        statusBar.hide();
        selectionBar.clear();
        selectionBar.invalidate();
        itemsAdapter.clear();
    }
}
