package pd.droidapp.fmgr.popup;

import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.view.StatusBar;
import pd.droidapp.fmgr.view.StatusBar.IconState;
import pd.droidapp.fmgr.view.StatusBar.State;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.forwardViewActionsTo;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.toFileProperties;

public class DirectoryPickerPopup extends ProcessingPopup {

    private static final String CAPPING_DIRECTORY = Environment.getExternalStorageDirectory().getPath();

    // views
    private final StatusBar statusBar;
    private final RecyclerView itemsView;
    private final DirectoryAdapter itemsAdapter;

    // callbacks
    private Consumer<String> onDirectorySelected;

    private String targetDirectory;

    public DirectoryPickerPopup(View containerView, String startDirectory) {
        super(containerView, R.layout.directory_picker_popup);
        targetDirectory = startDirectory;

        statusBar = new StatusBar(mainAreaView.findViewById(R.id.status_bar), R.drawable.i_directory_24);
        itemsView = mainAreaView.findViewById(R.id.popup_items_list);
        itemsAdapter = new DirectoryAdapter();

        titleBar.setTitle(R.string.select_directory);

        initStatusBar();
        initItemsView();
    }

    @Override
    protected void initPopupButtons() {
        super.initPopupButtons();
        buttonBar.addButton(R.string.select, () -> true, () -> true, v -> {
            if (onDirectorySelected != null) {
                onDirectorySelected.accept(targetDirectory);
            }
            selfWindow.dismiss();
        });
    }

    private void initStatusBar() {
        statusBar.whenButtonClicked(id -> {
            if (id == R.drawable.action_up) {
                goUp();
            }
        });
    }

    private boolean canGoUp() {
        return !CAPPING_DIRECTORY.equals(targetDirectory)
                && !PathOps.singleton.dirname(targetDirectory).equals(targetDirectory);
    }

    private void goUp() {
        changeDirectory(PathOps.singleton.dirname(targetDirectory));
    }

    private void initItemsView() {
        itemsView.setLayoutManager(new LinearLayoutManager(context));
        itemsAdapter.setOnItemClicked(this::changeDirectory);
        itemsView.setAdapter(itemsAdapter);
    }

    public void whenDirectorySelected(Consumer<String> onDirectorySelected) {
        this.onDirectorySelected = onDirectorySelected;
    }

    private void changeDirectory(String directory) {
        targetDirectory = directory;
        statusBar.render(new State(IconState.IDLE,
                getDisplayPath(directory),
                new ButtonState(R.drawable.action_up, true, canGoUp())));

        List<String> paths = new LinkedList<>();
        FileOps.singleton.listDirectory(directory, 1, false, null,
                (action, src, dst, succeeded) -> {
                    if (action == FileOps.Action.MEET) {
                        paths.add(src);
                    }
                });
        itemsAdapter.set(toFileProperties(paths).stream()
                .filter(item -> item.isDirectory)
                .collect(Collectors.toList()));
    }

    @Override
    protected boolean isProcessing() {
        return false;
    }

    @Override
    protected void onDismissing(Runnable continueDismiss) {
        continueDismiss.run();
    }

    @Override
    protected void onDismissed() {
    }

    @Override
    protected void onShow() {
        changeDirectory(targetDirectory);
    }

    private static class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.ItemViewHolder> {

        private final List<FileProperties> items = new ArrayList<>();
        private Consumer<String> onItemClicked;

        public void setOnItemClicked(Consumer<String> onItemClicked) {
            this.onItemClicked = onItemClicked;
        }

        public void set(List<FileProperties> newItems) {
            List<FileProperties> oldItems = new ArrayList<>(items);
            items.clear();
            items.addAll(newItems);
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return oldItems.size();
                }

                @Override
                public int getNewListSize() {
                    return items.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return oldItems.get(oldItemPosition).path.equals(items.get(newItemPosition).path);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    // a row shows the basename, which its path determines
                    return true;
                }
            }).dispatchUpdatesTo(this);
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
