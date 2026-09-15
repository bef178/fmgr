package pd.droidapp.fmgr.popup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.SelectionBar;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.animateCollapsed;
import static pd.droidapp.fmgr.util.Util.getSizeString;

class PopupFileGroupsAdapter extends RecyclerView.Adapter<PopupFileGroupsAdapter.FileGroupViewHolder> {

    private final String startDirectory;
    private final SelectionBar selectionBar;
    private final List<PopupFileGroup> groups = new ArrayList<>();
    private final Map<String, Boolean> collapsedStates = new HashMap<>();
    private int[] startIndexes = new int[0];

    public PopupFileGroupsAdapter(String startDirectory, SelectionBar selectionBar) {
        this.startDirectory = startDirectory;
        this.selectionBar = selectionBar;
    }

    public void load(List<PopupFileGroup> newGroups) {
        List<PopupFileGroup> oldGroups = new ArrayList<>(groups);
        int[] oldStartIndexes = startIndexes;
        groups.clear();
        groups.addAll(newGroups);
        startIndexes = calculateStartIndexes(groups);
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldGroups.size();
            }

            @Override
            public int getNewListSize() {
                return groups.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldGroups.get(oldPos).key().equals(groups.get(newPos).key());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldStartIndexes[oldPos] == startIndexes[newPos]
                        && oldGroups.get(oldPos).getPaths().size() == groups.get(newPos).getPaths().size();
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                return Boolean.TRUE;
            }
        }).dispatchUpdatesTo(this);
    }

    private int[] calculateStartIndexes(List<PopupFileGroup> groups) {
        int[] indexes = new int[groups.size()];
        int index = 1;
        for (int i = 0; i < groups.size(); i++) {
            indexes[i] = index;
            index += groups.get(i).getPaths().size();
        }
        return indexes;
    }

    public List<PopupFileGroup> getGroups() {
        return groups;
    }

    public List<String> getSelectedPaths() {
        List<String> selected = new LinkedList<>();
        for (PopupFileGroup group : groups) {
            for (String path : group.getPaths()) {
                if (selectionBar.hasSelected(path)) {
                    selected.add(path);
                }
            }
        }
        return selected;
    }

    @NonNull
    @Override
    public FileGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View groupView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dedup_group, parent, false);
        return new FileGroupViewHolder(groupView);
    }

    @Override
    public void onBindViewHolder(@NonNull FileGroupViewHolder viewHolder, int position) {
        PopupFileGroup group = groups.get(position);
        List<String> paths = group.getPaths();

        Context context = viewHolder.itemView.getContext();
        viewHolder.titleTextView.setText(context.getString(R.string.x_files_y_each, paths.size(), getSizeString(group.size)));
        boolean collapsed = isCollapsed(group);
        viewHolder.triangleImageView.setRotation(collapsed ? -90f : 0f);
        viewHolder.filesView.setVisibility(collapsed ? View.GONE : View.VISIBLE);

        viewHolder.titleBarView.setOnClickListener(v -> {
            collapsedStates.put(group.key(), !isCollapsed(group));
            animateCollapsed(viewHolder.triangleImageView, viewHolder.filesView, isCollapsed(group));
        });

        int nowCount = viewHolder.filesView.getChildCount();
        int requiredCount = paths.size();

        int startIndex = startIndexes[position];

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        for (int i = 0; i < requiredCount; i++) {
            String path = paths.get(i);
            View fileView;

            if (i < nowCount) {
                fileView = viewHolder.filesView.getChildAt(i);
            } else {
                fileView = layoutInflater.inflate(R.layout.popup_file_item, viewHolder.filesView, false);
                viewHolder.filesView.addView(fileView);
            }

            PopupFileItemBar itemBar = new PopupFileItemBar(fileView);
            itemBar.setIndex(startIndex + i);
            itemBar.forwardPathViewClicksTo(fileView);
            itemBar.setIcon(R.drawable.i_file_24);
            itemBar.setPath(PathOps.singleton.relativize(startDirectory, path));
            itemBar.setSelected(selectionBar.hasSelected(path));

            fileView.setOnClickListener(v -> {
                if (!selectionBar.isEmpty()) {
                    toggleSelected(path, position);
                }
            });
            fileView.setOnLongClickListener(v -> {
                toggleSelected(path, position);
                return true;
            });
        }
        if (nowCount > requiredCount) {
            viewHolder.filesView.removeViews(requiredCount, nowCount - requiredCount);
        }
    }

    private boolean isCollapsed(PopupFileGroup group) {
        return collapsedStates.getOrDefault(group.key(), false);
    }

    private void toggleSelected(String path, int position) {
        selectionBar.toggleSelected(path);
        notifyItemChanged(position);
        selectionBar.invalidate();
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class FileGroupViewHolder extends RecyclerView.ViewHolder {

        final View titleBarView;
        final ImageView triangleImageView;
        final TextView titleTextView;
        final LinearLayout filesView;

        FileGroupViewHolder(View view) {
            super(view);
            titleBarView = view.findViewById(R.id.group_title_bar);
            triangleImageView = view.findViewById(R.id.group_triangle);
            titleTextView = view.findViewById(R.id.group_title);
            filesView = view.findViewById(R.id.group_files);
        }
    }

    static class PopupFileGroup {

        public final long size;
        public final String sha256sum;
        private final List<String> paths;

        PopupFileGroup(long size, String sha256sum, List<String> paths) {
            this.size = size;
            this.sha256sum = sha256sum;
            this.paths = new LinkedList<>(paths);
        }

        public List<String> getPaths() {
            return paths;
        }

        public String key() {
            return sha256sum;
        }
    }
}
