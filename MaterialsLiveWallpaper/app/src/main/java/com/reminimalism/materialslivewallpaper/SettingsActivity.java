package com.reminimalism.materialslivewallpaper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
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
            String CustomMaterialPath = getFilesDir().getAbsolutePath() + "/CustomMaterial/";
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
            "reflection",
            "reflections",
            "shininess",
            "normal",
            "brush"
    };

    static final String[] SupportedImageFormats = {
            ".bmp",
            ".gif",
            ".jpg",
            ".png",
            ".webp"
    };

    static boolean IsZipContentSupported(String Filename)
    {
        if (Filename.equals("config.txt"))
            return true;

        String file_format = null;
        for (String format : SupportedImageFormats)
        {
            if (Filename.endsWith(format))
            {
                file_format = format;
                break;
            }
        }
        if (file_format != null)
            for (String name : SupportedImageFilenames)
                if (Filename.equals(name + file_format))
                    return true;

        return false;
    }
}
