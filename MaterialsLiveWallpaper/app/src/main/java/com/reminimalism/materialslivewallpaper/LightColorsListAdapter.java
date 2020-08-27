package com.reminimalism.materialslivewallpaper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class LightColorsListAdapter extends ArrayAdapter<String>
{
    private Context Context;
    private String[] Values;

    public LightColorsListAdapter(Context context, String[] values)
    {
        super(context, -1, values);
        Context = context;
        Values = values;
    }

    @Override @NonNull
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) Context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View item = inflater.inflate(R.layout.light_colors_list_item, parent, false);

        ((TextView)item.findViewById(R.id.label)).setText(
                Context.getResources().getString(R.string.settings_lights_colors_light)
                        + " "
                        + position
        );
        item.findViewById(R.id.color_preview).setBackgroundColor(LightColors.GetSColorAsInt(Context, position));
        item.findViewById(R.id.intensity_preview).setBackgroundColor(LightColors.GetIntensityColorAsInt(Context, position));

        return item;
    }
}
