package se.anwar.online_database;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class Utils {

    private static final String TAG = "Utils_Log";
    private static final String VERSIONS_FILE = "version.txt";

    public static void writeExtractedFileToDisk(InputStream in, OutputStream outs) throws IOException {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            outs.write(buffer, 0, length);
        }
        outs.flush();
        outs.close();
        in.close();
    }

    public static ZipInputStream getFileFromZip(InputStream zipFileStream) throws IOException {
        ZipInputStream zis = new ZipInputStream(zipFileStream);
        ZipEntry ze;
        if ((ze = zis.getNextEntry()) != null) {
            Log.w(TAG, "extracting file: '" + ze.getName() + "'...");
            return zis;
        }
        return null;
    }

    public static void setDatabaseVersion(Context context, int version) {
        File cacheDir = new File(context.getApplicationInfo().dataDir + "/cache");
        if (!cacheDir.exists()) cacheDir.mkdir();
        File file = new File(cacheDir, VERSIONS_FILE);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, false));
            writer.write(version + "");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "setDatabaseVersion: ", e);
        }

    }

    public static int getDatabaseVersion(Context context) {
        String path = context.getApplicationInfo().dataDir + "/cache/" + VERSIONS_FILE;
        try {
            File file = new File(path);
            String line = new Scanner(file).useDelimiter("\\A").next();
            return Integer.parseInt(line);
        } catch (Exception e) {
            Log.w(TAG, "getDatabaseVersion: ", e);
            return 0;
        }
    }

    public static void deleteDatabaseFiles(String path) {
        try {
            new File(path).delete();
            new File(path + ".zip").delete();
            new File(path + ".gz").delete();
        } catch (Exception e) {
            Log.w(TAG, "deleteDatabaseFiles: failed delete old files", e);
        }
    }
}
