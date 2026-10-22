package pd.droidapp.fmgr.util;

import android.content.Context;
import android.content.res.XmlResourceParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;
import pd.droidapp.fmgr.R;
import pd.util.PathOps;

public class FileIconUtil {

    private static Map<String, Integer> mimeDrawables;
    private static Map<String, Integer> extDrawables;
    private static Map<Integer, String> drawableMimes;

    private static Map<String, Integer> getOrLoadMimeDrawables(Context context) {
        Map<String, Integer> parsed = mimeDrawables;
        if (parsed == null) {
            mimeDrawables = parsed = readDrawables(context, R.xml.mime_icons, "mime");
        }
        return parsed;
    }

    private static Map<String, Integer> getOrLoadExtDrawables(Context context) {
        Map<String, Integer> parsed = extDrawables;
        if (parsed == null) {
            extDrawables = parsed = readDrawables(context, R.xml.ext_icons, "ext");
        }
        return parsed;
    }

    private static Map<Integer, String> getOrLoadDrawableMimes(Context context) {
        Map<Integer, String> parsed = drawableMimes;
        if (parsed == null) {
            parsed = new HashMap<>();
            for (Map.Entry<String, Integer> entry : getOrLoadMimeDrawables(context).entrySet()) {
                parsed.put(entry.getValue(), entry.getKey());
            }
            drawableMimes = parsed;
        }
        return parsed;
    }

    // parsed once from the given res/xml icon mapping
    private static Map<String, Integer> readDrawables(Context context, int xmlResId, String keyAttr) {
        Map<String, Integer> parsed = new HashMap<>();
        try (XmlResourceParser parser = context.getResources().getXml(xmlResId)) {
            int event;
            while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG && "item".equals(parser.getName())) {
                    String key = parser.getAttributeValue(null, keyAttr);
                    int drawableId = parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "drawable", 0);
                    if (key != null && drawableId != 0) {
                        parsed.put(key, drawableId);
                    }
                }
            }
        } catch (IOException | XmlPullParserException ignored) {
        }
        return parsed;
    }

    public static int getDrawableId(Context context, FileProperties item) {
        return getDrawableId(context, item.path, item.isDirectory, item.mimeType);
    }

    public static int getDrawableId(Context context, String path, boolean isDirectory, String mimeType) {
        if (isDirectory) {
            return R.drawable.ic_directory_24;
        }
        Integer drawableId = mimeType != null ? getOrLoadMimeDrawables(context).get(mimeType) : null;
        if (drawableId == null && path != null) {
            String extname = PathOps.singleton.extname(path);
            if (extname != null) {
                drawableId = getOrLoadExtDrawables(context).get(extname.toLowerCase(Locale.ROOT));
            }
        }
        return drawableId != null ? drawableId : R.drawable.ic_file_24;
    }

    public static String getMimeByDrawableId(Context context, int drawableId) {
        return getOrLoadDrawableMimes(context).get(drawableId);
    }
}
