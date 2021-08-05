package com.reminimalism.materialslivewallpaper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Hashtable;

public class Config
{
    public String TargetVersion = null;

    public boolean PixelatedBase = false;
    public boolean PixelatedReflections = false;
    public boolean PixelatedNormal = false;
    public boolean PixelatedShininess = false;
    public boolean PixelatedBrush = false;
    public boolean PixelatedBrushIntensity = false;

    public boolean NormalizeNormal = true;

    public double DepthIntensity = 0.05;
    public double HeightIntensity = 0.05;
    public double FrameWithParallaxEnabled = 0.9;
    public double FrameWithParallaxDisabled = 1;

    public Config()
    {
        InitializeSupportedTargetVersions();
    }

    public Config(String Text)
    {
        InitializeSupportedTargetVersions();

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
        try { TargetVersion             = json.getString("TargetVersion");             } catch (JSONException ignored) {}
        try { PixelatedBase             = json.getBoolean("PixelatedBase");            } catch (JSONException ignored) {}
        try { PixelatedReflections      = json.getBoolean("PixelatedReflections");     } catch (JSONException ignored) {}
        try { PixelatedNormal           = json.getBoolean("PixelatedNormal");          } catch (JSONException ignored) {}
        try { PixelatedShininess        = json.getBoolean("PixelatedShininess");       } catch (JSONException ignored) {}
        try { PixelatedBrush            = json.getBoolean("PixelatedBrush");           } catch (JSONException ignored) {}
        try { PixelatedBrushIntensity   = json.getBoolean("PixelatedBrushIntensity");  } catch (JSONException ignored) {}
        try { NormalizeNormal           = json.getBoolean("NormalizeNormal");          } catch (JSONException ignored) {}
        try { DepthIntensity            = json.getDouble("DepthIntensity");            } catch (JSONException ignored) {}
        try { HeightIntensity           = json.getDouble("HeightIntensity");           } catch (JSONException ignored) {}
        try { FrameWithParallaxEnabled  = json.getDouble("FrameWithParallaxEnabled");  } catch (JSONException ignored) {}
        try { FrameWithParallaxDisabled = json.getDouble("FrameWithParallaxDisabled"); } catch (JSONException ignored) {}
    }

    public boolean IsTargetVersionSupported()
    {
        if (TargetVersion == null)
            return true;
        return SupportedTargetVersions.containsKey(TargetVersion);
    }

    public class Behavior
    {
        boolean DefaultBaseAndReflectionsToGray80 = false;
    }

    public Behavior GetBehavior()
    {
        Behavior result = new Behavior();
        if (TargetVersion == null || !SupportedTargetVersions.containsKey(TargetVersion))
            return result;
        result.DefaultBaseAndReflectionsToGray80 = SupportedTargetVersions.get(TargetVersion) == 1;
        return result;
    }

    // public boolean Is<Feature>Supported();

    public static String GetLatestSupportedCustomMaterialTargetVersion()
    {
        return "1.0"; // Shouldn't be updated until the material file format gets an update.
    }

    // Version name => Version code
    private static Hashtable<String, Integer> SupportedTargetVersions = null;
    private static void InitializeSupportedTargetVersions()
    {
        if (SupportedTargetVersions != null)
            return;
        SupportedTargetVersions = new Hashtable<>();
        SupportedTargetVersions.put("0.1", 1);
        SupportedTargetVersions.put("0.2", 2);
        SupportedTargetVersions.put("1.0", 3);
    }
}
