package com.reminimalism.materialslivewallpaper;

import android.service.wallpaper.WallpaperService;

public class MaterialsWallpaperService extends WallpaperService
{
    @Override
    public Engine onCreateEngine()
    {
        return new MaterialsWallpaperEngine();
    }

    private class MaterialsWallpaperEngine extends Engine
    {
    }
}
