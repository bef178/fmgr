package pd.droidapp.fmgr.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.view.ButtonState;
import pd.util.PathOps;

public class Util {

    public static String getSizeString(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("E: `size` must not be negative");
        }
        if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        if (exp > 6) {
            exp = 6;
        }
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", size / Math.pow(1024, exp), unit);
    }

    public static int themeColor(Context context, @AttrRes int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    // ClickableViewAccessibility: the listener never consumes events (returns false);
    @SuppressLint("ClickableViewAccessibility")
    public static void forwardViewActionsTo(View view, View itemView) {
        view.setHapticFeedbackEnabled(false);
        view.setSoundEffectsEnabled(false);
        view.setOnClickListener(v -> itemView.performClick());
        view.setOnLongClickListener(v -> itemView.performLongClick());
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    itemView.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                    // ViewGroup#dispatchSetPressed always propagates pressed=false to children,
                    // which would clear this view's pressed flag and swallow its pending click
                    itemView.post(() -> itemView.setPressed(false));
                    break;
                default:
                    itemView.setPressed(false);
                    break;
            }
            return false;
        });
    }

    public static float getGaussianValue(double mu, double sigma, float amplitude, float fraction) {
        // f(x) = A * exp(-(x-μ)² / (2σ²))
        double exponent = -Math.pow(fraction - mu, 2) / (2 * sigma * sigma);
        return (float) (amplitude * Math.exp(exponent));
    }

    public static void animateCollapsed(ImageView triangleView, View contentView, boolean collapsed) {
        animateCollapsed(triangleView, collapsed,
                () -> contentView.setVisibility(collapsed ? View.GONE : View.VISIBLE));
    }

    public static ValueAnimator animateCollapsed(ImageView triangleView, boolean collapsed, Runnable onAnimationEnded) {
        // rotate the triangle
        float targetRotation = collapsed ? -90f : 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(triangleView.getRotation(), targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation ->
                triangleView.setRotation((float) animation.getAnimatedValue()));
        if (onAnimationEnded != null) {
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        onAnimationEnded.run();
                    }
                }
            });
        }
        animator.start();
        return animator;
    }

    /**
     * display path for UI
     * `/storage/emulated/0` => `/`
     * `/storage/emulated/0/xxx` => `/xxx`
     */
    public static String getDisplayPath(String path) {
        String rootPath = Environment.getExternalStorageDirectory().getPath();
        if (path.equals(rootPath)) {
            return "/";
        }
        if (path.startsWith(rootPath + "/")) {
            return path.substring(rootPath.length());
        }
        return path;
    }

    public static FileProperties toFileProperties(String path) {
        return new FileProperties(PathOps.singleton.normalize(path), path.endsWith("/"));
    }

    public static List<FileProperties> toFileProperties(Collection<String> paths) {
        List<FileProperties> items = new LinkedList<>();
        for (String path : paths) {
            items.add(toFileProperties(path));
        }
        return items;
    }

    /**
     * return `null` or error string resource id
     */
    public static Integer checkBasename(String name) {
        if (name == null || name.isEmpty()) {
            return R.string.error_empty_name;
        }

        if (name.equals(".") || name.equals("..")) {
            return R.string.error_invalid_name;
        }

        if (name.contains("/") || name.contains("\\")) {
            return R.string.error_invalid_name;
        }

        // check for control characters (ASCII 0-31)
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32) {
                return R.string.error_invalid_name;
            }
        }

        return null;
    }

    public static int bitCeil(int n) {
        if (n <= 0) {
            return 1;
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }

    public static List<byte[]> encode(String s, String[] charsets) {
        return Arrays.stream(charsets)
                .map(charset -> encode(s, charset))
                .filter(Objects::nonNull)
                .map(ByteBuffer::wrap)
                .distinct()
                .map(ByteBuffer::array)
                .collect(Collectors.toList());
    }

    public static byte[] encode(String s, String charset) {
        try {
            ByteBuffer encoded = Charset.forName(charset).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(s));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void scrollToIndex(RecyclerView itemsView, int index) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int firstEntireVisibleIndex = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastEntireVisibleIndex = layoutManager.findLastCompletelyVisibleItemPosition();
        View firstEntireVisibleView = layoutManager.findViewByPosition(firstEntireVisibleIndex);
        View lastEntireVisibleView = layoutManager.findViewByPosition(lastEntireVisibleIndex);
        if (firstEntireVisibleView == null || lastEntireVisibleView == null) {
            return;
        }
        int viewportTop = itemsView.getPaddingTop();
        int viewportBottom = itemsView.getHeight() - itemsView.getPaddingBottom();
        int dy;
        if (index > lastEntireVisibleIndex) {
            dy = (index - lastEntireVisibleIndex) * lastEntireVisibleView.getHeight()
                    - (viewportBottom - lastEntireVisibleView.getBottom());
        } else if (index < firstEntireVisibleIndex) {
            dy = (index - firstEntireVisibleIndex) * firstEntireVisibleView.getHeight()
                    + (firstEntireVisibleView.getTop() - viewportTop);
        } else {
            return;
        }
        final int SCROLL_ANIMATION_MILLISECONDS = 250;
        if (Math.abs(dy) > itemsView.getHeight() - itemsView.getPaddingTop() - itemsView.getPaddingBottom()) {
            itemsView.scrollBy(0, dy);
        } else {
            itemsView.smoothScrollBy(0, dy, new LinearInterpolator(), SCROLL_ANIMATION_MILLISECONDS);
        }
    }

    public static boolean isSameAsOrDescendantOfAny(String path, Collection<FileProperties> items) {
        for (FileProperties item : items) {
            if (path.equals(item.path) || path.startsWith(item.path + "/")) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> listOf(T[] elements) {
        return elements == null || elements.length == 0
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(elements));
    }

    public static byte[] readHeadBytes(String path, int length) {
        byte[] head = new byte[length];
        int nRead = 0;
        try (FileInputStream inputStream = new FileInputStream(path)) {
            int n;
            while (nRead < head.length
                    && (n = inputStream.read(head, nRead, head.length - nRead)) > 0) {
                nRead += n;
            }
        } catch (IOException ignored) {
            return null;
        }
        if (nRead <= 0) {
            return null;
        }
        if (nRead == head.length) {
            return head;
        }
        return Arrays.copyOfRange(head, 0, nRead);
    }

    public static void renderButtonStates(List<ButtonState> buttonStates, ViewGroup containerView, @LayoutRes int layoutId, IntConsumer onButtonClicked) {
        // remove unused
        for (int i = containerView.getChildCount() - 1; i >= 0; i--) {
            int id = containerView.getChildAt(i).getId();
            boolean contains = false;
            for (ButtonState buttonState : buttonStates) {
                if (buttonState.id == id) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                containerView.removeViewAt(i);
            }
        }

        // add new and set status
        for (ButtonState buttonState : buttonStates) {
            View button = containerView.findViewById(buttonState.id);
            if (button == null) {
                button = LayoutInflater.from(containerView.getContext())
                        .inflate(layoutId, containerView, false);
                button.setId(buttonState.id);
                ((ImageButton) button).setImageResource(buttonState.drawableId);
                if (onButtonClicked != null) {
                    button.setOnClickListener(v -> onButtonClicked.accept(buttonState.id));
                }
                containerView.addView(button);
            }
            button.setVisibility(buttonState.visible ? View.VISIBLE : View.GONE);
            button.setEnabled(buttonState.enabled);
        }

        // sort without detach
        for (ButtonState buttonState : buttonStates) {
            containerView.bringChildToFront(containerView.findViewById(buttonState.id));
        }
    }

    public static void renderText(TextView textView, String text) {
        if (text == null) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(text);
            textView.setVisibility(View.VISIBLE);
        }
    }
}
