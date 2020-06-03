package com.reminimalism.materialslivewallpaper;

import org.json.JSONException;
import org.json.JSONObject;

public class Config
{
    public String TargetVersion = null;

    public boolean PixelatedBase = false;
    public boolean PixelatedReflections = false;
    public boolean PixelatedNormal = false;
    public boolean PixelatedShininess = false;
    public boolean PixelatedBrush = false;

    public boolean NormalizeNormal = true;

    public Config() {}

    public Config(String Text)
    {
        if (Text == null)
            return;

        try
        {
            JSONObject json = new JSONObject(Text);
            ReadJSON(json);
        }
        catch (JSONException ignored) {}
    }

    private void ReadJSON(JSONObject json)
    {
        try { TargetVersion        = json.getString("TargetVersion");         } catch (JSONException ignored) {}
        try { PixelatedBase        = json.getBoolean("PixelatedBase");        } catch (JSONException ignored) {}
        try { PixelatedReflections = json.getBoolean("PixelatedReflections"); } catch (JSONException ignored) {}
        try { PixelatedNormal      = json.getBoolean("PixelatedNormal");      } catch (JSONException ignored) {}
        try { PixelatedShininess   = json.getBoolean("PixelatedShininess");   } catch (JSONException ignored) {}
        try { PixelatedBrush       = json.getBoolean("PixelatedBrush");       } catch (JSONException ignored) {}
        try { NormalizeNormal      = json.getBoolean("NormalizeNormal");      } catch (JSONException ignored) {}
    }
}
