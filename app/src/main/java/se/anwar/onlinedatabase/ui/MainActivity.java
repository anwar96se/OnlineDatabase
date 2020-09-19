package se.anwar.onlinedatabase.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import se.anwar.online_database.SQLiteOnlineHelper;
import se.anwar.onlinedatabase.R;
import se.anwar.onlinedatabase.db.DBManager;

public class MainActivity extends AppCompatActivity implements SQLiteOnlineHelper.OnFileDownloadListener {


    private static final String TAG = "MainActivity_Log";
    private static final String FILE_URL =
            "http://download1489.mediafire.com/rcqfw3zqj1mg/cj8dihw60zoa8q6/books.db.zip";
    private SeekBar seekBar;
    private TextView textProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        seekBar = findViewById(R.id.progress);
        textProgress = findViewById(R.id.tv_progress);
        downloadData();
    }

    @Override
    public void onDownloadStart() {
        seekBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDownloadProgress(int progress) {
        seekBar.setProgress(progress);
        textProgress.setText(progress < 100 ? "Progress: " + progress + "%" : "Download Completed");
        Log.i(TAG, "onProgress: " + progress);
    }

    @Override
    public void onDownloadFailed(Exception e) {
        seekBar.setVisibility(View.GONE);
//        showBooks();
        Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        textProgress.setText(e.getLocalizedMessage());
    }

    @Override
    public void onDownloadSuccess() {
        Log.d(TAG, "onComplete: ");
        seekBar.setVisibility(View.GONE);
        showBooks();
    }

    private void downloadData() {
        DBManager dbManager = DBManager.getInstance(this);
        if (dbManager.shouldDownloadDatabase())
            dbManager.downloadDatabase(FILE_URL, this);
        else
            showBooks();
    }

    private void showBooks() {
        startActivity(new Intent(this, ListActivity.class));
        finish();
    }


}