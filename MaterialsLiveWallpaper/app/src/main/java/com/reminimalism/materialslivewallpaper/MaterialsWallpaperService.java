package com.reminimalism.materialslivewallpaper;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import java.io.PrintWriter;
import java.io.StringWriter;
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
        boolean RotationVectorChanged = false;

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
                            {
                                System.arraycopy(event.values, 0, RotationVector, 0, 4);
                                RotationVectorChanged = true;
                            }
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                    };
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
                    float[] ScreenFrontDirection = new float[3];
                    float[] ScreenUpDirection = new float[3];
                    float[] ScreenRightDirection = new float[3];
                    float AspectRatio = 1;

                    boolean LimitFPS = false;
                    int FrameMinDuration_ms;
                    long Time;

                    float[] LightDirections;
                    float[] LightReflectionDirections;
                    float[] LightColors;

                    float Exposure;

                    final String[] DefaultLightDirections = {
                            "0,0,1",
                            "0.8944271909999159,0,0.4472135954999579",
                            "0,0.8944271909999159,0.4472135954999579",
                            "-0.8944271909999159,0,0.4472135954999579",
                            "0,-0.8944271909999159,0.4472135954999579",
                            "0,0,-1",
                    };

                    class Layer
                    {
                        public boolean EnableBase = false;
                        public boolean EnableReflections = false;
                        public boolean EnableNormal = false;
                        public boolean EnableShininess = false;
                        public boolean EnableBrush = false;
                        public boolean EnableBrushIntensity = false;
                        public boolean EnableDepth = false;
                        public boolean EnableHeight = false;

                        public boolean EnableParallax = false;

                        public boolean EnableCircularBrush = false;

                        public int Program;

                        public int PositionAttribute;

                        public int ScreenFrontDirectionUniform;
                        public int ScreenUpDirectionUniform;
                        public int ScreenRightDirectionUniform;
                        public int FOVUniform;
                        public int UVScaleUniform;

                        public int ViewerUniform;

                        public int LightDirectionsUniform;
                        public int LightReflectionDirectionsUniform;
                        public int LightColorsUniform;

                        public int BaseUniform;
                        public int ReflectionsUniform;
                        public int NormalUniform;
                        public int ShininessUniform;
                        public int BrushUniform;
                        public int BrushIntensityUniform;
                        public int DepthUniform;
                        public int HeightUniform;

                        public int DepthIntensityUniform;
                        public int HeightIntensityUniform;

                        public int ExposureUniform;

                        public int BaseTexture;
                        public int ReflectionsTexture;
                        public int NormalTexture;
                        public int ShininessTexture;
                        public int BrushTexture;
                        public int BrushIntensityTexture;
                        public int DepthTexture;
                        public int HeightTexture;

                        public float DepthIntensity;
                        public float HeightIntensity;

                        public int[] SamplerUniforms = null;
                        public int[] Textures = null;
                    }

                    Layer[] Layers = null;
                    int[] AllTextures = new int[30];
                    int AllTexturesCount = 0;
                    int[] AllShaders = new int[2 + SettingsActivity.MAX_POSSIBLE_ADDITIONAL_LAYERS];
                    int AllShadersCount = 0;

                    // 0..n: left-to-right digits
                    // positions:
                    // 0  1  2
                    // 3  4  5
                    // 6  7  8
                    // 9  10 11
                    // 12 13 14
                    FloatBuffer[][] FPSCounterTriangleStripPositionValues;
                    int[][] FPSCounterNumbersToPositionIndexes;
                    float[] FPSCounterColor = { 1, 1, 1 };
                    class SolidTriangleProgram
                    {
                        public int Program;
                        public int PositionAttribute;
                        public int ColorUniform;
                    }
                    SolidTriangleProgram FPSCounter = null;

                    float FPS = 0;

                    boolean SmoothOutRotationMatrix = true;
                    float[] DeviceRotationMatrixA = new float[9];
                    float[] DeviceRotationMatrixB = new float[9];
                    long DeviceRotationMatrixATime_ms = 0;
                    long DeviceRotationMatrixBTime_ms = 0;
                    long DeviceRotationMatrixCTime_ms = 1;
                    long DeviceRotationMatrixABTimeDiff_ms = 1;

                    boolean UseViewerForReflections = false;
                    boolean EnableParallax = true;

                    float[] ViewerPosition = { 0, 0, 1 };

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

                        final float DIGITS_DISTANCE = 0.1f;
                        final float DIGIT_FRAGMENT_SIZE = DIGITS_DISTANCE / 4;
                        final float FIRST_DIGIT_POSITION_X = 0 - DIGITS_DISTANCE * (1 + 0.5f * 3 / 4); // Top-left pos
                        final float FIRST_DIGIT_POSITION_Y = 0 + DIGITS_DISTANCE * (0.5f * 5 / 4); // Top-left pos
                        FPSCounterTriangleStripPositionValues = new FloatBuffer[3][];
                        for (int i = 0; i < 3; i++)
                        {
                            FPSCounterTriangleStripPositionValues[i] = new FloatBuffer[15];
                            float pos_x = FIRST_DIGIT_POSITION_X + i * DIGITS_DISTANCE; // Top-left pos
                            float pos_y = FIRST_DIGIT_POSITION_Y; // Top-left pos
                            int counter = 0;
                            for (int k = 0; k < 5; k++)
                            {
                                for (int j = 0; j < 3; j++)
                                {
                                    float min_x = pos_x + j * DIGIT_FRAGMENT_SIZE;
                                    float max_x = pos_x + (j + 1) * DIGIT_FRAGMENT_SIZE;
                                    float max_y = pos_y - k * DIGIT_FRAGMENT_SIZE;
                                    float min_y = pos_y - (k + 1) * DIGIT_FRAGMENT_SIZE;
                                    float[] arr = {
                                            min_x, min_y, 0,
                                            min_x, max_y, 0,
                                            max_x, min_y, 0,
                                            max_x, max_y, 0
                                    };
                                    FPSCounterTriangleStripPositionValues[i][counter] = ByteBuffer.allocateDirect(TriangleStripArray.length * 4)
                                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                                    FPSCounterTriangleStripPositionValues[i][counter].put(arr).position(0);
                                    counter++;
                                }
                            }
                        }
                        FPSCounterNumbersToPositionIndexes = new int[10][];
                        /*
                        FPSCounterNumbersToPositionIndexes[TEMPLATE] = new int[] {
                                0,  1,  2,
                                3,  4,  5,
                                6,  7,  8,
                                9,  10, 11,
                                12, 13, 14
                        };
                        */
                        FPSCounterNumbersToPositionIndexes[0] = new int[] {
                                0,  1,  2,
                                3,      5,
                                6,      8,
                                9,      11,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[1] = new int[] {
                                0,  1,
                                    4,
                                    7,
                                    10,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[2] = new int[] {
                                0,  1,  2,
                                        5,
                                6,  7,  8,
                                9,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[3] = new int[] {
                                0,  1,  2,
                                        5,
                                6,  7,  8,
                                        11,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[4] = new int[] {
                                0,      2,
                                3,      5,
                                6,  7,  8,
                                        11,
                                        14
                        };
                        FPSCounterNumbersToPositionIndexes[5] = new int[] {
                                0,  1,  2,
                                3,
                                6,  7,  8,
                                        11,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[6] = new int[] {
                                0,  1,  2,
                                3,
                                6,  7,  8,
                                9,      11,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[7] = new int[] {
                                0,  1,  2,
                                        5,
                                    7,  8,
                                    10,
                                    13,
                        };
                        FPSCounterNumbersToPositionIndexes[8] = new int[] {
                                0,  1,  2,
                                3,      5,
                                6,  7,  8,
                                9,      11,
                                12, 13, 14
                        };
                        FPSCounterNumbersToPositionIndexes[9] = new int[] {
                                0,  1,  2,
                                3,      5,
                                6,  7,  8,
                                        11,
                                12, 13, 14
                        };

                        Initialize();
                    }

                    boolean InitializationFailed = false;
                    void ShaderCheck(int Shader)
                    {
                        if (Shader == 0)
                            InitializationFailed = true;
                        else
                        {
                            AllShaders[AllShadersCount] = Shader;
                            AllShadersCount++;
                        }
                    }
                    void ProgramCheck(int Program)
                    {
                        if (Program == 0)
                            InitializationFailed = true;
                    }
                    void FinalCheck()
                    {
                        if (InitializationFailed)
                            DeleteObjects();
                    }

                    void Initialize()
                    {
                        DeleteObjects();
                        ResetReportId();

                        SettingsChanged = false;

                        SmoothOutRotationMatrix = Preferences.getBoolean("smooth_out_rotation_sensor", true);

                        // Initialize matrices with the phone screen facing up
                        Arrays.fill(DeviceRotationMatrix, 0);
                        DeviceRotationMatrix[0] = 1;
                        DeviceRotationMatrix[4] = 1;
                        DeviceRotationMatrix[8] = 1;
                        System.arraycopy(DeviceRotationMatrix, 0, DeviceRotationMatrixA, 0, 9);
                        System.arraycopy(DeviceRotationMatrix, 0, DeviceRotationMatrixB, 0, 9);

                        UseViewerForReflections = Preferences.getBoolean("enable_viewer_for_reflections", false);
                        EnableParallax = Preferences.getBoolean("enable_parallax", true);

                        String sensor_update_delay_str = Preferences.getString("rotation_sensor_update_delay", "ui");
                        int sensor_update_delay;
                        switch (sensor_update_delay_str)
                        {
                            case "normal": // 3
                                sensor_update_delay = SensorManager.SENSOR_DELAY_NORMAL;
                                break;
                            case "ui": // 2
                                sensor_update_delay = SensorManager.SENSOR_DELAY_UI;
                                break;
                            case "game": // 1
                                sensor_update_delay = SensorManager.SENSOR_DELAY_GAME;
                                break;
                            case "fastest": // 0
                                sensor_update_delay = SensorManager.SENSOR_DELAY_FASTEST;
                                break;
                            default:
                                sensor_update_delay = SensorManager.SENSOR_DELAY_UI;
                                break;
                        }
                        Sensor RotationSensor = SensorManagerInstance.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                        SensorManagerInstance.registerListener(
                                RotationSensorEventListener,
                                RotationSensor,
                                sensor_update_delay
                        );

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

                        LightColor[] colors = com.reminimalism.materialslivewallpaper.LightColors.Decode(
                                com.reminimalism.materialslivewallpaper
                                        .LightColors
                                        .GetColors(MaterialsWallpaperService.this)
                        );
                        int colors_index = 0;
                        LightColors = new float[count * 3];
                        for (int i = 0; i < count; i++)
                        {
                            colors_index += 1;
                            if (colors_index >= colors.length)
                                colors_index = 0;
                            try
                            {
                                LightColors[i * 3] = colors[colors_index].R;
                                LightColors[i * 3 + 1] = colors[colors_index].G;
                                LightColors[i * 3 + 2] = colors[colors_index].B;
                            }
                            catch (Exception ignored)
                            {
                                LightDirections[i * 3] = 0.6f;
                                LightDirections[i * 3 + 1] = 0.6f;
                                LightDirections[i * 3 + 2] = 0.6f;
                            }
                        }

                        LightReflectionDirections = new float[LightDirections.length]; // Will be updated based on LightDirections & device rotation

                        // Exposure

                        if (Preferences.getBoolean("enable_auto_exposure", false))
                        {
                            float max_light = 0;
                            for (int i = 0; i < count; i++)
                            {
                                float sum_r = LightColors[i * 3];
                                float sum_g = LightColors[i * 3 + 1];
                                float sum_b = LightColors[i * 3 + 2];
                                for (int j = 0; j < count; j++) if (i != j)
                                {
                                    float dot = LightDirections[i * 3] * LightDirections[j * 3]
                                            + LightDirections[i * 3 + 1] * LightDirections[j * 3 + 1]
                                            + LightDirections[i * 3 + 2] * LightDirections[j * 3 + 2];
                                    if (dot > 0)
                                    {
                                        sum_r += dot * LightColors[j * 3];
                                        sum_g += dot * LightColors[j * 3 + 1];
                                        sum_b += dot * LightColors[j * 3 + 2];
                                    }
                                }
                                max_light = Math.max(max_light, Math.max(sum_r, Math.max(sum_g, sum_b)));
                            }
                            if (max_light > 1)
                                Exposure = 1 / max_light;
                            else
                                Exposure = 1;
                            Exposure += Exposure * ((float)Preferences.getInt("additional_exposure", 0) / 100f);
                        }
                        else
                        {
                            Exposure = ((float)Preferences.getInt("exposure", 100) / 100f);
                        }

                        // GL setup

                        GLES20.glClearColor(0, 0, 0, 1);

                        // FPSCounter program setup

                        if (Preferences.getBoolean("enable_fps_counter", false))
                        {
                            FPSCounter = new SolidTriangleProgram();

                            int VertexShader = CompileShader(
                                    ReadRawTextResource(R.raw.solid_triangle_vertex_shader),
                                    false,
                                    "FPS Counter"
                            );
                            ShaderCheck(VertexShader);

                            int FragmentShader = CompileShader(
                                    ReadRawTextResource(R.raw.solid_triangle_fragment_shader),
                                    true,
                                    "FPS Counter"
                            );
                            ShaderCheck(FragmentShader);

                            FPSCounter.Program = GLES20.glCreateProgram();
                            if (FPSCounter.Program != 0)
                            {
                                GLES20.glAttachShader(FPSCounter.Program, VertexShader);
                                GLES20.glAttachShader(FPSCounter.Program, FragmentShader);

                                GLES20.glBindAttribLocation(FPSCounter.Program, 0, "Position");

                                GLES20.glLinkProgram(FPSCounter.Program);

                                final int[] LinkStatus = new int[1];
                                GLES20.glGetProgramiv(FPSCounter.Program, GLES20.GL_LINK_STATUS, LinkStatus, 0);

                                if (LinkStatus[0] == 0)
                                {
                                    GLES20.glDeleteProgram(FPSCounter.Program);
                                    FPSCounter.Program = 0;
                                    ReportError("FPS counter program link failed.");
                                }
                                else
                                {
                                    FPSCounter.PositionAttribute = GLES20.glGetAttribLocation(FPSCounter.Program, "Position");
                                    FPSCounter.ColorUniform = GLES20.glGetUniformLocation(FPSCounter.Program, "Color");
                                }
                            }
                            else ReportError("FPS counter program creation failed.");

                            ProgramCheck(FPSCounter.Program);
                        }

                        // Layers

                        LayerFilenames[] layers_f;
                        if (UseCustomMaterial)
                            layers_f = SettingsActivity.GetCustomMaterialAdditionalLayers(MaterialsWallpaperService.this);
                        else
                            layers_f = new LayerFilenames[0];
                        Layers = new Layer[1 + layers_f.length];
                        Arrays.fill(Layers, null);
                        AllTexturesCount = 0;
                        for (int i = -1; i < layers_f.length; i++)
                        {
                            boolean load_from_resources = false;
                            LayerFilenames layer_f;
                            Layer current_layer = new Layer();
                            current_layer.Program = 0;

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
                                    layer_f.Depth = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Depth
                                    );
                                    layer_f.Height = SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Height
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
                                    current_layer.EnableDepth          = layer_f.Depth          != null;
                                    current_layer.EnableHeight         = layer_f.Height         != null;
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
                                    current_layer.EnableDepth = false;
                                    current_layer.EnableHeight = false;
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
                                current_layer.EnableDepth          = layer_f.Depth          != null;
                                current_layer.EnableHeight         = layer_f.Height         != null;
                            }

                            current_layer.EnableBrush = current_layer.EnableBrush && !current_layer.EnableCircularBrush;
                            current_layer.EnableDepth = current_layer.EnableDepth && EnableParallax;
                            current_layer.EnableHeight = current_layer.EnableHeight && EnableParallax;
                            current_layer.EnableParallax = current_layer.EnableDepth || current_layer.EnableHeight;

                            int current_layer_textures_count = 0;
                            if (current_layer.EnableBase) current_layer_textures_count++;
                            if (current_layer.EnableReflections) current_layer_textures_count++;
                            if (current_layer.EnableNormal) current_layer_textures_count++;
                            if (current_layer.EnableShininess) current_layer_textures_count++;
                            if (current_layer.EnableBrush) current_layer_textures_count++;
                            if (current_layer.EnableBrushIntensity) current_layer_textures_count++;
                            if (current_layer.EnableDepth) current_layer_textures_count++;
                            if (current_layer.EnableHeight) current_layer_textures_count++;

                            // Vertex shader setup

                            int VertexShader = CompileShader(
                                    "#define ENABLE_DEPTH " + (current_layer.EnableDepth ? "1\n" : "0\n")
                                            + "#define ENABLE_HEIGHT "                 + (current_layer.EnableHeight ? "1\n" : "0\n")
                                            + "#define ENABLE_VIEWER_FOR_REFLECTIONS " + (UseViewerForReflections ? "1\n" : "0\n")
                                            + ReadRawTextResource(R.raw.wallpaper_vertex_shader),
                                    false,
                                    "Wallpaper"
                            );
                            ShaderCheck(VertexShader);

                            // Fragment shader setup

                            int FragmentShader = CompileShader(
                                    "#define LIGHTS_COUNT " + (LightDirections.length / 3) + "\n"
                                            + (current_layer.EnableCircularBrush ? "#define ENABLE_CIRCULAR_BRUSH 1\n" : "")
                                            + "#define ENABLE_NORMAL_NORMALIZATION "   + (Config.NormalizeNormal ? "1\n" : "0\n")
                                            + "#define ENABLE_VIEWER_FOR_REFLECTIONS " + (UseViewerForReflections ? "1\n" : "0\n")
                                            + "#define ENABLE_BASE "            + (current_layer.EnableBase ? "1\n" : "0\n")
                                            + "#define ENABLE_REFLECTIONS "     + (current_layer.EnableReflections ? "1\n" : "0\n")
                                            + "#define ENABLE_NORMAL "          + (current_layer.EnableNormal ? "1\n" : "0\n")
                                            + "#define ENABLE_SHININESS "       + (current_layer.EnableShininess ? "1\n" : "0\n")
                                            + "#define ENABLE_BRUSH "           + (current_layer.EnableBrush ? "1\n" : "0\n")
                                            + "#define ENABLE_BRUSH_INTENSITY " + (current_layer.EnableBrushIntensity ? "1\n" : "0\n")
                                            + "#define ENABLE_DEPTH "           + (current_layer.EnableDepth ? "1\n" : "0\n")
                                            + "#define ENABLE_HEIGHT "          + (current_layer.EnableHeight ? "1\n" : "0\n")
                                            + ReadRawTextResource(R.raw.wallpaper_fragment_shader),
                                    true,
                                    "Wallpaper"
                            );
                            ShaderCheck(FragmentShader);

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
                                    current_layer.Program = 0;
                                    ReportError("Program link failed.");
                                }
                            }
                            else ReportError("Program creation failed.");

                            ProgramCheck(current_layer.Program);

                            if (current_layer.Program != 0)
                            {
                                current_layer.PositionAttribute = GLES20.glGetAttribLocation(current_layer.Program, "Position");

                                current_layer.ScreenFrontDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenFrontDirection");
                                current_layer.ScreenUpDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenUpDirection");
                                current_layer.ScreenRightDirectionUniform = GLES20.glGetUniformLocation(current_layer.Program, "ScreenRightDirection");
                                current_layer.FOVUniform = GLES20.glGetUniformLocation(current_layer.Program, "FOV");
                                current_layer.UVScaleUniform = GLES20.glGetUniformLocation(current_layer.Program, "UVScale");

                                if (UseViewerForReflections || current_layer.EnableParallax)
                                    current_layer.ViewerUniform = GLES20.glGetUniformLocation(current_layer.Program, "Viewer");

                                current_layer.LightDirectionsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightDirections");
                                if (!UseViewerForReflections)
                                    current_layer.LightReflectionDirectionsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightReflectionDirections");
                                current_layer.LightColorsUniform = GLES20.glGetUniformLocation(current_layer.Program, "LightColors");

                                current_layer.SamplerUniforms = new int[current_layer_textures_count];
                                int tex_arr_index = 0;
                                if (current_layer.EnableBase)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.BaseUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "BaseColor");
                                if (current_layer.EnableReflections)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.ReflectionsUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "ReflectionsColor");
                                if (current_layer.EnableNormal)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.NormalUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "Normal");
                                if (current_layer.EnableShininess)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.ShininessUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "Shininess");
                                if (current_layer.EnableBrush)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.BrushUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "Brush");
                                if (current_layer.EnableBrushIntensity)
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.BrushIntensityUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "BrushIntensity");
                                if (current_layer.EnableDepth)
                                {
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.DepthUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "Depth");
                                    current_layer.DepthIntensityUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "DepthIntensity");
                                    current_layer.DepthIntensity = (float)Config.DepthIntensity;
                                }
                                if (current_layer.EnableHeight)
                                {
                                    current_layer.SamplerUniforms[tex_arr_index++]
                                            = current_layer.HeightUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "Height");
                                    current_layer.HeightIntensityUniform
                                            = GLES20.glGetUniformLocation(current_layer.Program, "HeightIntensity");
                                    current_layer.HeightIntensity = (float)Config.HeightIntensity;
                                }

                                current_layer.ExposureUniform = GLES20.glGetUniformLocation(current_layer.Program, "Exposure");
                            }

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
                                if (current_layer.EnableDepth)
                                    current_layer.DepthTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Depth), false);
                                if (current_layer.EnableHeight)
                                    current_layer.HeightTexture = LoadTextureFromResource(Integer.parseInt(layer_f.Height), false);
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
                                if (current_layer.EnableDepth)
                                    current_layer.DepthTexture = LoadTextureFromFile(
                                            layer_f.Depth,
                                            R.drawable.black_16x16,
                                            false
                                    );
                                if (current_layer.EnableHeight)
                                    current_layer.HeightTexture = LoadTextureFromFile(
                                            layer_f.Height,
                                            R.drawable.black_16x16,
                                            false
                                    );
                            }

                            current_layer.Textures = new int[current_layer_textures_count];
                            int tex_arr_index = 0;

                            if (current_layer.EnableBase)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.BaseTexture;
                                AllTextures[AllTexturesCount] = current_layer.BaseTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableReflections)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.ReflectionsTexture;
                                AllTextures[AllTexturesCount] = current_layer.ReflectionsTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableNormal)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.NormalTexture;
                                AllTextures[AllTexturesCount] = current_layer.NormalTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableShininess)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.ShininessTexture;
                                AllTextures[AllTexturesCount] = current_layer.ShininessTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableBrush)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.BrushTexture;
                                AllTextures[AllTexturesCount] = current_layer.BrushTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableBrushIntensity)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.BrushIntensityTexture;
                                AllTextures[AllTexturesCount] = current_layer.BrushIntensityTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableDepth)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.DepthTexture;
                                AllTextures[AllTexturesCount] = current_layer.DepthTexture;
                                AllTexturesCount++;
                            }

                            if (current_layer.EnableHeight)
                            {
                                current_layer.Textures[tex_arr_index++] = current_layer.HeightTexture;
                                AllTextures[AllTexturesCount] = current_layer.HeightTexture;
                                AllTexturesCount++;
                            }

                            Layers[i + 1] = current_layer; // i starts from -1
                        }

                        if (EnableParallax)
                        {
                            EnableParallax = false;
                            for (Layer layer : Layers) if (layer.EnableParallax)
                            {
                                EnableParallax = true;
                                break;
                            }
                        }

                        GLES20.glEnable(GLES20.GL_BLEND);
                        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);

                        GetTimeDiff_ms(); // Update Time

                        FinalCheck();
                    }

                    void DeleteObjects()
                    {
                        if (Layers != null)
                            for (Layer layer : Layers)
                                if (layer != null && layer.Program != 0)
                                    GLES20.glDeleteProgram(layer.Program);
                        Layers = null;

                        for (int i = 0; i < AllShadersCount; i++)
                            GLES20.glDeleteShader(AllShaders[i]);
                        AllShadersCount = 0;

                        GLES20.glDeleteTextures(AllTexturesCount, AllTextures, 0);
                        AllTexturesCount = 0;

                        if (FPSCounter != null)
                        {
                            GLES20.glDeleteProgram(FPSCounter.Program);
                            FPSCounter = null;
                        }
                    }

                    void Reinitialize()
                    {
                        if (SensorManagerInstance != null && RotationSensorEventListener != null)
                            SensorManagerInstance.unregisterListener(RotationSensorEventListener);
                        //DeleteObjects(); // Initialize() does this.
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

                        int TimeDiff_ms = 0;
                        if (LimitFPS)
                        {
                            TimeDiff_ms = GetTimeDiff_ms();
                            int remaining = FrameMinDuration_ms - TimeDiff_ms;
                            if (remaining > 0) try
                            {
                                Thread.sleep(remaining);
                            }
                            catch (InterruptedException e)
                            {
                                Thread.currentThread().interrupt();
                            }
                            TimeDiff_ms += GetTimeDiff_ms(); // Update time too
                        }
                        else if (FPSCounter != null)
                            TimeDiff_ms = GetTimeDiff_ms();

                        // Calculations

                        if (SmoothOutRotationMatrix)
                        {
                            long current_time_ms = System.currentTimeMillis();
                            if (current_time_ms > DeviceRotationMatrixCTime_ms)
                            {
                                System.arraycopy(DeviceRotationMatrixB, 0, DeviceRotationMatrix, 0, 9);
                            }
                            else
                            {
                                float t = (float)(current_time_ms - DeviceRotationMatrixBTime_ms) / (float)DeviceRotationMatrixABTimeDiff_ms;
                                for (int i = 0; i < 9; i++)
                                    DeviceRotationMatrix[i]
                                            = DeviceRotationMatrixA[i]
                                            + (DeviceRotationMatrixB[i] - DeviceRotationMatrixA[i])
                                                * t;
                                // No normalization should be fine
                            }
                            if (RotationVectorChanged)
                            {
                                System.arraycopy(DeviceRotationMatrix, 0, DeviceRotationMatrixA, 0, 9);
                                SensorManager.getRotationMatrixFromVector(DeviceRotationMatrixB, RotationVector);
                                DeviceRotationMatrixATime_ms = DeviceRotationMatrixBTime_ms;
                                DeviceRotationMatrixBTime_ms = System.currentTimeMillis();
                                DeviceRotationMatrixABTimeDiff_ms = DeviceRotationMatrixBTime_ms - DeviceRotationMatrixATime_ms;
                                if (DeviceRotationMatrixABTimeDiff_ms <= 0) DeviceRotationMatrixABTimeDiff_ms = 1;
                                if (DeviceRotationMatrixATime_ms == 0)
                                {
                                    System.arraycopy(DeviceRotationMatrixB, 0, DeviceRotationMatrixA, 0, 9);
                                    System.arraycopy(DeviceRotationMatrixB, 0, DeviceRotationMatrix, 0, 9);
                                    DeviceRotationMatrixABTimeDiff_ms = 1;
                                }
                                DeviceRotationMatrixCTime_ms = DeviceRotationMatrixBTime_ms + DeviceRotationMatrixABTimeDiff_ms;
                                RotationVectorChanged = false;
                            }
                        }
                        else if (RotationVectorChanged)
                        {
                            SensorManager.getRotationMatrixFromVector(DeviceRotationMatrix, RotationVector);
                            RotationVectorChanged = false;
                        }

                        int rotation = ((WindowManager)getSystemService(Context.WINDOW_SERVICE))
                                .getDefaultDisplay()
                                .getRotation();
                        boolean SwapUpAndRight = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
                        int UpIndexOffset = SwapUpAndRight ? 0 : 1;
                        int RightIndexOffset = SwapUpAndRight ? 1 : 0;
                        float UpFinalCoefficient = rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_180 ? -1 : 1;
                        float RightFinalCoefficient = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180 ? -1 : 1;

                        ScreenFrontDirection[0] = DeviceRotationMatrix[2];
                        ScreenFrontDirection[1] = DeviceRotationMatrix[5];
                        ScreenFrontDirection[2] = DeviceRotationMatrix[8];
                        ScreenUpDirection[0] = DeviceRotationMatrix[UpIndexOffset] * UpFinalCoefficient;
                        ScreenUpDirection[1] = DeviceRotationMatrix[3 + UpIndexOffset] * UpFinalCoefficient;
                        ScreenUpDirection[2] = DeviceRotationMatrix[6 + UpIndexOffset] * UpFinalCoefficient;
                        ScreenRightDirection[0] = DeviceRotationMatrix[RightIndexOffset] * RightFinalCoefficient;
                        ScreenRightDirection[1] = DeviceRotationMatrix[3 + RightIndexOffset] * RightFinalCoefficient;
                        ScreenRightDirection[2] = DeviceRotationMatrix[6 + RightIndexOffset] * RightFinalCoefficient;

                        float ViewerRelativeX = 0;
                        float ViewerRelativeY = 0;
                        float ViewerRelativeZ = 1;
                        if (UseViewerForReflections || EnableParallax)
                        {
                            ViewerRelativeX = ViewerPosition[0] * ScreenRightDirection[0]
                                            + ViewerPosition[1] * ScreenRightDirection[1]
                                            + ViewerPosition[2] * ScreenRightDirection[2];
                            ViewerRelativeY = ViewerPosition[0] * ScreenUpDirection[0]
                                            + ViewerPosition[1] * ScreenUpDirection[1]
                                            + ViewerPosition[2] * ScreenUpDirection[2];
                            ViewerRelativeZ = ViewerPosition[0] * ScreenFrontDirection[0]
                                            + ViewerPosition[1] * ScreenFrontDirection[1]
                                            + ViewerPosition[2] * ScreenFrontDirection[2];
                            if (ViewerRelativeZ < 0.707106781F) // Needs correction
                            {
                                // Correct
                                // ViewerPosition correction: Z: 0.707106781, magnitude(X,Y): 0.707106781 (= 1 / sqrt(2))
                                ViewerRelativeZ = 0.707106781F;
                                if (ViewerRelativeX == 0 && ViewerRelativeY == 0)
                                {
                                    ViewerRelativeX = 1;
                                    ViewerRelativeY = 1;
                                }
                                float xy_mag = (float)Math.sqrt(ViewerRelativeX * ViewerRelativeX + ViewerRelativeY * ViewerRelativeY);
                                float xy_correction = 0.707106781F / xy_mag;
                                ViewerRelativeX *= xy_correction;
                                ViewerRelativeY *= xy_correction;

                                // Update the non-relative vector
                                ViewerPosition[0] = ViewerRelativeX * ScreenRightDirection[0]
                                                  + ViewerRelativeY * ScreenUpDirection[0]
                                                  + ViewerRelativeZ * ScreenUpDirection[0];
                                ViewerPosition[0] = ViewerRelativeX * ScreenRightDirection[1]
                                                  + ViewerRelativeY * ScreenUpDirection[1]
                                                  + ViewerRelativeZ * ScreenUpDirection[1];
                                ViewerPosition[0] = ViewerRelativeX * ScreenRightDirection[2]
                                                  + ViewerRelativeY * ScreenUpDirection[2]
                                                  + ViewerRelativeZ * ScreenUpDirection[2];
                            }
                        }

                        float UVScaleX, UVSCaleY;
                        if (AspectRatio < 1) { UVScaleX = AspectRatio; UVSCaleY = 1; }
                        else { UVSCaleY = 1 / AspectRatio; UVScaleX = 1; }

                        if (!UseViewerForReflections)
                        {
                            // Make lights 2x closer to ScreenFrontDirection
                            for (int i = 0; i < LightDirections.length; i++)
                            {
                                LightReflectionDirections[i]
                                        = (
                                        ScreenFrontDirection[i % 3]
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
                        }

                        // GL

                        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

                        if (Layers == null)
                            return;
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
                                    ScreenFrontDirection[0],
                                    ScreenFrontDirection[1],
                                    ScreenFrontDirection[2]
                            );
                            GLES20.glUniform3f(
                                    layer.ScreenUpDirectionUniform,
                                    ScreenUpDirection[0],
                                    ScreenUpDirection[1],
                                    ScreenUpDirection[2]
                            );
                            GLES20.glUniform3f(
                                    layer.ScreenRightDirectionUniform,
                                    ScreenRightDirection[0],
                                    ScreenRightDirection[1],
                                    ScreenRightDirection[2]
                            );

                            if (UseViewerForReflections || layer.EnableParallax)
                                GLES20.glUniform3f(
                                        layer.ViewerUniform,
                                        ViewerRelativeX,
                                        ViewerRelativeY,
                                        ViewerRelativeZ
                                );

                            GLES20.glUniform2f(layer.FOVUniform, 0.1f * AspectRatio, 0.1f);

                            GLES20.glUniform2f(layer.UVScaleUniform, UVScaleX, UVSCaleY);

                            GLES20.glUniform3fv(
                                    layer.LightDirectionsUniform,
                                    LightDirections.length / 3,
                                    LightDirections,
                                    0
                            );
                            if (!UseViewerForReflections)
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
                            for (int i = 0; i < layer.SamplerUniforms.length; i++)
                            {
                                GLES20.glActiveTexture(GetTextureIDValue(i));
                                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, layer.Textures[i]);
                                GLES20.glUniform1i(layer.SamplerUniforms[i], i);
                            }

                            if (layer.EnableDepth)
                                GLES20.glUniform1f(layer.DepthIntensityUniform, layer.DepthIntensity);
                            if (layer.EnableHeight)
                                GLES20.glUniform1f(layer.HeightIntensityUniform, layer.HeightIntensity);

                            GLES20.glUniform1f(layer.ExposureUniform, Exposure);

                            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                            //GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 6);
                        }

                        if (FPSCounter != null)
                        {
                            float TimeDiff = (float)TimeDiff_ms / 1000;
                            if (TimeDiff != 0)
                                FPS = FPS + 0.1f * (1.0f / TimeDiff - FPS);

                            int fps = (int)FPS;
                            int[] digits = new int[FPSCounterTriangleStripPositionValues.length];
                            Arrays.fill(digits, 0);
                            int count = 0;
                            while (fps > 0)
                            {
                                if (count < digits.length)
                                    digits[digits.length - 1 - count] = fps % 10;
                                fps /= 10;
                                count++;
                            }
                            if (count > digits.length)
                                Arrays.fill(digits, 9);

                            for (int i = 0; i < digits.length; i++)
                            {
                                for (int j : FPSCounterNumbersToPositionIndexes[digits[i]])
                                {
                                    GLES20.glUseProgram(FPSCounter.Program);
                                    GLES20.glVertexAttribPointer(
                                            FPSCounter.PositionAttribute,
                                            3,
                                            GLES20.GL_FLOAT,
                                            false,
                                            0,
                                            FPSCounterTriangleStripPositionValues[i][j]
                                    );
                                    GLES20.glEnableVertexAttribArray(FPSCounter.PositionAttribute);
                                    GLES20.glUniform3fv(
                                            FPSCounter.ColorUniform,
                                            1,
                                            FPSCounterColor,
                                            0
                                    );
                                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                                }
                            }
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

                    int CompileShader(String Source, boolean IsFragmentShaderAndNotVertexShader, String Label)
                    {
                        int Shader = GLES20.glCreateShader(
                                IsFragmentShaderAndNotVertexShader ? GLES20.GL_FRAGMENT_SHADER : GLES20.GL_VERTEX_SHADER
                        );
                        if (Shader != 0)
                        {
                            GLES20.glShaderSource(Shader, Source);
                            GLES20.glCompileShader(Shader);

                            final int[] CompileStatus = new int[1];
                            GLES20.glGetShaderiv(Shader, GLES20.GL_COMPILE_STATUS, CompileStatus, 0);

                            if (CompileStatus[0] == 0)
                            {
                                String log = GLES20.glGetShaderInfoLog(Shader);
                                GLES20.glDeleteShader(Shader);
                                Shader = 0;
                                ReportError(Label
                                        + (IsFragmentShaderAndNotVertexShader ? " fragment" : " vertex")
                                        + " shader compilation failed. " + log);
                            }
                        }
                        else ReportError(Label
                                + (IsFragmentShaderAndNotVertexShader ? " fragment" : " vertex")
                                + " shader creation failed.");
                        return Shader;
                    }

                    private int report_notif_id = 0;
                    void ResetReportId()
                    {
                        report_notif_id = 0;
                    }
                    void ReportError(String Message)
                    {
                        StringWriter swriter = new StringWriter();
                        PrintWriter pwriter = new PrintWriter(swriter);
                        new Throwable().printStackTrace(pwriter);
                        String StackTrace = swriter.toString();
                        final String Text = Message + "\n\nStack Trace:\n" + StackTrace;

                        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                        Notification.Builder notification_builder;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        {
                            NotificationChannel channel = new NotificationChannel("errors", "Errors", NotificationManager.IMPORTANCE_HIGH);
                            channel.setDescription("Errors are reported here.");
                            nm.createNotificationChannel(channel);
                            notification_builder = new Notification.Builder(MaterialsWallpaperService.this, "errors");
                        }
                        else
                            notification_builder = new Notification.Builder(MaterialsWallpaperService.this);
                        Notification notification = notification_builder
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle(report_notif_id == 0 ? "First error (copied to clipboard)" : "Error")
                                .setContentText(Text)
                                .setStyle(new Notification.BigTextStyle())
                                .build();
                        nm.notify(report_notif_id++, notification);

                        if (report_notif_id == 0)
                            ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE))
                                    .setPrimaryClip(ClipData.newPlainText("Error text", Text));
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
