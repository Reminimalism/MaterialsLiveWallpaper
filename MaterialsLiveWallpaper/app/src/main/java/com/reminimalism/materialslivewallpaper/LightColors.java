package com.reminimalism.materialslivewallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.preference.PreferenceManager;

public class LightColors
{
    private static SharedPreferences Preferences;

    private static void Init(Context context)
    {
        if (Preferences == null)
            Preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static final String LIGHT_COLORS_KEY = "light_colors";
    private static final String DEFAULT_COLOR = "0.6,0.6,0.6";

    public static String[] GetColors(Context context)
    {
        Init(context);
        return Preferences.getString(LIGHT_COLORS_KEY, DEFAULT_COLOR).split(";");
    }

    public static int GetColorsCount(Context context)
    {
        Init(context);
        return Preferences.getString(LIGHT_COLORS_KEY, DEFAULT_COLOR).split(";").length;
    }

    public static String GetColor(Context context, int Index)
    {
        Init(context);
        return Preferences.getString(LIGHT_COLORS_KEY, DEFAULT_COLOR).split(";")[Index];
    }

    public static void SetColor(Context context, int Index, String Value)
    {
        Init(context);
        String[] arr = Preferences.getString(LIGHT_COLORS_KEY, DEFAULT_COLOR).split(";");
        arr[Index] = Value;
        StringBuilder result = new StringBuilder();
        result.append(arr[0]);
        for (int i = 1; i < arr.length; i++)
            result.append(";").append(arr[i]);
        SharedPreferences.Editor editor = Preferences.edit();
        editor.putString(LIGHT_COLORS_KEY, result.toString());
        editor.apply();
    }

    public static int GetSColorAsInt(Context context, int Index)
    {
        Init(context);
        LightColor color = Decode(GetColor(context, Index));
        float I = Math.max(color.R, Math.max(color.G, color.B));
        color.R /= I;
        color.G /= I;
        color.B /= I;
        return Color.rgb(
                (int)(color.R * 255),
                (int)(color.G * 255),
                (int)(color.B * 255)
        );
    }

    public static int GetIntensityColorAsInt(Context context, int Index)
    {
        Init(context);
        LightColor color = Decode(GetColor(context, Index));
        float I = Math.max(color.R, Math.max(color.G, color.B));
        return Color.rgb(
                (int)(I * 255),
                (int)(I * 255),
                (int)(I * 255)
        );
    }

    public static String Encode(float R, float G, float B)
    {
        return R + "," + G + "," + B;
    }

    public static LightColor Decode(String Value, float DefaultR, float DefaultG, float DefaultB)
    {
        try
        {
            return Decode(Value);
        }
        catch (Exception ignored)
        {
            LightColor result = new LightColor();
            result.R = DefaultR;
            result.G = DefaultG;
            result.B = DefaultB;
            return result;
        }
    }

    public static LightColor Decode(String Value, LightColor DefaultColor)
    {
        try
        {
            return Decode(Value);
        }
        catch (Exception ignored)
        {
            return DefaultColor;
        }
    }

    public static LightColor Decode(String Value)
    {
        LightColor result = new LightColor();
        String[] values = Value.split(",");
        result.R   = Float.parseFloat(values[0]);
        result.G = Float.parseFloat(values[1]);
        result.B  = Float.parseFloat(values[2]);
        return result;
    }
}
