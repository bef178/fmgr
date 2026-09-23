package pd.droidapp.fmgr.view;

import androidx.annotation.DrawableRes;

import java.util.Objects;

public class ButtonState {

    public final int id;
    public final int drawableId;
    public final boolean visible;
    public final boolean enabled;

    public ButtonState(@DrawableRes int drawableId, boolean visible, boolean enabled) {
        this.id = drawableId;
        this.drawableId = drawableId;
        this.visible = visible;
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ButtonState)) {
            return false;
        }
        ButtonState another = (ButtonState) o;
        return id == another.id
                && drawableId == another.drawableId
                && visible == another.visible
                && enabled == another.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, drawableId, visible, enabled);
    }
}
