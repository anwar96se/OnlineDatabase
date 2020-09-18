package se.anwar.online_database;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

import javax.net.ssl.HttpsURLConnection;

public class SQLiteOnlineHelper extends SQLiteOpenHelper {

    //region Constants
    private static final String TAG = "SQLiteOnline_Log";
    private static final int BUFFER_SIZE = 1024;
    //endregion

    //region Variables
    private final Context mContext;
    private final String mName;
    private final SQLiteDatabase.CursorFactory mFactory;
    private final int mNewVersion;

    private SQLiteDatabase mDatabase = null;
    private boolean mIsInitializing = false;
    private String mDatabasePath;
    //endregion

    //region Constructor

    /**
     * Create a helper object to create, open, and/or manage a database in
     * a specified location.
     * This method always returns very quickly.  The database is not actually
     * created or opened until one of {@link #getWritableDatabase} or
     * {@link #getReadableDatabase} is called.
     *
     * @param context          to use to open or create the database
     * @param name             of the database file
     * @param storageDirectory to store the database file upon creation; caller must
     *                         ensure that the specified absolute path is available and can be written to
     * @param factory          to use for creating cursor objects, or null for the default
     * @param version          number of the database (starting at 1); if the database is older,
     *                         SQL file(s) contained within the application assets folder will be used to
     *                         upgrade the database
     */
    public SQLiteOnlineHelper(Context context, String name, String storageDirectory, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);

        if (version < 1) throw new IllegalArgumentException("Version must be >= 1, was " + version);
        if (name == null) throw new IllegalArgumentException("Database name cannot be null");

        mContext = context;
        mName = name;
        mFactory = factory;
        mNewVersion = version;

        if (storageDirectory != null) {
            mDatabasePath = storageDirectory;
        } else {
            mDatabasePath = context.getApplicationInfo().dataDir + "/databases";
        }
    }

    /**
     * Create a helper object to create, open, and/or manage a database in
     * the application's default private data directory.
     * This method always returns very quickly.  The database is not actually
     * created or opened until one of {@link #getWritableDatabase} or
     * {@link #getReadableDatabase} is called.
     *
     * @param context to use to open or create the database
     * @param name    of the database file
     * @param factory to use for creating cursor objects, or null for the default
     * @param version number of the database (starting at 1); if the database is older,
     *                SQL file(s) contained within the application assets folder will be used to
     *                upgrade the database
     */
    public SQLiteOnlineHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        this(context, name, null, factory, version);
    }
    //endregion

    //region Override
    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        if (mDatabase != null && mDatabase.isOpen() && !mDatabase.isReadOnly()) {
            return mDatabase;  // The database is already open for business
        }

        if (mIsInitializing) {
            throw new IllegalStateException("getWritableDatabase called recursively");
        }

        // If we have a read-only database open, someone could be using it
        // (though they shouldn't), which would cause a lock to be held on
        // the file, and our attempts to open the database read-write would
        // fail waiting for the file lock.  To prevent that, we acquire the
        // lock on the read-only database, which shuts out other users.

        boolean success = false;
        SQLiteDatabase db = null;
        //if (mDatabase != null) mDatabase.lock();
        try {
            mIsInitializing = true;
            //if (mName == null) {
            //    db = SQLiteDatabase.create(null);
            //} else {
            //    db = mContext.openOrCreateDatabase(mName, 0, mFactory);
            //}
            db = createOrOpenDatabase(false);

            int version = db.getVersion();

            // do force upgrade
            if (version < 0) {
                db = createOrOpenDatabase(true);
                db.setVersion(mNewVersion);
                version = db.getVersion();
            }

            if (version != mNewVersion) {
                db.beginTransaction();
                try {
                    if (version == 0) {
                        onCreate(db);
                    } else {
                        if (version > mNewVersion) {
                            Log.w(TAG, "Can't downgrade read-only database from version " +
                                    version + " to " + mNewVersion + ": " + db.getPath());
                        }
                        onUpgrade(db, version, mNewVersion);
                    }
                    db.setVersion(mNewVersion);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            onOpen(db);
            success = true;
            return db;
        } finally {
            mIsInitializing = false;
            if (success) {
                if (mDatabase != null) {
                    try {
                        mDatabase.close();
                    } catch (Exception e) {
                        Log.e(TAG, "getWritableDatabase: ", e);
                    }
                    //mDatabase.unlock();
                }
                mDatabase = db;
            } else {
                //if (mDatabase != null) mDatabase.unlock();
                if (db != null) db.close();
            }
        }

    }

    /**
     * Create and/or open a database.  This will be the same object returned by
     * {@link #getWritableDatabase} unless some problem, such as a full disk,
     * requires the database to be opened read-only.  In that case, a read-only
     * database object will be returned.  If the problem is fixed, a future call
     * to {@link #getWritableDatabase} may succeed, in which case the read-only
     * database object will be closed and the read/write object will be returned
     * in the future.
     *
     * <p class="caution">Like {@link #getWritableDatabase}, this method may
     * take a long time to return, so you should not call it from the
     * application main thread, including from
     * {@link android.content.ContentProvider#onCreate ContentProvider.onCreate()}.
     *
     * @return a database object valid until {@link #getWritableDatabase}
     * or {@link #close} is called.
     * @throws SQLiteException if the database cannot be opened
     */
    @Override
    public synchronized SQLiteDatabase getReadableDatabase() {
        if (mDatabase != null && mDatabase.isOpen()) {
            return mDatabase;  // The database is already open for business
        }

        if (mIsInitializing) {
            throw new IllegalStateException("getReadableDatabase called recursively");
        }

        try {
            return getWritableDatabase();
        } catch (SQLiteException e) {
            if (mName == null) throw e;  // Can't open a temp database read-only!
            Log.e(TAG, "Couldn't open " + mName + " for writing (will try read-only):", e);
        }

        SQLiteDatabase db = null;
        try {
            mIsInitializing = true;
            String path = mContext.getDatabasePath(mName).getPath();
            db = SQLiteDatabase.openDatabase(path, mFactory, SQLiteDatabase.OPEN_READONLY);
            if (db.getVersion() != mNewVersion) {
                throw new SQLiteException("Can't upgrade read-only database from version " +
                        db.getVersion() + " to " + mNewVersion + ": " + path);
            }

            onOpen(db);
            Log.w(TAG, "Opened " + mName + " in read-only mode");
            mDatabase = db;
            return mDatabase;
        } finally {
            mIsInitializing = false;
            if (db != null && db != mDatabase) db.close();
        }
    }

    /**
     * Close any open database object.
     */
    @Override
    public synchronized void close() {
        if (mIsInitializing) throw new IllegalStateException("Closed during initialization");

        if (mDatabase != null && mDatabase.isOpen()) {
            mDatabase.close();
            mDatabase = null;
        }
    }

    @Override
    public final void onConfigure(SQLiteDatabase db) {
        // not supported!
    }

    @Override
    public final void onCreate(SQLiteDatabase db) {
        // do nothing - createOrOpenDatabase() is called in
        // getWritableDatabase() to handle database creation.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public final void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // not supported!
    }
    //endregion

    //region Copy & Upgrade database
    private SQLiteDatabase createOrOpenDatabase(boolean force) throws SQLiteOnlineException {

        // test for the existence of the db file first and don't attempt open
        // to prevent the error trace in log on API 14+
        SQLiteDatabase db = null;
        File file = new File(mDatabasePath + "/" + mName);
        if (file.exists()) {
            db = returnDatabase();
        }
        //SQLiteDatabase db = returnDatabase();

        if (db != null) {
            // database already exists
            if (force) {
                Log.w(TAG, "forcing database upgrade!");
                copyDatabaseFromZip();
                db = returnDatabase();
            }
            return db;
        } else {
            // database does not exist, copy it from assets and return it
            copyDatabaseFromZip();
            db = returnDatabase();
            return db;
        }
    }

    private SQLiteDatabase returnDatabase() {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(mDatabasePath + "/" + mName, mFactory, SQLiteDatabase.OPEN_READWRITE);
            Log.i(TAG, "successfully opened database " + mName);
            return db;
        } catch (SQLiteException e) {
            Log.w(TAG, "could not open database " + mName + " - " + e.getMessage());
            return null;
        }
    }

    private void copyDatabaseFromZip() throws SQLiteOnlineException {
        Log.d(TAG, "copying database from assets...");

        String path = mDatabasePath + "/" + mName;
        String dest = mDatabasePath + "/" + mName;
        InputStream is;
        boolean isZip = false;
        // try zip
        try {
            is = new FileInputStream(path + ".zip");
            isZip = true;
        } catch (IOException e2) {
            // try gzip
            try {
                is = new FileInputStream(path + ".gz");
            } catch (IOException e3) {
                SQLiteOnlineException se = new SQLiteOnlineException("Missing " + mName + " file (or .zip, .gz archive) in assets, or target folder not writable");
                se.setStackTrace(e3.getStackTrace());
                throw se;
            }
        }

        try {
            File f = new File(mDatabasePath + "/");
            if (!f.exists()) {
                Log.i(TAG, "copyDatabaseFromZip: mkdir " + f.mkdir());
            }
            if (isZip) {
                ZipInputStream zis = Utils.getFileFromZip(is);
                if (zis == null) {
                    throw new SQLiteOnlineException("Archive is missing a SQLite database file");
                }
                Utils.writeExtractedFileToDisk(zis, new FileOutputStream(dest));
            } else {
                Utils.writeExtractedFileToDisk(is, new FileOutputStream(dest));
            }

            Log.d(TAG, "database copy complete");

        } catch (IOException e) {
            SQLiteOnlineException se = new SQLiteOnlineException("Unable to write " + dest + " to data directory");
            se.setStackTrace(e.getStackTrace());
            throw se;
        }
    }
    //endregion

    //region Download database
    private boolean isDatabaseDownloaded() {
        String path = mDatabasePath + "/" + mName;
        return new File(path).exists()
                || new File(path + ".zip").exists()
                || new File(path + ".gz").exists();
    }

    public boolean shouldDownloadDatabase() {
        int mDbVersion = Utils.getDatabaseVersion(mContext);
        boolean shouldUpdate = mDbVersion < mNewVersion || !isDatabaseDownloaded();
        if (shouldUpdate) {
            String path = mDatabasePath + "/" + mName;
            Utils.deleteDatabaseFiles(path);
        }
        Utils.setDatabaseVersion(mContext, mNewVersion);
        return shouldUpdate;
    }

    public void downloadDatabase(final String fileURL,
                                 final OnFileDownloadListener listener) {
        ExecutorService exec = Executors.newCachedThreadPool();
        exec.submit(new Runnable() {
            @Override
            public void run() {
                downloadFile(fileURL, listener);
            }
        });
    }

    private void downloadFile(String fileURL, final OnFileDownloadListener listener) {
        onStart(listener);
        HttpsURLConnection httpConnection = null;
        try {
            URL url = new URL(fileURL);
            httpConnection = (HttpsURLConnection) url.openConnection();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                httpConnection.setSSLSocketFactory(new TLSSocketFactory());
            httpConnection.addRequestProperty("Accept-Encoding", "identity");
            int responseCode = httpConnection.getResponseCode();

            String fileName = "";
            // always check HTTP response code first
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String disposition = httpConnection.getHeaderField("Content-Disposition");
                String contentType = httpConnection.getContentType();
                int contentLength = httpConnection.getContentLength();

                if (disposition != null) {
                    // extracts file name from header field
                    int index = disposition.indexOf("filename=");
                    if (index > 0) {
                        fileName = disposition.substring(index + 10,
                                disposition.length() - 1);
                    }
                } else {
                    // extracts file name from URL
                    fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
                }

                Log.i(TAG, "Content-Type = " + contentType);
                Log.i(TAG, "Content-Disposition = " + disposition);
                Log.i(TAG, "Content-Length = " + contentLength);
                Log.i(TAG, "fileName = " + fileName);

                if (contentLength < 0) {
                    throw new SQLiteOnlineException
                            ("No file to download. Server replied HTTP code: " + responseCode);
                }

                // opens input stream from the HTTP connection
                InputStream inputStream = httpConnection.getInputStream();

                // opens an output stream to save into file
                File dir = new File(mDatabasePath + "/");
                if (!dir.exists()) {
                    boolean mkdir = dir.mkdir();
                    Log.i(TAG, "downloadFile: " + mkdir);
                }
                File dbFile = new File(dir, fileName);
//                FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                FileOutputStream outputStream = new FileOutputStream(dbFile);

                int bytesRead;
                long total = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    total += bytesRead;
                    final int progress = (int) (total * 100 / contentLength);
                    onProgress(listener, progress);
                }

                outputStream.close();
                inputStream.close();

                Log.i(TAG, "File downloaded");
                copyDatabaseFromZip();
                onSuccess(listener);
            } else {
                throw new SQLiteOnlineException
                        ("No file to download. Server replied HTTP code: " + responseCode);
            }

        } catch (final Exception e) {
            onFailed(listener, e);
        } finally {
            if (httpConnection != null)
                httpConnection.disconnect();
        }
    }
    //endregion

    //region DownloadListener
    private void runOnUiThread(Runnable runnable) {
        ((Activity) mContext).runOnUiThread(runnable);
    }

    private void onStart(final OnFileDownloadListener listener) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onDownloadStart();
            }
        });
    }

    private void onProgress(final OnFileDownloadListener listener, final int progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onDownloadProgress(progress);
            }
        });
    }

    private void onFailed(final OnFileDownloadListener listener, final Exception e) {
        Log.w(TAG, "downloadFile: Failed", e);
        String path = mDatabasePath + "/" + mName;
        Utils.deleteDatabaseFiles(path);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onDownloadFailed(e);
            }
        });
    }

    private void onSuccess(final OnFileDownloadListener listener) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onDownloadSuccess();
            }
        });
    }

    public interface OnFileDownloadListener {
        void onDownloadStart();

        void onDownloadProgress(int progress);

        void onDownloadFailed(Exception e);

        void onDownloadSuccess();
    }
    //endregion

    //region SQLite Exception

    /**
     * An exception that indicates there was an error with SQLite asset retrieval or parsing.
     */
    @SuppressWarnings("serial")
    public static class SQLiteOnlineException extends SQLiteException {

        public SQLiteOnlineException(String error) {
            super(error);
        }
    }
    //endregion

}
