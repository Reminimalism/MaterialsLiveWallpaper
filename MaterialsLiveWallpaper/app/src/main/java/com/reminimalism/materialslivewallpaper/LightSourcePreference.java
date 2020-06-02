package com.reminimalism.materialslivewallpaper;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

public class LightSourcePreference extends DialogPreference
{
    public LightSourcePreference(Context context)
    {
        super(context);
        Initialize();
    }
    public LightSourcePreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        Initialize();
    }
    public LightSourcePreference(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        Initialize();
    }
    public LightSourcePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
    {
        super(context, attrs, defStyleAttr, defStyleRes);
        Initialize();
    }

    private void Initialize()
    {
        // Nothing
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index)
    {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue)
    {
        SetValue(getPersistedString((String)defaultValue));
    }

    @Override
    public int getDialogLayoutResource()
    {
        return R.layout.light_source_preference;
    }

    String GetValue()
    {
        return Value;
    }

    void SetValue(String Value)
    {
        this.Value = Value;
        persistString(Value);
    }

    private String Value = "";
}
