package pd.droidapp.fmgr.popup;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.repository.ListDirectoryWorker;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.PopupBottomBar;
import pd.droidapp.fmgr.view.PopupTitleBar;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.State;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;

public class PickDirectoryPopup extends ProcessingPopup {

    private static final String CAPPING_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

    private String targetDirectory;
    private ListDirectoryWorker worker;

    // views
    private final StatusBar statusBar;
    private final RecyclerView itemsView;
    private final DirectoryAdapter itemsAdapter;

    // callbacks
    private Consumer<String> onDirectorySelected;

    public PickDirectoryPopup(View containerView, String startDirectory) {
        super(containerView);
        targetDirectory = startDirectory;

        // find views
        statusBar = new StatusBar(contentView.findViewById(R.id.status_bar), R.drawable.ic_directory_24);
        itemsView = contentView.findViewById(R.id.popup_items_list);
        itemsAdapter = new DirectoryAdapter();

        // init views
        bottomBar.whenButtonClicked(id -> {
            if (id == R.string.select) {
                if (onDirectorySelected != null) {
                    onDirectorySelected.accept(targetDirectory);
                }
                selfWindow.dismiss();
            }
        });
        initStatusBar();
        initItemsView();
    }

    @Override
    protected void inflateContent() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) areaView.getLayoutParams();
        params.height = context.getResources().getDimensionPixelSize(R.dimen.directory_picker_popup_height);
        areaView.setLayoutParams(params);

        LinearLayout.LayoutParams contentParams = (LinearLayout.LayoutParams) contentView.getLayoutParams();
        contentParams.height = 0;
        contentParams.weight = 1;
        contentView.setLayoutParams(contentParams);
        LayoutInflater.from(context).inflate(R.layout.directory_picker_popup_content, contentView, true);
    }

    private void initStatusBar() {
        statusBar.whenButtonClicked(id -> {
            if (id == R.drawable.ic_arrow_up_24) {
                changeDirectory(PathOps.singleton.dirname(targetDirectory));
            }
        });
    }

    private void initItemsView() {
        itemsAdapter.setOnItemClicked(this::changeDirectory);
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsView.setAdapter(itemsAdapter);
    }

    private void changeDirectory(String directory) {
        targetDirectory = directory;
        renderStatusBar(StatusBar.IconStatus.RUNNING);

        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        itemsAdapter.clear();

        List<FileProperties> listed = new LinkedList<>();
        ListDirectoryWorker current = new ListDirectoryWorker(); // the guard
        current.whenUpdated(items -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            for (FileProperties item : items) {
                if (item.isDirectory) {
                    listed.add(item);
                }
            }
        }));
        current.whenStopped(reason -> containerView.post(() -> {
            if (worker != current) {
                return;
            }
            renderStatusBar(StatusBar.IconStatus.IDLE);
            itemsAdapter.set(listed);
        }));
        worker = current;
        current.start(directory);
    }

    private void renderStatusBar(StatusBar.IconStatus iconStatus) {
        statusBar.render(new State(iconStatus,
                getDisplayPath(targetDirectory),
                new ButtonState(R.drawable.ic_arrow_up_24, true, !CAPPING_DIRECTORY.equals(targetDirectory)
                        && !PathOps.singleton.dirname(targetDirectory).equals(targetDirectory))));
    }

    public void whenDirectorySelected(Consumer<String> onDirectorySelected) {
        this.onDirectorySelected = onDirectorySelected;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        if (worker != null) {
            worker.cancel();
            worker = null; // late callbacks are dropped by the guards
        }
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
    }

    @Override
    protected void onShow() {
        titleBar.render(new PopupTitleBar.State(context.getString(R.string.select_directory)));
        bottomBar.render(new PopupBottomBar.State(ButtonState.ofText(R.string.select, context.getString(R.string.select))));

        changeDirectory(targetDirectory);
    }

    private static class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ItemViewHolder> {

        private final Comparator<FileProperties> itemComparator = (p1, p2) -> PathOps.singleton.compare(p1.path, p2.path);

        private final List<FileProperties> items = new ArrayList<>();
        private Consumer<String> onItemClicked;

        public void setOnItemClicked(Consumer<String> onItemClicked) {
            this.onItemClicked = onItemClicked;
        }

        public void clear() {
            items.clear();
            notifyDataSetChanged();
        }

        public void set(Collection<FileProperties> newItems) {
            items.clear();
            items.addAll(newItems);
            items.sort(itemComparator);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.file_item, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
            FileProperties item = items.get(position);
            viewHolder.itemPathTextView.setText(PathOps.singleton.basename(item.path));
            viewHolder.itemView.setOnClickListener(v -> onItemClicked.accept(item.path));
            forwardViewActionsTo(viewHolder.itemPathTextView, viewHolder.itemView);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {

            final TextView itemPathTextView;

            ItemViewHolder(View view) {
                super(view);
                itemPathTextView = view.findViewById(R.id.item_name);
            }
        }
    }
}
