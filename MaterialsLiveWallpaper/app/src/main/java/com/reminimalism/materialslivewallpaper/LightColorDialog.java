package com.reminimalism.materialslivewallpaper;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.SeekBar;

public class LightColorDialog extends Dialog
{
    private int LightIndex;

    public LightColorDialog(Activity activity, int LightIndex)
    {
        super(activity);
        this.LightIndex = LightIndex;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.light_color_dialog);
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                dismiss();
            }
        });
        findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                Save();
                dismiss();
            }
        });
        HueSeekBar = findViewById(R.id.color_hue);
        SaturationSeekBar = findViewById(R.id.color_saturation);
        IntensitySeekBar = findViewById(R.id.intensity);
        HuePreview = findViewById(R.id.color_hue_preview);
        SaturationPreview = findViewById(R.id.color_saturation_preview);
        IntensityPreview = findViewById(R.id.intensity_preview);
        SeekBar.OnSeekBarChangeListener SeekBarListener = new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if (fromUser)
                {
                    UpdateBasedOnUI();
                    UpdateUIColors();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar)
            {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar)
            {
            }
        };
        HueSeekBar.setOnSeekBarChangeListener(SeekBarListener);
        SaturationSeekBar.setOnSeekBarChangeListener(SeekBarListener);
        IntensitySeekBar.setOnSeekBarChangeListener(SeekBarListener);
        Load();
    }

    // Preference Data

    void Load()
    {
        // For now
        Decode("0.6,0.2,0.4");
        UpdateUIPositions();
        UpdateUIColors();
    }

    void Save()
    {
        //
    }

    // UI

    private final int SEEKBAR_MAX = 1024;

    private SeekBar HueSeekBar = null;
    private SeekBar SaturationSeekBar = null;
    private SeekBar IntensitySeekBar = null;

    private View HuePreview = null;
    private View SaturationPreview = null;
    private View IntensityPreview = null;

    private void UpdateUIPositions()
    {
        HueSeekBar.setMax(SEEKBAR_MAX);
        SaturationSeekBar.setMax(SEEKBAR_MAX);
        IntensitySeekBar.setMax(SEEKBAR_MAX);

        HueSeekBar.setProgress((int)(Hue * SEEKBAR_MAX));
        SaturationSeekBar.setProgress((int)(Saturation * SEEKBAR_MAX));
        IntensitySeekBar.setProgress((int)(Intensity * SEEKBAR_MAX));
    }

    private void UpdateUIColors()
    {
        SetSeekBarColor(HueSeekBar, HRed, HGreen, HBlue);
        SetSeekBarColor(SaturationSeekBar, SRed, SGreen, SBlue);
        SetSeekBarColor(IntensitySeekBar, Red, Green, Blue);
        HuePreview.setBackgroundColor(GetColorInt(HRed, HGreen, HBlue));
        SaturationPreview.setBackgroundColor(GetColorInt(SRed, SGreen, SBlue));
        IntensityPreview.setBackgroundColor(GetColorInt(Red, Green, Blue));
    }

    private void UpdateBasedOnUI()
    {
        Hue = (float)HueSeekBar.getProgress() / SEEKBAR_MAX;
        Saturation = (float)SaturationSeekBar.getProgress() / SEEKBAR_MAX;
        Intensity = (float)IntensitySeekBar.getProgress() / SEEKBAR_MAX;
        UpdateBasedOnHSI();
    }

    private void SetSeekBarColor(SeekBar seek_bar, float r, float g, float b)
    {
        seek_bar.getProgressDrawable().setColorFilter(Color.rgb(
                (int)(255 * r), (int)(255 * g), (int)(255 * b)
        ), PorterDuff.Mode.SRC_IN);
    }

    private int GetColorInt(float r, float g, float b)
    {
        return Color.rgb(
                (int)(r * 255),
                (int)(g * 255),
                (int)(b * 255)
        );
    }

    // Calculations

    private float Red;
    private float Green;
    private float Blue;

    private float Hue = 0;
    private float Saturation = 0;
    private float Intensity = 0.6f;

    private float HRed;
    private float HGreen;
    private float HBlue;

    private float SRed;
    private float SGreen;
    private float SBlue;

    private void UpdateBasedOnHSI()
    {
        // Apply Hue

        float h = Hue * 6;

        if (h < 1)
        {
            HRed = 1;     HGreen = h;     HBlue = 0;
        }
        else if (h < 2)
        {
            HRed = 2 - h; HGreen = 1;     HBlue = 0;
        }
        else if (h < 3)
        {
            HRed = 0;     HGreen = 1;     HBlue = h - 2;
        }
        else if (h < 4)
        {
            HRed = 0;     HGreen = 4 - h; HBlue = 1;
        }
        else if (h < 5)
        {
            HRed = h - 4; HGreen = 0;     HBlue = 1;
        }
        else if (h < 6)
        {
            HRed = 1;     HGreen = 0;     HBlue = 6 - h;
        }
        else
        {
            HRed = 1;     HGreen = 0;     HBlue = 0;
        }

        // Apply Saturation

        SRed   = 1 - (1 - HRed)   * Saturation;
        SGreen = 1 - (1 - HGreen) * Saturation;
        SBlue  = 1 - (1 - HBlue)  * Saturation;

        // Apply Intensity

        Red   = SRed   * Intensity;
        Green = SGreen * Intensity;
        Blue  = SBlue  * Intensity;
    }

    private void UpdateBasedOnRGB()
    {
        Intensity = Math.max(Math.max(Red, Blue), Green);

        if (Intensity == 0)
        {
            SRed = 1; SGreen = 1; SBlue = 1;
            Saturation = 0;
            HRed = 1; HGreen = 0; HBlue = 0;
            Hue = 0;
            return;
        }

        SRed   = Red   / Intensity;
        SGreen = Green / Intensity;
        SBlue  = Blue  / Intensity;

        Saturation = 1 - Math.min(Math.min(SRed, SGreen), SBlue);

        if (Saturation == 0)
        {
            HRed = 1; HGreen = 0; HBlue = 0;
            Hue = 0;
            return;
        }

        HRed   = 1 - (1 - SRed)   / Saturation;
        HGreen = 1 - (1 - SGreen) / Saturation;
        HBlue  = 1 - (1 - SBlue)  / Saturation;

        if      (HRed   >= HGreen && HGreen >= HBlue)  Hue = HGreen;
        else if (HGreen >= HRed   && HRed   >= HBlue)  Hue = 2 - HRed;
        else if (HGreen >= HBlue  && HBlue  >= HRed)   Hue = 2 + HBlue;
        else if (HBlue  >= HGreen && HGreen >= HRed)   Hue = 4 - HGreen;
        else if (HBlue  >= HRed   && HRed   >= HGreen) Hue = 4 + HRed;
        else if (HRed   >= HBlue  && HBlue  >= HGreen) Hue = 6 - HBlue;
        else Hue = 0;

        Hue /= 6;
    }

    private void Decode(String Value)
    {
        try
        {
            String[] values = Value.split(",");
            Red   = Float.parseFloat(values[0]);
            Green = Float.parseFloat(values[1]);
            Blue  = Float.parseFloat(values[2]);
            UpdateBasedOnRGB();
        }
        catch (Exception ignored)
        {
            Hue = 0;
            Saturation = 0;
            Intensity = 0;
            UpdateBasedOnHSI();
        }
    }

    private String Encode()
    {
        return Red + "," + Green + "," + Blue;
    }
}