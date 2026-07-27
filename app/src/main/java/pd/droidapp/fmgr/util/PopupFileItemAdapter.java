package pd.droidapp.fmgr.util;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import pd.droidapp.fmgr.R;
import pd.util.PathExtension;

public class PopupFileItemAdapter extends RecyclerView.Adapter<PopupFileItemAdapter.ItemViewHolder> {

    private final File startDirectory;
    private final Set<File> selectedFiles;
    final List<File> items = new LinkedList<>();
    private Runnable onItemFileToggled;

    public PopupFileItemAdapter(File startDirectory, Set<File> selectedFiles) {
        this.startDirectory = startDirectory;
        this.selectedFiles = selectedFiles;
    }

    public void whenItemFileToggled(Runnable onItemFileToggled) {
        this.onItemFileToggled = onItemFileToggled;
    }

    public void invalidate(List<File> files) {
        items.clear();
        items.addAll(files);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.popup_file_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder viewHolder, int position) {
        File file = items.get(position);

        if (file.isDirectory()) {
            viewHolder.fileItem.setIcon(R.drawable.i_directory_24);
        } else {
            viewHolder.fileItem.setIcon(R.drawable.i_file_24);
        }

        viewHolder.fileItem.setSelected(selectedFiles.contains(file));

        viewHolder.fileItem.setPath(PathExtension.relativize(startDirectory.getPath(), file.getPath()));

        viewHolder.fileItem.setIndex(position + 1);

        viewHolder.itemView.setOnClickListener(v -> {
            if (!selectedFiles.isEmpty()) {
                toggleSelected(file, position);
            }
        });

        viewHolder.itemView.setOnLongClickListener(v -> {
            toggleSelected(file, position);
            return true;
        });
    }

    private void toggleSelected(File file, int position) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
        } else {
            selectedFiles.add(file);
        }
        notifyItemChanged(position);
        if (onItemFileToggled != null) {
            onItemFileToggled.run();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        final PopupFileItem fileItem;

        ItemViewHolder(View view) {
            super(view);
            fileItem = new PopupFileItem(view);
        }
    }
}
