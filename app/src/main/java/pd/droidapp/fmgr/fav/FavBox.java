package pd.droidapp.fmgr.fav;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.util.Consumer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.EditPopup;

public class FavBox {

    private final Context context;
    private final FavItemStore favItemStore;
    private final FavItemAdapter favItemAdapter;

    private final LinearLayout favLayout;
    private final ImageView favTrangle;
    private final TextView favTitle;
    private final RecyclerView favItemListView;

    private final EditPopup editPopup;
    private boolean isFavItemListViewInvisible = false;

    private Consumer<File> onClickedFavItemListener;

    public FavBox(Context context, View containerView) {
        this.context = context;
        favItemStore = new FavItemStore(context);
        favItemAdapter = new FavItemAdapter();
        favItemAdapter.whenBindFavItemView(this::bindFavItemView);

        favLayout = containerView.findViewById(R.id.favorites_layout);

        favTrangle = containerView.findViewById(R.id.favorites_trangle);
        favTrangle.setOnClickListener(v -> toggleFavItemListView());

        favTitle = containerView.findViewById(R.id.favorites_title);

        favItemListView = containerView.findViewById(R.id.favorites_list);
        favItemListView.setLayoutManager(new LinearLayoutManager(context));

        favItemListView.setAdapter(favItemAdapter);

        editPopup = new EditPopup(context, favLayout);
    }

    public void invalidate() {
        List<FavItem> favItems = favItemStore.getAll();
        if (favItems.isEmpty()) {
            favLayout.setVisibility(View.GONE);
            return;
        }
        favLayout.setVisibility(View.VISIBLE);
        favTitle.setText(context.getString(R.string.home_favorites_title, favItems.size()));
        favItemAdapter.invalidate(favItems);
    }

    private void toggleFavItemListView() {
        isFavItemListViewInvisible = !isFavItemListViewInvisible;
        favItemListView.setVisibility(isFavItemListViewInvisible ? View.GONE : View.VISIBLE);

        // rotate the trangle
        float targetRotation = isFavItemListViewInvisible ? -90f : 0f;
        float currentRotation = favTrangle.getRotation();
        ValueAnimator animator = ValueAnimator.ofFloat(currentRotation, targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float rotation = (float) animation.getAnimatedValue();
            favTrangle.setRotation(rotation);
        });
        animator.start();
    }

    void bindFavItemView(FavItemViewHolder viewHolder, FavItem favItem) {
        viewHolder.nameTextView.setText(favItem.getDisplayName());
        viewHolder.pathTextView.setText(favItem.path);

        viewHolder.favIcon.setImageResource(R.drawable.round_star_24);
        viewHolder.favIcon.setOnClickListener(v -> {
            favItemStore.remove(favItem);
            invalidate();
        });

        viewHolder.nameEditButton.setOnClickListener(v -> editPopup.show(
                context.getString(R.string.edit_favorite_name),
                favItem.getDisplayName(),
                favItem.getDefaultName(),
                newName -> {
                    if (!newName.equals(favItem.getDisplayName())) {
                        favItem.setDisplayName(newName);
                        favItemStore.put(favItem);
                        favItemAdapter.invalidate(favItemStore.getAll());
                    }
                    editPopup.dismiss();
                }));

        viewHolder.itemView.setOnClickListener(v -> {
            File file = new File(favItem.path);
            if (file.exists() && file.isDirectory()) {
                if (onClickedFavItemListener != null) {
                    onClickedFavItemListener.accept(file);
                }
            }
        });
    }

    public void whenClickedFavItem(Consumer<File> onClickedFavItemListener) {
        this.onClickedFavItemListener = onClickedFavItemListener;
    }
}
