package se.anwar.onlinedatabase.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import se.anwar.online_database.SQLiteOnlineHelper;
import se.anwar.onlinedatabase.R;
import se.anwar.onlinedatabase.db.DBManager;

public class MainActivity extends AppCompatActivity implements SQLiteOnlineHelper.OnFileDownloadListener {


    private static final String TAG = "MainActivity_Log";
    private static final String fileURL =
            "http://download1590.mediafire.com/nn44a3ic8ywg/pl5svsjl678dsp5/books.db.zip";
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
    public void onDownloadComplete() {
        Log.d(TAG, "onComplete: ");
        seekBar.setVisibility(View.GONE);
        showBooks();
    }

    private void downloadData() {
        DBManager dbManager = DBManager.getInstance(this);
        if (dbManager.shouldDownloadDatabase())
            dbManager.downloadDatabase(fileURL, this);
        else
            showBooks();
    }

    private void showBooks() {
        startActivity(new Intent(this, ListActivity.class));
        finish();
    }


}