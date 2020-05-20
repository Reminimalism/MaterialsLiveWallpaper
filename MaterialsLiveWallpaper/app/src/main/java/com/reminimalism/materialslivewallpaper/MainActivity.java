package com.reminimalism.materialslivewallpaper;

import androidx.appcompat.app.AppCompatActivity;

import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GetDonationAddress();
    }

    public void OnSetWallpaperClick(View view)
    {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, MaterialsWallpaperService.class)
        );
        startActivity(intent);
    }

    public String DonationAddressLabel = "";
    public String DonationAddress = "";

    public void OnDonationAddressCopyClick(View view)
    {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null)
        {
            clipboard.setPrimaryClip(ClipData.newPlainText(DonationAddressLabel, DonationAddress));
            Toast.makeText(this, R.string.copy_successful, Toast.LENGTH_LONG).show();
        }
        else
            Toast.makeText(this, R.string.copy_failed, Toast.LENGTH_LONG).show();
    }

    public void OnDonationPageClick(View view)
    {
        startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://pragma-once.github.io/materialslivewallpaper/donation-page.html")
        ));
    }

    private static class GetDonationAddressTask extends AsyncTask<MainActivity, Void, Void>
    {
        @Override
        protected Void doInBackground(final MainActivity... params)
        {
            try
            {
                URL url = new URL("https://pragma-once.github.io/materialslivewallpaper/donation-address");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                int code = connection.getResponseCode();
                if (code == 200)
                {
                    InputStream stream = new BufferedInputStream(connection.getInputStream());
                    final String result = ReadInputStream(stream);
                    connection.disconnect();
                    params[0].runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            TextView address_text = params[0].findViewById(R.id.donation_address_text);
                            if (result == null || result.isEmpty())
                            {
                                address_text.setText(R.string.donation_address_text_failed);
                                return;
                            }
                            int c = result.lastIndexOf(':');
                            int s = c;
                            if (s != -1)
                            {
                                s++;
                                for (; s < result.length()
                                        && (result.charAt(s) == ' '
                                        || result.charAt(s) == '\t'
                                        || result.charAt(s) == '\n'); s++);
                                if (s < result.length())
                                {
                                    int e = s;
                                    for (; e < result.length()
                                            && result.charAt(e) != ' '
                                            && result.charAt(e) != '\t'
                                            && result.charAt(e) != '\n'; e++);
                                    int temp = e;
                                    for (; temp < result.length()
                                            && (result.charAt(temp) == ' '
                                            || result.charAt(temp) == '\t'
                                            || result.charAt(temp) == '\n'); temp++);
                                    if (temp == result.length())
                                    {
                                        params[0].DonationAddress = result.substring(s, e);
                                        params[0].DonationAddressLabel = result.substring(0, c);
                                        TextView copy_button = params[0].findViewById(R.id.donation_address_copy_button);
                                        copy_button.setEnabled(true);
                                    }
                                }
                            }
                            address_text.setText(result);
                        }
                    });
                    return null;
                }
                else connection.disconnect();
            }
            catch (MalformedURLException ignored) {}
            catch (IOException ignored) {}

            params[0].runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    TextView address_text = params[0].findViewById(R.id.donation_address_text);
                    address_text.setText(R.string.donation_address_text_failed);
                }
            });

            return null;
        }
    }

    void GetDonationAddress()
    {
        new GetDonationAddressTask().execute(this);
    }

    public static String ReadInputStream(InputStream stream)
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
