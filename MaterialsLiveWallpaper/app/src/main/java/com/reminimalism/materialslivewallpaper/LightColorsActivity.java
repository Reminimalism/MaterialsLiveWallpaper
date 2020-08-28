package com.reminimalism.materialslivewallpaper;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LightColorsActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_light_colors);
        ActionBar action_bar = getSupportActionBar();
        if (action_bar != null)
            action_bar.setDisplayHomeAsUpEnabled(true);
        InitializeDynamicList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
            onBackPressed();
        return super.onOptionsItemSelected(item);
    }

    private final int MAX_COLORS_COUNT = 10;
    private final int MAX_INTENSITY_PREVIEW_WIDTH = 24;
    private final int INTENSITY_PREVIEW_HEIGHT = 4;

    private LinearLayout DynamicListLayout = null;
    private View[] ItemViews = new View[MAX_COLORS_COUNT];
    private View[] SColorViews = new View[MAX_COLORS_COUNT];
    private View[] IntensityViews = new View[MAX_COLORS_COUNT];

    private LightColor[] Colors = {};

    private void InitializeDynamicList()
    {
        if (DynamicListLayout != null)
            return;
        DynamicListLayout = findViewById(R.id.dynamic_list);
        LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);

        for (int i = 0; i < MAX_COLORS_COUNT; i++)
        {
            ItemViews[i] = inflater.inflate(R.layout.light_colors_list_item, null);
            DynamicListLayout.addView(ItemViews[i]);

            SColorViews[i] = ItemViews[i].findViewById(R.id.color_preview);
            IntensityViews[i] = ItemViews[i].findViewById(R.id.intensity_preview);
            ((TextView)ItemViews[i].findViewById(R.id.label)).setText(
                    String.format(getResources().getString(R.string.settings_lights_colors_light_color_n), i + 1)
            );

            final int Index = i;
            ItemViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v)
                {
                    new LightColorDialog(LightColorsActivity.this, Index).show();
                }
            });
        }

        Update();
    }

    private void Update()
    {
        Colors = LightColors.Decode(LightColors.GetColors(this));

        int len = Math.min(Colors.length, MAX_COLORS_COUNT);
        for (int i = 0; i < len; i++)
        {
            ItemViews[i].setVisibility(View.VISIBLE);

            SColorViews[i].setBackgroundColor(LightColors.GetSColorAsInt(Colors[i]));

            int height_in_pixels = (int) (INTENSITY_PREVIEW_HEIGHT * getResources().getDisplayMetrics().density + 0.5f);
            float width_in_dps = LightColors.GetIntensityOf(Colors[i]) * MAX_INTENSITY_PREVIEW_WIDTH;
            int width_in_pixels = (int) (width_in_dps * getResources().getDisplayMetrics().density + 0.5f);
            IntensityViews[i].setLayoutParams(new LinearLayout.LayoutParams(width_in_pixels, height_in_pixels));
        }
        for (int i = len; i < MAX_COLORS_COUNT; i++)
        {
            ItemViews[i].setVisibility(View.GONE);
        }
    }
}
