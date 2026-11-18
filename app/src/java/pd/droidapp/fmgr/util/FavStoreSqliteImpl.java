package pd.droidapp.fmgr.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import pd.droidapp.fmgr.util.FavoritesStore.FavItem;

import static pd.droidapp.fmgr.util.FavoritesStore.getDefaultName;

class FavStoreSqliteImpl implements FavStoreImpl {

    private static final String DATABASE_NAME = "fmgr.db";
    private static final String TABLE_NAME = "settings_favorites";
    private static final String COLUMN_PATH = "path";
    private static final String COLUMN_NAME = "name";
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
            + COLUMN_PATH + " TEXT PRIMARY KEY, "
            + COLUMN_NAME + " TEXT NOT NULL)";

    private final Context context;

    FavStoreSqliteImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean contains(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        try (SQLiteDatabase database = openDatabase()) {
            if (database == null) {
                return false;
            }
            try (Cursor cursor = database.query(TABLE_NAME, new String[]{"1"},
                    COLUMN_PATH + " = ?", new String[]{path}, null, null, null, "1")) {
                return cursor.moveToFirst();
            }
        }
    }

    @Override
    public FavItem get(String path) {
        try (SQLiteDatabase database = openDatabase()) {
            if (database == null) {
                return null;
            }
            try (Cursor cursor = database.query(TABLE_NAME, new String[]{COLUMN_NAME},
                    COLUMN_PATH + " = ?", new String[]{path}, null, null, null)) {
                return cursor.moveToFirst() ? new FavItem(path, cursor.getString(0)) : null;
            }
        }
    }

    @Override
    public List<FavItem> getAll() {
        List<FavItem> items = new ArrayList<>();
        try (SQLiteDatabase database = openDatabase()) {
            if (database == null) {
                return items;
            }
            try (Cursor cursor = database.query(TABLE_NAME,
                    new String[]{COLUMN_PATH, COLUMN_NAME}, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    items.add(new FavItem(cursor.getString(0), cursor.getString(1)));
                }
            }
        }
        items.sort(Comparator.comparing(item -> item.path));
        return items;
    }

    @Override
    public void put(String path) {
        put(path, null);
    }

    @Override
    public void put(String path, String name) {
        if (name == null || name.isEmpty()) {
            name = getDefaultName(path);
        }
        try (SQLiteDatabase database = openDatabase()) {
            if (database == null) {
                return;
            }
            ContentValues values = new ContentValues();
            values.put(COLUMN_PATH, path);
            values.put(COLUMN_NAME, name);
            database.insertWithOnConflict(TABLE_NAME, null, values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    @Override
    public void remove(String path) {
        try (SQLiteDatabase database = openDatabase()) {
            if (database == null) {
                return;
            }
            database.delete(TABLE_NAME, COLUMN_PATH + " = ?", new String[]{path});
        }
    }

    private SQLiteDatabase openDatabase() {
        File directory = context.getExternalFilesDir(null);
        if (directory == null) {
            directory = context.getFilesDir();
        }
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        try {
            SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(
                    new File(directory, DATABASE_NAME), null);
            database.execSQL(CREATE_TABLE);
            return database;
        } catch (SQLiteException e) {
            return null;
        }
    }
}
