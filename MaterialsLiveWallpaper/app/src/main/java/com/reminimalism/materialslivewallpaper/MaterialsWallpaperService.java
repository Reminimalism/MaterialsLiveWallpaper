package com.reminimalism.materialslivewallpaper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.service.wallpaper.WallpaperService;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MaterialsWallpaperService extends WallpaperService
{
    @Override
    public Engine onCreateEngine()
    {
        return new MaterialsWallpaperEngine();
    }

    private class MaterialsWallpaperEngine extends Engine
    {
        class WallpaperGLSurfaceView extends GLSurfaceView
        {
            WallpaperGLSurfaceView(Context context)
            {
                super(context);
            }

            @Override
            public SurfaceHolder getHolder()
            {
                return getSurfaceHolder();
            }

            public void onDestroy()
            {
                super.onDetachedFromWindow();
            }
        }

        SharedPreferences Preferences = null;
        SharedPreferences.OnSharedPreferenceChangeListener PreferenceChangeListener = null;

        SensorManager SensorManagerInstance = null;
        SensorEventListener RotationSensorEventListener = null;
        float[] RotationVector = new float[4];

        WallpaperGLSurfaceView GLSurface = null;
        boolean SettingsChanged = false;

        @Override
        public void onCreate(final SurfaceHolder surface_holder)
        {
            super.onCreate(surface_holder);
            GLSurface = new WallpaperGLSurfaceView(MaterialsWallpaperService.this);

            boolean SupportsGLES2 = false;
            try
            {
                SupportsGLES2 =
                        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                                .getDeviceConfigurationInfo().reqGlEsVersion >= 0x20000;
            }
            catch (NullPointerException ignored) {}

            if (SupportsGLES2)
            {
                // Sensors

                SensorManagerInstance = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                if (SensorManagerInstance != null)
                {
                    RotationSensorEventListener = new SensorEventListener()
                    {
                        @Override
                        public void onSensorChanged(SensorEvent event)
                        {
                            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR)
                                System.arraycopy(event.values, 0, RotationVector, 0, 4);
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                    };

                    Sensor RotationSensor = SensorManagerInstance.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                    SensorManagerInstance.registerListener(
                            RotationSensorEventListener,
                            RotationSensor,
                            SensorManager.SENSOR_DELAY_UI
                    );
                }
                // else do nothing

                // Preferences

                Preferences = PreferenceManager.getDefaultSharedPreferences(MaterialsWallpaperService.this);
                PreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener()
                {
                    @Override
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
                    {
                        SettingsChanged = true;
                    }
                };
                Preferences.registerOnSharedPreferenceChangeListener(PreferenceChangeListener);

                // Rendering

                GLSurface.setEGLContextClientVersion(2);
                GLSurface.setPreserveEGLContextOnPause(true);

                // ---------------------------------------------------------------- //

                GLSurface.setRenderer(
                        new GLSurfaceView.Renderer() // -------- RENDERER BEGIN -------- //
                {
                    FloatBuffer TriangleStripPositionValues;
                    //FloatBuffer TriangleFanPositionValues;

                    float[] DeviceRotationMatrix = new float[9];
                    float AspectRatio = 1;

                    boolean LimitFPS = false;
                    int FrameMinDuration_ms;
                    long Time;

                    float[] LightDirections;
                    float[] LightReflectionDirections;
                    float[] LightColors;

                    final String[] DefaultLightDirections = {
                            "0,0,1",
                            "0.8944271909999159,0,0.4472135954999579",
                            "0,0.8944271909999159,0.4472135954999579",
                            "-0.8944271909999159,0,0.4472135954999579",
                            "0,-0.8944271909999159,0.4472135954999579",
                            "0,0,-1",
                    };
                    final String[] DefaultLightColors = {
                            "0.6,0.6,0.6",
                            "0.6,0,0.234",   // 1.00f * 0.6f, 0.00f * 0.6f, 0.39f * 0.6f
                            "0.6,0.2,0.1",
                            "0,0.516,0.468", // 0.00f * 0.6f, 0.86f * 0.6f, 0.78f * 0.6f
                            "0.1,0.3,0.6",
                            "0.6,0.6,0.6",
                            "0.6,0.6,0.6",
                            "0.6,0.6,0.6",
                            "0.6,0.6,0.6",
                            "0.6,0.6,0.6",
                    };

                    class Layer
                    {
                        public boolean EnableBase = false;
                        public boolean EnableReflections = false;
                        public boolean EnableNormal = false;
                        public boolean EnableShininess = false;
                        public boolean EnableBrush = false;
                        public boolean EnableBrushIntensity = false;

                        public boolean EnableCircularBrush = false;

                        public int Program;

                        public int PositionAttribute;

                        public int ScreenFrontDirectionUniform;
                        public int ScreenUpDirectionUniform;
                        public int ScreenRightDirectionUniform;
                        public int FOVUniform;
                        public int UVScaleUniform;

                        public int LightDirectionsUniform;
                        public int LightReflectionDirectionsUniform;
                        public int LightColorsUniform;

                        public int BaseUniform;
                        public int ReflectionsUniform;
                        public int NormalUniform;
                        public int ShininessUniform;
                        public int BrushUniform;
                        public int BrushIntensityUniform;

                        public int BaseTexture;
                        public int ReflectionsTexture;
                        public int NormalTexture;
                        public int ShininessTexture;
                        public int BrushTexture;
                        public int BrushIntensityTexture;

                        public int BaseTextureID;
                        public int ReflectionsTextureID;
                        public int NormalTextureID;
                        public int ShininessTextureID;
                        public int BrushTextureID;
                        public int BrushIntensityTextureID;
                    }

                    Layer[] Layers;
                    int[] AllTextures = new int[30];
                    int AllTexturesCount = 0;

                    int GetTextureIDValue(int ID)
                    {
                        if (ID >= 0 && ID < 30)
                            return GLES20.GL_TEXTURE0 + ID;
                        throw new RuntimeException("Invalid texture ID");
                    }

                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config)
                    {
                        // Constant one-time data initializations

                        // Vertices

                        float[] TriangleStripArray = {
                                -1, -1, 0,
                                -1, +1, 0,
                                +1, -1, 0,
                                +1, +1, 0
                        };
                        TriangleStripPositionValues = ByteBuffer.allocateDirect(TriangleStripArray.length * 4)
                                .order(ByteOrder.nativeOrder()).asFloatBuffer();
                        TriangleStripPositionValues.put(TriangleStripArray).position(0);

                        /*float[] TriangleFanArray = {
                                 0,  0, 0,
                                -1, +1, 0,
                                +1, +1, 0,
                                +1, -1, 0,
                                -1, -1, 0,
                                -1, +1, 0
                        };
                        TriangleFanPositionValues = ByteBuffer.allocateDirect(TriangleFanArray.length * 4)
                                .order(ByteOrder.nativeOrder()).asFloatBuffer();
                        TriangleFanPositionValues.put(TriangleFanArray).position(0);*/

                        Initialize();
                    }

                    void Initialize()
                    {
                        SettingsChanged = false;

                        boolean UseCustomMaterial = Preferences.getBoolean("use_custom_material", false);

                        Config Config = null;
                        if (UseCustomMaterial)
                        {
                            Config = new Config(ReadTextFile( // Filename:
                                    SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Config
                                    )
                            ));
                        }
                        else
                            Config = new Config();

                        Config.Behavior Behavior = Config.GetBehavior();

                        // Frame Rate Limit

                        String FPSLimit = Preferences.getString("fps_limit", "none");

                        if (FPSLimit.equals("none"))
                            LimitFPS = false;
                        else try
                        {
                            FrameMinDuration_ms = 1000 / Integer.parseInt(FPSLimit);
                            LimitFPS = true;
                        }
                        catch (NumberFormatException ignored)
                        {
                            LimitFPS = false;
                        }

                        // Lights

                        Set<String> set = Preferences.getStringSet("light_directions", new HashSet<>(Arrays.asList(DefaultLightDirections)));
                        LightDirections = new float[set.size() * 3];
                        int count = 0;
                        for (String str : set)
                        {
                            try
                            {
                                String[] coord = str.split(",");
                                LightDirections[count * 3] = Float.parseFloat(coord[0]);
                                LightDirections[count * 3 + 1] = Float.parseFloat(coord[1]);
                                LightDirections[count * 3 + 2] = Float.parseFloat(coord[2]);
                            }
                            catch (Exception ignored)
                            {
                                LightDirections[count * 3] = 0;
                                LightDirections[count * 3 + 1] = 0;
                                LightDirections[count * 3 + 2] = 1;
                            }
                            count++;
                        }

                        LightColors = new float[count * 3];
                        for (int i = 0; i < count; i++)
                        {
                            String str = Preferences.getString("light_color_" + i, DefaultLightColors[i]);
                            try
                            {
                                String[] color = str.split(",");
                                LightColors[i * 3] = Float.parseFloat(color[0]);
                                LightColors[i * 3 + 1] = Float.parseFloat(color[1]);
                                LightColors[i * 3 + 2] = Float.parseFloat(color[2]);
                            }
                            catch (Exception ignored)
                            {
                                LightDirections[i * 3] = 0.6f;
                                LightDirections[i * 3 + 1] = 0.6f;
                                LightDirections[i * 3 + 2] = 0.6f;
                            }
                        }

                        LightReflectionDirections = new float[LightDirections.length]; // Will be updated based on LightDirections & device rotation

                        // GL setup

                        GLES20.glClearColor(0, 0, 0, 1);

                        // Vertex shader setup

                        int VertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
                        if (VertexShader != 0)
                        {
                            GLES20.glShaderSource(VertexShader, ReadRawTextResource(R.raw.vertex_shader));
                            GLES20.glCompileShader(VertexShader);

                            int[] CompileStatus = new int[1];
                            GLES20.glGetShaderiv(VertexShader, GLES20.GL_COMPILE_STATUS, CompileStatus, 0);
                            if (CompileStatus[0] == 0)
                            {
                                String log = GLES20.glGetShaderInfoLog(VertexShader);
                                GLES20.glDeleteShader(VertexShader);
                                throw new RuntimeException("Vertex shader compilation failed. " + log);
                            }
                        }
                        else throw new RuntimeException("Vertex shader creation failed.");

                        // Layers

                        LayerFilenames[] layers_f;
                        if (UseCustomMaterial)
                            layers_f = SettingsActivity.GetCustomMaterialAdditionalLayers(MaterialsWallpaperService.this);
                        else
                            layers_f = new LayerFilenames[0];
                        Layers = new Layer[1 + layers_f.length];
                        AllTexturesCount = 0;
                        for (int i = -1; i < layers_f.length; i++)
                        {
                            boolean load_from_resources = false;
                            LayerFilenames layer_f;
                            Layer current_layer = new Layer();

                            if (i == -1)
                            {
                                layer_f = new LayerFilenames();
                                if (UseCustomMaterial)
                                {
                                    layer_f.Base = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Base
                                    );
                                    layer_f.Reflections = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Reflections
                                    );
                                    layer_f.Normal = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Normal
                                    );
                                    layer_f.Shininess = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Shininess
                                    );
                                    layer_f.Brush = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Brush
                                    );
                                    layer_f.BrushIntensity = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.BrushIntensity
                                    );
                                    current_layer.EnableBase =
                                            Behavior.DefaultBaseAndReflectionsToGray80 // Backward compatibility
                                            || layer_f.Base != null;
                                    current_layer.EnableReflections =
                                            Behavior.DefaultBaseAndReflectionsToGray80 // Backward compatibility
                                            || layer_f.Reflections != null;
                                    current_layer.EnableNormal         = layer_f.Normal         != null;
                                    current_layer.EnableShininess      = layer_f.Shininess      != null;
                                    current_layer.EnableBrush          = layer_f.Brush          != null;
                                    current_layer.EnableBrushIntensity = layer_f.BrushIntensity != null;
                                }
                                else
                                {
                                    String  MaterialSample = Preferences.getString("material_sample", "flat_poly");
                                    current_layer.EnableCircularBrush = MaterialSample.equals("circular_brush");

                                    load_from_resources = true;
                                    int BaseR        = R.drawable.gray_80_128_16x16;
                                    int ReflectionsR = R.drawable.gray_80_128_16x16;
                                    int NormalR      = R.drawable.flat_normal_16x16;
                                    int ShininessR   = R.drawable.black_16x16;
                                    int BrushR       = R.drawable.gray_80_128_16x16;
                                    switch (MaterialSample)
                                    {
                                        case "circular_brush":
                                            ShininessR = R.drawable.black_16x16;
                                            break;
                                        case "brushed_tiles":
                                            BaseR = R.drawable.tiles_base;
                                            ReflectionsR = R.drawable.tiles_reflections;
                                            ShininessR = R.drawable.tiles_shininess;
                                            BrushR = R.drawable.tiles_brush;
                                            break;
                                        case "flat_poly":
                                            BaseR = R.drawable.flat_poly_base;
                                            ReflectionsR = R.drawable.poly_reflections;
                                            ShininessR = R.drawable.poly_shininess;
                                            BrushR = R.drawable.poly_brush;
                                            break;
                                        case "poly":
                                            BaseR = R.drawable.poly_base;
                                            ReflectionsR = R.drawable.poly_reflections;
                                            NormalR = R.drawable.poly_normal;
                                            ShininessR = R.drawable.poly_shininess;
                                            BrushR = R.drawable.poly_brush;
                                            break;
                                    }

                                    layer_f.Base        = Integer.toString(BaseR);
                                    layer_f.Reflections = Integer.toString(ReflectionsR);
                                    layer_f.Normal      = Integer.toString(NormalR);
                                    layer_f.Shininess   = Integer.toString(ShininessR);
                                    layer_f.Brush       = Integer.toString(BrushR);
                                    current_layer.EnableBase        = true;
                                    current_layer.EnableReflections = true;
                                    current_layer.EnableNormal      = true;
                                    current_layer.EnableShininess   = true;
                                    current_layer.EnableBrush       = true;
                                    current_layer.EnableBrushIntensity = false;
                                }
                            }
                            else
                            {
                                layer_f = layers_f[i];
                                current_layer.EnableBase           = layer_f.Base           != null;
                                current_layer.EnableReflections    = layer_f.Reflections    != null;
                                current_layer.EnableNormal         = layer_f.Normal         != null;
                                current_layer.EnableShininess      = layer_f.Shininess      != null;
                                current_layer.EnableBrush          = layer_f.Brush          != null;
                                current_layer.EnableBrushIntensity = layer_f.BrushIntensity != null;
                            }

                            // Fragment shader setup

                            int FragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
                            if (FragmentShader != 0)
                            {
                                GLES20.glShaderSource(
                                        FragmentShader,
                                        "#define LIGHTS_COUNT " + (LightDirections.length / 3) + "\n"
                                                + (current_layer.EnableCircularBrush ? "#define ENABLE_CIRCULAR_BRUSH 1\n" : "")
                                                + "#define ENABLE_NORMAL_NORMALIZATION " + (Config.NormalizeNormal ? "1\n" : "0\n")
                                                + "#define ENABLE_BASE "            + (current_layer.EnableBase ? "1\n" : "0\n")
                                                + "#define ENABLE_REFLECTIONS "     + (current_layer.EnableReflections ? "1\n" : "0\n")
                                                + "#define ENABLE_NORMAL "          + (current_layer.EnableNormal ? "1\n" : "0\n")
                                                + "#define ENABLE_SHININESS "       + (current_layer.EnableShininess ? "1\n" : "0\n")
                                                + "#define ENABLE_BRUSH "           + (current_layer.EnableBrush ? "1\n" : "0\n")
                                                + "#define ENABLE_BRUSH_INTENSITY " + (current_layer.EnableBrushIntensity ? "1\n" : "0\n")
                                                + ReadRawTextResource(R.raw.fragment_shader)
                                );
                                GLES20.glCompileShader(FragmentShader);

                                final int[] CompileStatus = new int[1];
                                GLES20.glGetShaderiv(FragmentShader, GLES20.GL_COMPILE_STATUS, CompileStatus, 0);

                                if (CompileStatus[0] == 0)
                                {
                                    String log = GLES20.glGetShaderInfoLog(FragmentShader);
                                    GLES20.glDeleteShader(FragmentShader);
                                    throw new RuntimeException("Fragment shader compilation failed. " + log);
                                }
                            }
                            else throw new RuntimeException("Fragment shader creation failed.");

                            // Program setup

                            current_layer.Program = GLES20.glCreateProgram();
                            if (current_layer.Program != 0)
                            {
                                GLES20.glAttachShader(current_layer.Program, VertexShader);
                                GLES20.glAttachShader(current_layer.Program, FragmentShader);

                                GLES20.glBindAttribLocation(current_layer.Program, 0, "Position");

                                GLES20.glLinkProgram(current_layer.Program);

                                final int[] LinkStatus = new int[1];
                                GLES20.glGetProgramiv(current_layer.Program, GLES20.GL_LINK_STATUS, LinkStatus, 0);

                                if (LinkStatus[0] == 0)
                                {
                                    GLES20.glDeleteProgram(current_layer.Program);
                                    throw new RuntimeException("Program link failed.");
                                }
                            }
                            else throw new RuntimeException("Program creation failed.");

                            current_layer.PositionAttribute = GLES20.glGetAttribLocation(current_layer.Program, "Position");

                            current_layer.ScreenFrontDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenFrontDirection");
                            current_layer.ScreenUpDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenUpDirection");
                            current_layer.ScreenRightDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenRightDirection");
                            current_layer.FOVUniform = GLES20.glGetUniformLocation(current_layer.Program, "FOV");
                            current_layer.UVScaleUniform = GLES20.glGetUniformLocation(current_layer.Program, "UVScale");

                            current_layer.LightDirectionsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightDirections");
                            current_layer.LightReflectionDirectionsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightReflectionDirections");
                            current_layer.LightColorsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightColors");

                            if (current_layer.EnableBase)
                                current_layer.BaseUniform = GLES20.glGetUniformLocation(current_layer.Program, "BaseColor");
                            if (current_layer.EnableReflections)
                                current_layer.ReflectionsUniform = GLES20.glGetUniformLocation(current_layer.Program, "ReflectionsColor");
                            if (current_layer.EnableNormal)
                                current_layer.NormalUniform = GLES20.glGetUniformLocation(current_layer.Program, "Normal");
                            if (current_layer.EnableShininess)
                                current_layer.ShininessUniform = GLES20.glGetUniformLocation(current_layer.Program, "Shininess");
                            if (current_layer.EnableBrush && !current_layer.EnableCircularBrush)
                                current_layer.BrushUniform = GLES20.glGetUniformLocation(current_layer.Program, "Brush");
                            if (current_layer.EnableBrushIntensity)
                                current_layer.BrushIntensityUniform = GLES20.glGetUniformLocation(current_layer.Program, "BrushIntensity");

                            // Textures

                            if (load_from_resources)
                            {
                                if (current_layer.EnableBase)
                                    current_layer.BaseTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Base), false);
                                if (current_layer.EnableReflections)
                                    current_layer.ReflectionsTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Reflections), false);
                                if (current_layer.EnableNormal)
                                    current_layer.NormalTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Normal), false);
                                if (current_layer.EnableShininess)
                                    current_layer.ShininessTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Shininess), false);
                                if (current_layer.EnableBrush)
                                    current_layer.BrushTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Brush), false);
                                if (current_layer.EnableBrushIntensity)
                                    current_layer.BrushIntensityTexture = LoadTextureFromResource(Integer.parseInt(layer_f.BrushIntensity), false);
                            }
                            else
                            {
                                boolean BasePixelated = Config.PixelatedBase;
                                boolean ReflectionsPixelated = Config.PixelatedReflections;
                                boolean NormalPixelated = Config.PixelatedNormal;
                                boolean ShininessPixelated = Config.PixelatedShininess;
                                boolean BrushPixelated = Config.PixelatedBrush;
                                boolean BrushIntensityPixelated = Config.PixelatedBrushIntensity;

                                if (current_layer.EnableBase)
                                    current_layer.BaseTexture = LoadTextureFromFile(
                                            layer_f.Base,
                                            R.drawable.gray_80_128_16x16, // Backward compatibility
                                            BasePixelated
                                    );
                                if (current_layer.EnableReflections)
                                    current_layer.ReflectionsTexture = LoadTextureFromFile(
                                            layer_f.Reflections,
                                            R.drawable.gray_80_128_16x16, // Backward compatibility
                                            ReflectionsPixelated
                                    );
                                if (current_layer.EnableNormal)
                                    current_layer.NormalTexture = LoadTextureFromFile(
                                            layer_f.Normal,
                                            R.drawable.flat_normal_16x16,
                                            NormalPixelated
                                    );
                                if (current_layer.EnableShininess)
                                    current_layer.ShininessTexture = LoadTextureFromFile(
                                            layer_f.Shininess,
                                            R.drawable.black_16x16,
                                            ShininessPixelated
                                    );
                                if (current_layer.EnableBrush)
                                    current_layer.BrushTexture = LoadTextureFromFile(
                                            layer_f.Brush,
                                            R.drawable.gray_80_128_16x16,
                                            BrushPixelated
                                    );
                                if (current_layer.EnableBrushIntensity)
                                    current_layer.BrushIntensityTexture = LoadTextureFromFile(
                                            layer_f.BrushIntensity,
                                            R.drawable.white_16x16,
                                            BrushIntensityPixelated
                                    );
                            }

                            if (current_layer.EnableBase)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.BaseTexture);
                                current_layer.BaseTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.BaseTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableReflections)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.ReflectionsTexture);
                                current_layer.ReflectionsTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.ReflectionsTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableNormal)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.NormalTexture);
                                current_layer.NormalTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.NormalTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableShininess)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.ShininessTexture);
                                current_layer.ShininessTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.ShininessTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableBrush)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.BrushTexture);
                                current_layer.BrushTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.BrushTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableBrushIntensity)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(AllTexturesCount));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, current_layer.BrushIntensityTexture);
                                current_layer.BrushIntensityTextureID = AllTexturesCount;
                                AllTextures[AllTexturesCount] = current_layer.BrushIntensityTexture;
                                AllTexturesCount++;
                            }

                            Layers[i + 1] = current_layer; // i starts from -1
                        }

                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

                        GetTimeDiff_ms(); // Update Time
                    }

                    void Reinitialize()
                    {
                        for (Layer layer : Layers)
                            GLES20.glDeleteProgram(layer.Program);
                        GLES20.glDeleteTextures(AllTexturesCount, AllTextures, 0);
                        Initialize();
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height)
                    {
                        GLES20.glViewport(0, 0, width, height);
                        AspectRatio = (float)width / (float)height;
                    }

                    int GetTimeDiff_ms()
                    {
                        long CurrentTime = System.currentTimeMillis();
                        long Diff = CurrentTime - Time;
                        Time = CurrentTime;
                        if (Diff > 0)
                            return (int)Diff;
                        else
                            return 0;
                    }

                    @Override
                    public void onDrawFrame(GL10 gl)
                    {
                        if (SettingsChanged)
                            Reinitialize();

                        if (LimitFPS)
                        {
                            int remaining = FrameMinDuration_ms - GetTimeDiff_ms();
                            if (remaining > 0) try
                            {
                                Thread.sleep(remaining);
                            }
                            catch (InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                            }
                            GetTimeDiff_ms(); // Update Time
                        }

                        // Calculations

                        SensorManager.getRotationMatrixFromVector(DeviceRotationMatrix, RotationVector);
                        int rotation = ((WindowManager)getSystemService(Context.WINDOW_SERVICE))
                                .getDefaultDisplay()
                                .getRotation();
                        boolean SwapUpAndRight = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
                        int UpIndexOffset = SwapUpAndRight ? 0 : 1;
                        int RightIndexOffset = SwapUpAndRight ? 1 : 0;
                        float UpFinalCoefficient = rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_180 ? -1 : 1;
                        float RightFinalCoefficient = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180 ? -1 : 1;

                        float UVScaleX, UVSCaleY;
                        if (AspectRatio < 1) { UVScaleX = AspectRatio; UVSCaleY = 1; }
                        else { UVSCaleY = 1 / AspectRatio; UVScaleX = 1; }

                        // Make lights 2x closer to ScreenFrontDirection
                        for (int i = 0; i < LightDirections.length; i++)
                        {
                            LightReflectionDirections[i]
                                    = (
                                    DeviceRotationMatrix[(i % 3) * 3 + 2]
                                            + LightDirections[i]
                            ) / 2.0f;
                        }
                        // Normalize
                        for (int i = 0; i < LightReflectionDirections.length; i += 3)
                        {
                            float l = LightReflectionDirections[i] * LightReflectionDirections[i];
                            l += LightReflectionDirections[i + 1] * LightReflectionDirections[i + 1];
                            l += LightReflectionDirections[i + 2] * LightReflectionDirections[i + 2];
                            l = (float) Math.sqrt(l);
                            if (l != 0)
                            {
                                LightReflectionDirections[i] /= l;
                                LightReflectionDirections[i + 1] /= l;
                                LightReflectionDirections[i + 2] /= l;
                            }
                        }

                        // GL

                        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

                        for (Layer layer : Layers)
                        {
                            GLES20.glUseProgram(layer.Program);

                            GLES20.glVertexAttribPointer(
                                    layer.PositionAttribute,
                                    3,
                                    GLES20.GL_FLOAT,
                                    false,
                                    0,
                                    TriangleStripPositionValues
                                    //TriangleFanPositionValues
                            );
                            GLES20.glEnableVertexAttribArray(layer.PositionAttribute);

                            GLES20.glUniform3f(
                                    layer.ScreenFrontDirectionUniform,
                                    DeviceRotationMatrix[2],
                                    DeviceRotationMatrix[5],
                                    DeviceRotationMatrix[8]
                            );
                            GLES20.glUniform3f(
                                    layer.ScreenUpDirectionUniform,
                                    DeviceRotationMatrix[UpIndexOffset] * UpFinalCoefficient,
                                    DeviceRotationMatrix[3 + UpIndexOffset] * UpFinalCoefficient,
                                    DeviceRotationMatrix[6 + UpIndexOffset] * UpFinalCoefficient
                            );
                            GLES20.glUniform3f(
                                    layer.ScreenRightDirectionUniform,
                                    DeviceRotationMatrix[RightIndexOffset] * RightFinalCoefficient,
                                    DeviceRotationMatrix[3 + RightIndexOffset] * RightFinalCoefficient,
                                    DeviceRotationMatrix[6 + RightIndexOffset] * RightFinalCoefficient
                            );

                            GLES20.glUniform2f(layer.FOVUniform, 0.1f * AspectRatio, 0.1f);

                            GLES20.glUniform2f(layer.UVScaleUniform, UVScaleX, UVSCaleY);

                            GLES20.glUniform3fv(
                                    layer.LightDirectionsUniform,
                                    LightDirections.length / 3,
                                    LightDirections,
                                    0
                            );
                            GLES20.glUniform3fv(
                                    layer.LightReflectionDirectionsUniform,
                                    LightReflectionDirections.length / 3,
                                    LightReflectionDirections,
                                    0
                            );
                            GLES20.glUniform3fv(
                                    layer.LightColorsUniform,
                                    LightColors.length / 3,
                                    LightColors,
                                    0
                            );

                            // Textures
                            if (layer.EnableBase)
                                GLES20.glUniform1i(layer.BaseUniform, layer.BaseTextureID);
                            if (layer.EnableReflections)
                                GLES20.glUniform1i(layer.ReflectionsUniform, layer.ReflectionsTextureID);
                            if (layer.EnableNormal)
                                GLES20.glUniform1i(layer.NormalUniform, layer.NormalTextureID);
                            if (layer.EnableShininess)
                                GLES20.glUniform1i(layer.ShininessUniform, layer.ShininessTextureID);
                            if (layer.EnableBrush)
                                GLES20.glUniform1i(layer.BrushUniform, layer.BrushTextureID);
                            if (layer.EnableBrushIntensity)
                                GLES20.glUniform1i(layer.BrushIntensityUniform, layer.BrushIntensityTextureID);

                            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                            //GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 6);
                        }
                    }

                    int LoadTextureFromFile(String path, int default_texture_resource_id, boolean pixelated)
                    {
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        if (bitmap == null)
                            return LoadTextureFromResource(default_texture_resource_id, pixelated);
                        else
                            return LoadTexture(bitmap, pixelated);
                    }

                    int LoadTextureFromResource(int id, boolean pixelated)
                    {
                        Drawable drawable = getResources().getDrawable(id);

                        Bitmap bitmap = null;
                        if (drawable instanceof VectorDrawable) // Requires API 21 (5.0, Lollipop)
                        {
                            int width, height;
                            int aspect_ratio = drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
                            if (aspect_ratio < 1)
                            {
                                height = 2048;
                                width = 2048 * aspect_ratio;
                            }
                            else
                            {
                                width = 2048;
                                height = 2048 / aspect_ratio;
                            }
                            bitmap = Bitmap.createBitmap(
                                    width,
                                    height,
                                    Bitmap.Config.ARGB_8888
                            );
                            Canvas canvas = new Canvas(bitmap);
                            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                            drawable.draw(canvas);
                        }
                        else
                        {
                            BitmapFactory.Options bitmap_options = new BitmapFactory.Options();
                            bitmap_options.inScaled = false;
                            bitmap = BitmapFactory.decodeResource(getResources(), id, bitmap_options);
                        }

                        if (bitmap == null)
                            throw new RuntimeException("Drawable load failed.");

                        try
                        {
                            int result = LoadTexture(bitmap, pixelated);
                            bitmap.recycle();
                            return result;
                        }
                        catch (Exception e)
                        {
                            bitmap.recycle();
                            throw e;
                        }
                    }

                    int LoadTexture(Bitmap bitmap, boolean pixelated)
                    {
                        int[] texture = { 0 };
                        GLES20.glGenTextures(1, texture, 0);
                        if (texture[0] != 0)
                        {
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
                            int value = pixelated ? GLES20.GL_NEAREST : GLES20.GL_LINEAR;
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, value);
                            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, value);
                            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                        }
                        if (texture[0] == 0)
                            throw new RuntimeException("Texture load failed.");
                        return texture[0];
                    }

                    String ReadRawTextResource(int id)
                    {
                        return ReadInputStream(getResources().openRawResource(id));
                    }

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
                });  // -------- RENDERER END -------- //

                // ---------------------------------------------------------------- //

            }
            // else do nothing
        }

        @Override
        public void onVisibilityChanged(boolean visible)
        {
            super.onVisibilityChanged(visible);
            if (GLSurface != null)
                if (visible)
                    GLSurface.onResume();
                else
                    GLSurface.onPause();
        }

        @Override
        public void onDestroy()
        {
            super.onDestroy();
            if (GLSurface != null)
                GLSurface.onDestroy();
            if (SensorManagerInstance != null && RotationSensorEventListener != null)
                SensorManagerInstance.unregisterListener(RotationSensorEventListener);
            if (Preferences != null && PreferenceChangeListener != null)
                Preferences.unregisterOnSharedPreferenceChangeListener(PreferenceChangeListener);
        }
    }
}
