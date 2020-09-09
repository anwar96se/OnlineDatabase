package se.anwar.onlinedatabase.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import se.anwar.onlinedatabase.R;
import se.anwar.onlinedatabase.db.DBManager;

public class ListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        ListView listView = findViewById(R.id.lst_books);

        DBManager dbManager = DBManager.getInstance(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                dbManager.getBooks()
        );
        listView.setAdapter(adapter);
    }
    
}
