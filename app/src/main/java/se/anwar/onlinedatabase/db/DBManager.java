package se.anwar.onlinedatabase.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import se.anwar.online_database.SQLiteOnlineHelper;

public class DBManager extends SQLiteOnlineHelper {

    //region Constants
    private static final String TABLE_BOOK = "book";
    private static final String COL_Book_TITLE = "title";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "books.db";
    private static final String TAG = "DBManager_Log";
    //endregion

    //region Variables
    private static DBManager INSTANTS;
    //endregion

    //region InitDatabase
    private DBManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static DBManager getInstance(Context context) {
        if (INSTANTS == null) {
            INSTANTS = new DBManager(context);
        }
        return INSTANTS;
    }
    //endregion

    //region Books
    public List<String> getBooks() {
        List<String> books = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + TABLE_BOOK, null);
        while (c.moveToNext()) {
            books.add(c.getString(c.getColumnIndex(COL_Book_TITLE)));
        }
        c.close();
        db.close();
        return books;
    }
    //endregion

}
