package pd.droidapp.fmgr.fav;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import pd.droidapp.fmgr.R;

public class FavItemViewHolder extends RecyclerView.ViewHolder {

    TextView nameTextView;
    ImageButton nameEditButton;
    TextView pathTextView;
    ImageButton favIcon;

    public FavItemViewHolder(View itemView) {
        super(itemView);
        nameTextView = itemView.findViewById(R.id.favorite_name);
        pathTextView = itemView.findViewById(R.id.favorite_path);
        nameEditButton = itemView.findViewById(R.id.favorite_name_edit);
        favIcon = itemView.findViewById(R.id.fav_star);
    }
}
