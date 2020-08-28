package com.reminimalism.materialslivewallpaper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SettingsActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar action_bar = getSupportActionBar();
        if (action_bar != null)
            action_bar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
            onBackPressed();
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            findPreference("import_custom_material").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/zip");
                    intent = Intent.createChooser(intent, "Choose a zip file");
                    startActivityForResult(intent, 0);
                    return true;
                }
            });

            findPreference("light_colors").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    startActivity(new Intent(getContext(), LightColorsActivity.class));
                    return true;
                }
            });

            findPreference("donate_button").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://pragma-once.github.io/materialslivewallpaper/donation-page.html")
                    ));
                    return true;
                }
            });

            findPreference("more_materials_button").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference)
                {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/Reminimalism/MaterialsLiveWallpaperMaterialSamples")
                    ));
                    return true;
                }
            });

            try
            {
                findPreference("app_version").setSummary(getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName);
            }
            catch (Exception ignored)
            {
                findPreference("app_version").setSummary("Error getting version name");
            }

            findPreference("materials_version").setSummary(Config.GetLatestSupportedCustomMaterialTargetVersion());
        }
    }

    public static String GetCustomMaterialPath(Context context)
    {
        return context.getFilesDir().getAbsolutePath() + "/CustomMaterial/";
    }

    public enum CustomMaterialAssetType
    {
        Base,
        Reflections,
        Shininess,
        Normal,
        Brush,
        BrushIntensity,
        Config
    }

    public static String GetCustomMaterialAssetFilename(Context context, CustomMaterialAssetType asset_type)
    {
        String path = GetCustomMaterialPath(context);
        String name;
        switch (asset_type)
        {
            case Base:
                name = "base";
                break;
            case Reflections:
                name = "reflections";
                break;
            case Shininess:
                name = "shininess";
                break;
            case Normal:
                name = "normal";
                break;
            case Brush:
                name = "brush";
                break;
            case BrushIntensity:
                name = "brush_intensity";
                break;
            case Config:
                return path + "config.json";
            default:
                return null;
        }
        for (String extension : SupportedImageExtensions)
            if (new File(path + name + extension).exists())
                return path + name + extension;
        if (asset_type == CustomMaterialAssetType.Reflections)
        {
            name = "reflection";
            for (String extension : SupportedImageExtensions)
                if (new File(path + name + extension).exists())
                    return path + name + extension;
        }
        return null;
    }

    public static int MAX_POSSIBLE_ADDITIONAL_LAYERS = 4;

    public static LayerFilenames[] GetCustomMaterialAdditionalLayers(Context context)
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int max_layers;
        try
        {
            max_layers = Integer.parseInt(preferences.getString(
                    "max_additional_layers",
                    Integer.toString(MAX_POSSIBLE_ADDITIONAL_LAYERS)
            ));
        }
        catch (NumberFormatException ignored)
        {
            max_layers = MAX_POSSIBLE_ADDITIONAL_LAYERS;
        }
        String path = GetCustomMaterialPath(context);
        String name = "";
        LayerFilenames[] temp = new LayerFilenames[max_layers];
        int count = 0;
        LayerFilenames next = null;
        for (int i = 1; i <= max_layers; i++)
        {
            for (String extension : SupportedImageExtensions)
            {
                name = "base";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Base == null)
                        next.Base = path + name + i + extension;
                }
                name = "reflections";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Reflections == null)
                        next.Reflections = path + name + i + extension;
                }
                name = "reflection";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Reflections == null)
                        next.Reflections = path + name + i + extension;
                }
                name = "normal";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Normal == null)
                        next.Normal = path + name + i + extension;
                }
                name = "shininess";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Shininess == null)
                        next.Shininess = path + name + i + extension;
                }
                name = "brush";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.Brush == null)
                        next.Brush = path + name + i + extension;
                }
                name = "brush_intensity";
                if (new File(path + name + i + extension).exists())
                {
                    if (next == null)
                        next = new LayerFilenames();
                    if (next.BrushIntensity == null)
                        next.BrushIntensity = path + name + i + extension;
                }
            }

            if (next != null)
            {
                temp[count] = next;
                next = null;
                count++;
            }
        }

        LayerFilenames[] result = new LayerFilenames[count];
        System.arraycopy(temp, 0, result, 0, count);
        return result;
    }

    static void Delete(File file)
    {
        if (!file.exists())
            return;
        if (file.isDirectory())
            for (File f : file.listFiles())
                Delete(f);
        file.delete();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == -1)
        {
            // Trick to reinitialize wallpaper
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putInt("import_custom_material", new Random().nextInt());
            editor.apply();

            String CustomMaterialPath = GetCustomMaterialPath(this);
            File directory = new File(CustomMaterialPath);
            Delete(directory);
            directory.mkdir();

            // Unzip
            try
            {
                ZipInputStream zip = new ZipInputStream(getContentResolver().openInputStream(data.getData()));
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null)
                {
                    if (!entry.isDirectory())
                    {
                        if (IsZipContentSupported(entry.getName()))
                        {
                            FileOutputStream file = new FileOutputStream(CustomMaterialPath + entry.getName());
                            byte[] bytes = new byte[1024];
                            for (int length = zip.read(bytes); length != -1; length = zip.read(bytes))
                                file.write(bytes, 0, length);
                            file.close();
                            zip.closeEntry();
                        }
                    }
                }
                zip.close();

                Config Config = new Config(ReadTextFile( // Filename:
                        SettingsActivity.GetCustomMaterialAssetFilename(
                                this,
                                SettingsActivity.CustomMaterialAssetType.Config
                        )
                ));
                if (Config.IsTargetVersionSupported())
                    Toast.makeText(this, R.string.custom_material_imported, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(this, R.string.custom_material_imported_target_version_not_supported, Toast.LENGTH_LONG).show();
            }
            catch (FileNotFoundException ignored)
            {
                Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_LONG).show();
            }
            catch (IOException ignored)
            {
                Toast.makeText(this, R.string.unzip_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    static final String[] SupportedImageFilenames = {
            "base",
            "reflections",
            "reflection",
            "shininess",
            "normal",
            "brush",
            "brush_intensity"
    };

    static final String[] SupportedImageExtensions = {
            ".bmp",
            ".png",
            ".jpg",
            ".gif",
            ".webp"
    };

    static boolean IsZipContentSupported(String Filename)
    {
        if (Filename.equals("config.json"))
            return true;

        String file_extension = null;
        for (String extension : SupportedImageExtensions)
        {
            if (Filename.endsWith(extension))
            {
                file_extension = extension;
                break;
            }
        }
        if (file_extension != null)
            for (String name : SupportedImageFilenames)
            {
                if (Filename.equals(name + file_extension))
                    return true;
                for (int i = 1; i <= MAX_POSSIBLE_ADDITIONAL_LAYERS; i++)
                    if (Filename.equals(name + i + file_extension))
                        return true;
            }

        return false;
    }

    // Basic operations

    String ReadTextFile(String Filename)
    {
        try
        {
            return ReadInputStream(new FileInputStream(Filename));
        }
        catch (FileNotFoundException ignored) { return null; }
    }

    String ReadInputStream(InputStream stream)
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        StringBuilder builder = new StringBuilder();

        try
        {
            while ((line = reader.readLine()) != null)
            {
                builder.append(line);
                builder.append('\n');
            }
        } catch (IOException e) { return null; }

        return builder.toString();
    }
}
