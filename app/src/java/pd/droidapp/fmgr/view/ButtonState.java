package pd.droidapp.fmgr.view;

import androidx.annotation.DrawableRes;

import java.util.Objects;

public class ButtonState {

    public static ButtonState ofText(int id, String text) {
        return ofText(id, text, true, true);
    }

    public static ButtonState ofText(int id, String text, boolean visible) {
        return ofText(id, text, visible, true);
    }

    public static ButtonState ofText(int id, String text, boolean visible, boolean enabled) {
        return new ButtonState(id, 0, text, visible, enabled);
    }

    public final int id;
    public final int drawableId;
    public final String text;
    public final boolean visible;
    public final boolean enabled;

    public ButtonState(@DrawableRes int drawableId) {
        this(drawableId, true, true);
    }

    public ButtonState(@DrawableRes int drawableId, boolean visible) {
        this(drawableId, visible, true);
    }

    public ButtonState(@DrawableRes int drawableId, boolean visible, boolean enabled) {
        this(drawableId, drawableId, null, visible, enabled);
    }

    public ButtonState(int id, @DrawableRes int drawableId, String text, boolean visible, boolean enabled) {
        this.id = id;
        this.drawableId = drawableId;
        this.text = text;
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
                && Objects.equals(text, another.text)
                && visible == another.visible
                && enabled == another.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, drawableId, text, visible, enabled);
    }
}
