package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ViewFlipper;

public class MainActivity extends Activity {
    private static class Screens {
        public static final int MANAGEMENT = 0;
        public static final int ADD_NEW_DEVICE = 1;
    }

    private ViewFlipper viewFlipper;
    private Button btnAddFresh;
    private Button btnAddConfigured;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFlipper = findViewById(R.id.viewFlipper);

        btnAddFresh = findViewById(R.id.btnAddFresh);
        btnAddConfigured = findViewById(R.id.btnAddConfigured);

        Button[] allButtons = new Button[]{
                btnAddFresh,
                btnAddConfigured
        };

        ColorStateList cslButtonBg = getResources().getColorStateList(R.color.button_bg);
        ColorStateList cslButtonText = getResources().getColorStateList(R.color.button_text);
        for (Button btn : allButtons) {
            btn.setBackgroundTintList(cslButtonBg);
            btn.setTextColor(cslButtonText);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /*
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }
    */

    public void onAddNewDevice(MenuItem menuItem) {
        viewFlipper.setDisplayedChild(Screens.ADD_NEW_DEVICE);
    }

    public void onUpdateStatuses(MenuItem menuItem) {
    }
}
