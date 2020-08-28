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
        String[] result = Preferences.getString(LIGHT_COLORS_KEY, DEFAULT_COLOR).split(";");
        if (result.length == 0 || (result.length == 1 && result[0].equals("")))
            return new String[] { DEFAULT_COLOR };
        return result;
    }

    public static int GetColorsCount(Context context)
    {
        return GetColors(context).length;
    }

    public static String GetColor(Context context, int Index)
    {
        return GetColors(context)[Index];
    }

    private static void Save(String NewValue)
    {
        SharedPreferences.Editor editor = Preferences.edit();
        editor.putString(LIGHT_COLORS_KEY, NewValue);
        editor.apply();
    }

    public static void SetColor(Context context, int Index, String Value)
    {
        String[] arr = GetColors(context);
        arr[Index] = Value;
        StringBuilder result = new StringBuilder();
        result.append(arr[0]);
        for (int i = 1; i < arr.length; i++)
            result.append(";").append(arr[i]);
        Save(result.toString());
    }

    public static void Resize(Context context, int NewSize)
    {
        if (NewSize > 0)
        {
            String[] current = GetColors(context);
            StringBuilder result = new StringBuilder();
            result.append(current[0]);
            int len = Math.min(current.length, NewSize);
            for (int i = 1; i < len; i++)
                result.append(";").append(current[i]);
            for (int i = len; i < NewSize; i++)
                result.append(";").append(DEFAULT_COLOR);
            Save(result.toString());
        }
    }

    public static int GetSColorAsInt(LightColor color)
    {
        float I = GetIntensityOf(color);
        return Color.rgb(
                (int)((color.R / I) * 255),
                (int)((color.G / I) * 255),
                (int)((color.B / I) * 255)
        );
    }

    public static int GetIntensityColorAsInt(LightColor color)
    {
        float I = GetIntensityOf(color);
        return Color.rgb(
                (int)(I * 255),
                (int)(I * 255),
                (int)(I * 255)
        );
    }

    public static float GetIntensityOf(LightColor color)
    {
        return Math.max(color.R, Math.max(color.G, color.B));
    }

    public static String Encode(float R, float G, float B)
    {
        return R + "," + G + "," + B;
    }

    public static LightColor[] Decode(String[] Values)
    {
        LightColor[] result = new LightColor[Values.length];
        for (int i = 0; i < Values.length; i++)
            result[i] = Decode(Values[i]);
        return result;
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
