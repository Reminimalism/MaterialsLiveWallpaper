package com.reminimalism.materialslivewallpaper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
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
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference)
        {
            if (preference instanceof LightSourcePreference)
            {
                DialogFragment dialog = LightSourcePreferenceDialogFragmentCompat.newInstance(preference.getKey());
                dialog.setTargetFragment(this, 0);
                dialog.show(getParentFragmentManager(), "LightSourcePreference");
            }
            else super.onDisplayPreferenceDialog(preference);
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
                            for (int b = zip.read(); b != -1; b = zip.read())
                                file.write(b);
                            file.close();
                            zip.closeEntry();
                        }
                    }
                }
                zip.close();
                Toast.makeText(this, R.string.custom_material_imported, Toast.LENGTH_LONG).show();
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
            ".gif",
            ".jpg",
            ".png",
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
                if (Filename.equals(name + file_extension))
                    return true;

        return false;
    }
}
