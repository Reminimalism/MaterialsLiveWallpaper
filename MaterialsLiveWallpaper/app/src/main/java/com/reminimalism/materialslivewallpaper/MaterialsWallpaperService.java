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
                    int PositionAttribute;
                    FloatBuffer TriangleStripPositionValues;
                    //FloatBuffer TriangleFanPositionValues;

                    float[] DeviceRotationMatrix = new float[9];
                    float AspectRatio = 1;

                    boolean LimitFPS = false;
                    int FrameMinDuration_ms;
                    long Time;

                    int Program;

                    int ScreenFrontDirectionUniform;
                    int ScreenUpDirectionUniform;
                    int ScreenRightDirectionUniform;
                    int FOVUniform;
                    int UVScaleUniform;

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

                    int LightDirectionsUniform;
                    int LightReflectionDirectionsUniform;
                    int LightColorsUniform;

                    int BaseColorUniform;
                    int ReflectionsColorUniform;
                    int NormalUniform;
                    int ShininessUniform;
                    int BrushUniform;
                    int BrushIntensityUniform;

                    int BaseColorTexture;
                    int ReflectionsColorTexture;
                    int NormalTexture;
                    int ShininessTexture;
                    int BrushTexture;
                    int BrushIntensityTexture;

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
                        String  MaterialSample = Preferences.getString("material_sample", "flat_poly");
                        boolean EnableCircularBrush = !UseCustomMaterial && MaterialSample.equals("circular_brushed_metal");

                        Config Config = null;
                        boolean EnableBrushIntensity = false;
                        if (UseCustomMaterial)
                        {
                            Config = new Config(ReadTextFile( // Filename:
                                    SettingsActivity.GetCustomMaterialAssetFilename(
                                            MaterialsWallpaperService.this,
                                            SettingsActivity.CustomMaterialAssetType.Config
                                    )
                            ));
                            if (SettingsActivity.GetCustomMaterialAssetFilename(
                                    MaterialsWallpaperService.this,
                                    SettingsActivity.CustomMaterialAssetType.BrushIntensity) != null)
                                EnableBrushIntensity = true;
                        }
                        else
                            Config = new Config();

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

                        // Shaders setup

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

                        int FragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
                        if (FragmentShader != 0)
                        {
                            GLES20.glShaderSource(
                                    FragmentShader,
                                    "#define LIGHTS_COUNT " + (LightDirections.length / 3) + "\n"
                                            + (EnableCircularBrush ? "#define ENABLE_CIRCULAR_BRUSH 1\n" : "")
                                            + "#define ENABLE_NORMAL_NORMALIZATION " + (Config.NormalizeNormal ? "1\n" : "0\n")
                                            + "#define ENABLE_BRUSH_INTENSITY " + (EnableBrushIntensity ? "1\n" : "0\n")
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

                        Program = GLES20.glCreateProgram();
                        if (Program != 0)
                        {
                            GLES20.glAttachShader(Program, VertexShader);
                            GLES20.glAttachShader(Program, FragmentShader);

                            GLES20.glBindAttribLocation(Program, 0, "Position");

                            GLES20.glLinkProgram(Program);

                            final int[] LinkStatus = new int[1];
                            GLES20.glGetProgramiv(Program, GLES20.GL_LINK_STATUS, LinkStatus, 0);

                            if (LinkStatus[0] == 0)
                            {
                                GLES20.glDeleteProgram(Program);
                                throw new RuntimeException("Program link failed.");
                            }
                        }
                        else throw new RuntimeException("Program creation failed.");

                        PositionAttribute = GLES20.glGetAttribLocation(Program, "Position");

                        ScreenFrontDirectionUniform = GLES20.glGetUniformLocation(Program, "ScreenFrontDirection");
                        ScreenUpDirectionUniform = GLES20.glGetUniformLocation(Program, "ScreenUpDirection");
                        ScreenRightDirectionUniform = GLES20.glGetUniformLocation(Program, "ScreenRightDirection");
                        FOVUniform = GLES20.glGetUniformLocation(Program, "FOV");
                        UVScaleUniform = GLES20.glGetUniformLocation(Program, "UVScale");

                        LightDirectionsUniform = GLES20.glGetUniformLocation(Program, "LightDirections");
                        LightReflectionDirectionsUniform = GLES20.glGetUniformLocation(Program, "LightReflectionDirections");
                        LightColorsUniform = GLES20.glGetUniformLocation(Program, "LightColors");

                        BaseColorUniform = GLES20.glGetUniformLocation(Program, "BaseColor");
                        ReflectionsColorUniform = GLES20.glGetUniformLocation(Program, "ReflectionsColor");
                        NormalUniform = GLES20.glGetUniformLocation(Program, "Normal");
                        ShininessUniform = GLES20.glGetUniformLocation(Program, "Shininess");
                        if (!EnableCircularBrush)
                            BrushUniform = GLES20.glGetUniformLocation(Program, "Brush");

                        // Textures

                        if (UseCustomMaterial)
                        {
                            boolean BasePixelated = Config.PixelatedBase;
                            boolean ReflectionsPixelated = Config.PixelatedReflections;
                            boolean NormalPixelated = Config.PixelatedNormal;
                            boolean ShininessPixelated = Config.PixelatedShininess;
                            boolean BrushPixelated = Config.PixelatedBrush;
                            boolean BrushIntensityPixelated = Config.PixelatedBrushIntensity;

                            // TODO: set these by reading the config file

                            BaseColorTexture = LoadTextureFromFile(
                                    SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.Base),
                                    R.drawable.gray_80_128_16x16,
                                    BasePixelated
                            );
                            ReflectionsColorTexture = LoadTextureFromFile(
                                    SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.Reflections),
                                    R.drawable.gray_80_128_16x16,
                                    ReflectionsPixelated
                            );
                            NormalTexture = LoadTextureFromFile(
                                    SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.Normal),
                                    R.drawable.flat_normal_16x16,
                                    NormalPixelated
                            );
                            ShininessTexture = LoadTextureFromFile(
                                    SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.Shininess),
                                    R.drawable.black_16x16,
                                    ShininessPixelated
                            );
                            BrushTexture = LoadTextureFromFile(
                                    SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.Brush),
                                    R.drawable.gray_80_128_16x16,
                                    BrushPixelated
                            );
                            if (EnableBrushIntensity)
                                BrushIntensityTexture = LoadTextureFromFile(
                                        SettingsActivity.GetCustomMaterialAssetFilename(MaterialsWallpaperService.this, SettingsActivity.CustomMaterialAssetType.BrushIntensity),
                                        R.drawable.white_16x16,
                                        BrushIntensityPixelated
                                );
                            else
                                BrushIntensityTexture = 0;
                        }
                        else
                        {
                            int BaseR        = R.drawable.gray_80_128_16x16;
                            int ReflectionsR = R.drawable.gray_80_128_16x16;
                            int NormalR      = R.drawable.flat_normal_16x16;
                            int ShininessR   = R.drawable.black_16x16;
                            int BrushR       = R.drawable.gray_80_128_16x16;
                            switch (MaterialSample)
                            {
                                case "circular_brushed_metal":
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

                            BaseColorTexture = LoadTextureFromResource(BaseR, false);
                            ReflectionsColorTexture = LoadTextureFromResource(ReflectionsR, false);
                            NormalTexture = LoadTextureFromResource(NormalR, false);
                            ShininessTexture = LoadTextureFromResource(ShininessR, false);
                            if (EnableCircularBrush)
                                BrushTexture = 0;
                            else
                                BrushTexture = LoadTextureFromResource(BrushR, false);
                            BrushIntensityTexture = 0;
                        }

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, BaseColorTexture);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ReflectionsColorTexture);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, NormalTexture);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ShininessTexture);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, BrushTexture);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, BrushIntensityTexture);

                        GLES20.glUseProgram(Program);

                        GetTimeDiff_ms(); // Update Time
                    }

                    void Reinitialize()
                    {
                        GLES20.glDeleteProgram(Program);
                        int[] Textures = {
                                BaseColorTexture,
                                ReflectionsColorTexture,
                                NormalTexture,
                                ShininessTexture,
                                BrushTexture,
                                BrushIntensityTexture
                        };
                        GLES20.glDeleteTextures(
                                4,
                                Textures,
                                0
                        );
                        if (BrushTexture != 0)
                            GLES20.glDeleteTextures(
                                    1,
                                    Textures,
                                    4
                            );
                        if (BrushIntensityTexture != 0)
                            GLES20.glDeleteTextures(
                                    1,
                                    Textures,
                                    5
                            );
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

                        // No need to clear
                        //GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
                        GLES20.glVertexAttribPointer(
                                PositionAttribute,
                                3,
                                GLES20.GL_FLOAT,
                                false,
                                0,
                                TriangleStripPositionValues
                                //TriangleFanPositionValues
                        );
                        GLES20.glEnableVertexAttribArray(PositionAttribute);

                        SensorManager.getRotationMatrixFromVector(DeviceRotationMatrix, RotationVector);
                        int rotation = ((WindowManager)getSystemService(Context.WINDOW_SERVICE))
                                .getDefaultDisplay()
                                .getRotation();

                        boolean SwapUpAndRight = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
                        int UpIndexOffset = SwapUpAndRight ? 0 : 1;
                        int RightIndexOffset = SwapUpAndRight ? 1 : 0;
                        float UpFinalCoefficient = rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_180 ? -1 : 1;
                        float RightFinalCoefficient = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180 ? -1 : 1;

                        GLES20.glUniform3f(
                                ScreenFrontDirectionUniform,
                                DeviceRotationMatrix[2],
                                DeviceRotationMatrix[5],
                                DeviceRotationMatrix[8]
                        );
                        GLES20.glUniform3f(
                                ScreenUpDirectionUniform,
                                DeviceRotationMatrix[UpIndexOffset] * UpFinalCoefficient,
                                DeviceRotationMatrix[3 + UpIndexOffset] * UpFinalCoefficient,
                                DeviceRotationMatrix[6 + UpIndexOffset] * UpFinalCoefficient
                        );
                        GLES20.glUniform3f(
                                ScreenRightDirectionUniform,
                                DeviceRotationMatrix[RightIndexOffset] * RightFinalCoefficient,
                                DeviceRotationMatrix[3 + RightIndexOffset] * RightFinalCoefficient,
                                DeviceRotationMatrix[6 + RightIndexOffset] * RightFinalCoefficient
                        );

                        GLES20.glUniform2f(FOVUniform, 0.1f * AspectRatio, 0.1f);

                        float x, y;
                        if (AspectRatio < 1) { x = AspectRatio; y = 1; }
                        else { y = 1 / AspectRatio; x = 1; }
                        GLES20.glUniform2f(UVScaleUniform, x, y);

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
                        GLES20.glUniform3fv(
                                LightDirectionsUniform,
                                LightDirections.length / 3,
                                LightDirections,
                                0
                        );
                        GLES20.glUniform3fv(
                                LightReflectionDirectionsUniform,
                                LightReflectionDirections.length / 3,
                                LightReflectionDirections,
                                0
                        );
                        GLES20.glUniform3fv(
                                LightColorsUniform,
                                LightColors.length / 3,
                                LightColors,
                                0
                        );

                        // Textures
                        GLES20.glUniform1i(BaseColorUniform, 0);
                        GLES20.glUniform1i(ReflectionsColorUniform, 1);
                        GLES20.glUniform1i(NormalUniform, 2);
                        GLES20.glUniform1i(ShininessUniform, 3);
                        GLES20.glUniform1i(BrushUniform, 4);
                        GLES20.glUniform1i(BrushIntensityUniform, 5);

                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                        //GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 6);
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
