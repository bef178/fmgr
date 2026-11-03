package pd.droidapp.glry;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import pd.droidapp.fmgr.R;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ViewHolder> {

    private List<State> states = new ArrayList<>();
    private RecyclerView recyclerView;
    private BiConsumer<Integer, Boolean> onPageLoaded;
    private int actualSizePosition = -1;

    public void whenPageLoaded(BiConsumer<Integer, Boolean> onPageLoaded) {
        this.onPageLoaded = onPageLoaded;
    }

    public void render(List<State> states) {
        this.states = new ArrayList<>(states);
        actualSizePosition = -1;
        notifyDataSetChanged();
    }

    public void render(ShowActualSizeEvent event) {
        if (recyclerView == null) {
            return;
        }
        ViewHolder holder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(event.position);
        if (holder != null) {
            actualSizePosition = event.position;
            holder.showActualSize(downsampled -> {
                if (downsampled && actualSizePosition == event.position) {
                    actualSizePosition = -1;
                }
                if (onPageLoaded != null) {
                    onPageLoaded.accept(event.position, downsampled);
                }
            });
        }
    }

    public void render(ResetPageEvent event) {
        if (recyclerView == null) {
            return;
        }
        ViewHolder holder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(event.prevPosition);
        if (actualSizePosition == event.prevPosition) {
            actualSizePosition = -1;
            notifyItemChanged(event.prevPosition);
        } else if (holder != null) {
            holder.resetToDefault();
        } else {
            notifyItemChanged(event.prevPosition);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.image_page, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (onPageLoaded != null) {
            onPageLoaded.accept(position, false);
        }
        State state = states.get(position);
        holder.bind(state.path, state.mimeType, downsampled -> {
            if (onPageLoaded != null) {
                onPageLoaded.accept(position, downsampled);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.recycle();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        this.recyclerView = null;
    }

    @Override
    public int getItemCount() {
        return states.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final ZoomableImageView imageView;
        private final ProgressBar progressView;
        private final TextView errorView;
        private String path;
        private String mimeType;
        private int bindingGeneration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.page_image);
            progressView = itemView.findViewById(R.id.page_progress);
            errorView = itemView.findViewById(R.id.page_error);
        }

        void bind(String path, String mimeType, Consumer<Boolean> onLoaded) {
            this.path = path;
            this.mimeType = mimeType;
            int thisGeneration = ++bindingGeneration;
            errorView.setVisibility(View.GONE);
            progressView.setVisibility(View.VISIBLE);
            imageView.clear();
            OneShotPreDrawListener.add(imageView, () -> {
                if (thisGeneration != bindingGeneration) {
                    return;
                }
                ImageManager.singleton.load(path, mimeType, imageView.getWidth(), imageView.getHeight(), drawableEntry ->
                        imageView.post(() -> {
                            if (thisGeneration != bindingGeneration) {
                                return;
                            }
                            progressView.setVisibility(View.GONE);
                            if (drawableEntry == null) {
                                errorView.setVisibility(View.VISIBLE);
                                onLoaded.accept(false);
                                return;
                            }
                            imageView.setDrawable(drawableEntry.drawable);
                            onLoaded.accept(drawableEntry.isDownsampled());
                        }));
            });
        }

        void showActualSize(Consumer<Boolean> onLoaded) {
            int thisGeneration = bindingGeneration;
            progressView.setVisibility(View.VISIBLE);
            ImageManager.singleton.loadActualSize(path, mimeType, drawableEntry ->
                    imageView.post(() -> {
                        if (thisGeneration != bindingGeneration) {
                            return;
                        }
                        progressView.setVisibility(View.GONE);
                        if (drawableEntry == null) {
                            Toast.makeText(imageView.getContext(),
                                    R.string.error_failed_to_load_image, Toast.LENGTH_SHORT).show();
                            onLoaded.accept(true);
                            return;
                        }
                        imageView.setDrawableAtActualSize(drawableEntry.drawable);
                        onLoaded.accept(false);
                    }));
        }

        void resetToDefault() {
            imageView.resetToDefault();
        }

        void recycle() {
            bindingGeneration++;
            path = null;
            mimeType = null;
            imageView.clear();
        }
    }

    public static class State {

        public final String path;
        public final String mimeType;

        public State(String path, String mimeType) {
            this.path = path;
            this.mimeType = mimeType;
        }
    }

    public static class ShowActualSizeEvent {

        public final int position;

        public ShowActualSizeEvent(int position) {
            this.position = position;
        }
    }

    public static class ResetPageEvent {

        public final int prevPosition;

        public ResetPageEvent(int prevPosition) {
            this.prevPosition = prevPosition;
        }
    }
}
