package pd.droidapp.fmgr.popup;

import android.animation.LayoutTransition;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.Collections;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.repository.ProcessingWorker.StopReason;
import pd.droidapp.fmgr.repository.RenameWorker;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.FileItemAdapter;
import pd.droidapp.fmgr.view.FileItemBar;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.checkBasename;
import static pd.droidapp.fmgr.util.FileIconUtil.getDrawableId;
import static pd.droidapp.fmgr.view.FileItemBar.ProcessingStatus;

/**
 * Renaming a large directory might take a very long time:
 * The FUSE daemon updates MediaStore by scanning the entire tree, which cannot be canceled.
 * So it is built as a progressing popup, rather than a quick-done edit popup.
 */
public class RenamePopup extends ProcessingPopup {

    private final String srcItem;
    private final boolean isDirectory;
    private final String mimeType;

    // views
    private final StatusBar statusBar;
    private final HorizontalScrollView targetNameScrollView;
    private final TextView targetNameTextView;
    private final EditText targetNameEditView;
    private final RecyclerView itemsView;
    private final FileItemAdapter itemAdapter;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    private RenameWorker worker;
    private String netAdded;

    public RenamePopup(View containerView, FileProperties item) {
        super(containerView);
        srcItem = item.path;
        isDirectory = item.isDirectory;
        mimeType = item.mimeType;

        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ia_rename_24);
        targetNameScrollView = contentView.findViewById(R.id.target_name_scroll);
        targetNameTextView = contentView.findViewById(R.id.target_name);
        targetNameEditView = contentView.findViewById(R.id.target_name_edit);
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemAdapter = new FileItemAdapter();

        bottomBar.whenButtonClicked(id -> {
            if (id == R.id.action_start) {
                start();
            } else if (id == R.id.action_close) {
                selfDialog.dismiss();
            }
        });

        initNameEdit();
        initItemsView();
    }

    @Override
    protected void inflateContentView() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        params.width = context.getResources().getDimensionPixelSize(R.dimen.processing_popup_width);
        params.height = context.getResources().getDimensionPixelSize(R.dimen.rename_popup_content_height);
        contentView.setLayoutParams(params);
        LayoutInflater.from(context).inflate(R.layout.rename_popup_content, contentView, true);
    }

    @Override
    protected void setupDialogWindow(@NonNull Window window) {
        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.getAttributes().y = context.getResources().getDimensionPixelSize(R.dimen.rename_popup_top_margin);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemAdapter);
        RecyclerView.ItemAnimator itemAnimator = itemsView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
    }

    private void initNameEdit() {
        targetNameEditView.setText(suggestNewName(PathOps.singleton.basename(srcItem)));
        targetNameEditView.setHint(PathOps.singleton.basename(srcItem));
        targetNameEditView.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderBottomBar();
            }
        });

        targetNameEditView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                start();
                return true;
            }
            return false;
        });
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected boolean canDismiss() {
        return worker == null;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed != null) {
            if (netAdded == null) {
                onPopupDismissed.accept(Collections.emptyList(), Collections.emptyList());
            } else {
                onPopupDismissed.accept(
                        Collections.singletonList(new FileProperties(netAdded, isDirectory)),
                        Collections.singletonList(new FileProperties(srcItem, isDirectory)));
            }
        }
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.rename)));
        renderStatusBar(StatusBar.IconStatus.IDLE);
        renderBottomBar();
        renderItems();

        targetNameEditView.requestFocus();
    }

    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        if (iconStatus == StatusBar.IconStatus.IDLE) {
            statusBar.render(new StatusBar.State(iconStatus, context.getString(R.string.x_selected, 1)));
            return;
        }
        statusBar.render(new StatusBar.State(iconStatus,
                context.getString(iconStatus == StatusBar.IconStatus.RUNNING ? R.string.renaming : R.string.renamed)));
    }

    private void renderBottomBar() {
        bottomBar.render(new PopupBottomBar.State(
                new ButtonState(R.id.action_start, R.drawable.ic_checkmark_24, worker == null && netAdded == null, canStart()),
                new ButtonState(R.id.action_abort, R.drawable.ic_blockmark_24, worker != null, false),
                new ButtonState(R.id.action_close, R.drawable.ic_xmark_24, netAdded != null, true)));
    }

    private void renderItems() {
        ProcessingStatus badgeState;
        if (netAdded != null) {
            badgeState = ProcessingStatus.COMPLETED;
        } else if (worker != null) {
            badgeState = ProcessingStatus.RUNNING;
        } else {
            badgeState = ProcessingStatus.NONE;
        }
        itemAdapter.render(Collections.singletonList(new FileItemBar.State(
                srcItem,
                PathOps.singleton.basename(srcItem),
                null,
                "1",
                getDrawableId(context, srcItem, isDirectory, mimeType),
                true,
                badgeState)));
    }

    private boolean canStart() {
        return !targetNameEditView.getText().toString().trim().isEmpty();
    }

    private void start() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(targetNameEditView.getWindowToken(), 0);
        }

        String newName = targetNameEditView.getText().toString().trim();
        if (newName.equals(PathOps.singleton.basename(srcItem))) {
            Toast.makeText(context, context.getString(R.string.error_same_as_old_name), Toast.LENGTH_SHORT).show();
            return;
        }

        Integer errorTextId = checkBasename(newName);
        if (errorTextId != null) {
            Toast.makeText(context, errorTextId, Toast.LENGTH_SHORT).show();
            return;
        }

        String dst = PathOps.singleton.resolve(PathOps.singleton.dirname(srcItem), newName);
        if (FileOps.singleton.stat(dst).exists(false)) {
            Toast.makeText(context, R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return;
        }

        if (worker != null) {
            return;
        }

        worker = new RenameWorker();
        LayoutTransition collapseTransition = createTargetNameEditCollapseTransition(PathOps.singleton.basename(dst));
        ((ViewGroup) targetNameEditView.getParent()).setLayoutTransition(collapseTransition);
        targetNameEditView.setVisibility(View.GONE);
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.rename)).copyWithButtonEnabled(false));
        renderBottomBar();

        worker.whenStarted(() -> containerView.post(() -> {
            renderStatusBar(StatusBar.IconStatus.RUNNING);
            renderItems();
        }));
        worker.whenStopped(reason -> containerView.post(() -> {
            worker = null;
            titleBar.render(new PopupTitleBar.State(context.getString(R.string.rename)));
            if (reason == StopReason.COMPLETED) {
                Toast.makeText(context, R.string.renamed, Toast.LENGTH_SHORT).show();
                netAdded = dst;
                renderStatusBar(StatusBar.IconStatus.COMPLETED);
                renderBottomBar();
                renderItems();
                return;
            }
            Toast.makeText(context, R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            targetNameEditView.setVisibility(View.VISIBLE);
            targetNameScrollView.setVisibility(View.GONE);
            renderStatusBar(StatusBar.IconStatus.IDLE);
            renderBottomBar();
            renderItems();
        }));
        worker.start(srcItem, dst);
    }

    private LayoutTransition createTargetNameEditCollapseTransition(CharSequence targetName) {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(150);
        // there's 300ms start delay for APPEARING by default
        layoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                if (transitionType == LayoutTransition.DISAPPEARING && view == targetNameEditView) {
                    targetNameTextView.setText(targetName);
                    targetNameScrollView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
            }
        });
        return layoutTransition;
    }

    private static String suggestNewName(String name) {
        {
            String name1 = name.trim();
            if (name1.isEmpty()) {
                return name;
            }
            name = name1;
        }

        String extension = PathOps.singleton.extname(name);
        String core = PathOps.singleton.basename(name, extension);

        {
            String core1 = core.replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1");
            if (!core1.isEmpty()) {
                core = core1;
            }
        }

        if (!extension.isEmpty()) {
            String ext1 = extension
                    .substring(1)
                    .replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1")
                    .trim();
            if (!ext1.isEmpty()) {
                extension = "." + ext1;
            }
        }
        return core + extension;
    }
}
