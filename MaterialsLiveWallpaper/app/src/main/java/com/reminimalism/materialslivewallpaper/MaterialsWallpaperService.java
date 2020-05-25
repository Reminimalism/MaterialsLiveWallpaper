package com.reminimalism.materialslivewallpaper;

import android.app.ActivityManager;
import android.content.Context;
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
import android.view.SurfaceHolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

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

        SensorManager SensorManagerInstance = null;
        SensorEventListener RotationSensorEventListener = null;
        float[] RotationVector = new float[4];

        WallpaperGLSurfaceView GLSurface = null;

        @Override
        public void onCreate(SurfaceHolder surface_holder)
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
                    RotationSensorEventListener = new SensorEventListener() {
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

                    int ScreenFrontDirectionUniform;
                    int ScreenUpDirectionUniform;
                    int ScreenRightDirectionUniform;
                    int FOVUniform;
                    int UVScaleUniform;

                    float[] LightDirections = {
                            0, 0, 1,
                            1, 0, 0,
                            0, 1, 0,
                            -1, 0, 0,
                            0, -1, 0,
                            0, 0, -1
                    };
                    float[] LightReflectionDirections = {
                            0, 0, 1,
                            1, 0, 0,
                            0, 1, 0,
                            -1, 0, 0,
                            0, -1, 0,
                            0, 0, -1
                    };
                    float[] LightColors = {
                            1, 1, 1,
                            1, 0, 0,
                            0, 1, 0,
                            0, 0, 1,
                            0, 1, 1,
                            1, 0, 1
                    };

                    int LightDirectionsUniform;
                    int LightReflectionDirectionsUniform;
                    int LightColorsUniform;

                    int PixelSizeUniform;

                    int BaseColorUniform;
                    int ReflectionsColorUniform;
                    int NormalUniform;
                    int ShininessUniform;
                    int BrushUniform;

                    int BaseColorTexture;
                    int ReflectionsColorTexture;
                    int NormalTexture;
                    int ShininessTexture;
                    int BrushTexture;

                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config)
                    {
                        // Data

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
                            GLES20.glShaderSource(FragmentShader, ReadRawTextResource(R.raw.fragment_shader));
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

                        int Program = GLES20.glCreateProgram();
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

                        PixelSizeUniform = GLES20.glGetUniformLocation(Program, "PixelSize");

                        BaseColorUniform = GLES20.glGetUniformLocation(Program, "BaseColor");
                        ReflectionsColorUniform = GLES20.glGetUniformLocation(Program, "ReflectionsColor");
                        NormalUniform = GLES20.glGetUniformLocation(Program, "Normal");
                        ShininessUniform = GLES20.glGetUniformLocation(Program, "Shininess");
                        BrushUniform = GLES20.glGetUniformLocation(Program, "Brush");

                        BaseColorTexture = LoadTextureFromResource(R.drawable.gray_80_128_16x16, false);
                        ReflectionsColorTexture = LoadTextureFromResource(R.drawable.gray_80_128_16x16, false);
                        NormalTexture = LoadTextureFromResource(R.drawable.gray_80_128_16x16, false);
                        ShininessTexture = LoadTextureFromResource(R.drawable.white_16x16, false);
                        BrushTexture = LoadTextureFromResource(R.drawable.gray_80_128_16x16, false);

                        GLES20.glUseProgram(Program);
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height)
                    {
                        GLES20.glViewport(0, 0, width, height);
                        AspectRatio = (float)width / (float)height;
                    }

                    @Override
                    public void onDrawFrame(GL10 gl)
                    {
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
                        GLES20.glUniform3f(
                                ScreenFrontDirectionUniform,
                                DeviceRotationMatrix[2],
                                DeviceRotationMatrix[5],
                                DeviceRotationMatrix[8]
                        );
                        GLES20.glUniform3f(
                                ScreenUpDirectionUniform,
                                DeviceRotationMatrix[1],
                                DeviceRotationMatrix[4],
                                DeviceRotationMatrix[7]
                        );
                        GLES20.glUniform3f(
                                ScreenRightDirectionUniform,
                                DeviceRotationMatrix[0],
                                DeviceRotationMatrix[3],
                                DeviceRotationMatrix[6]
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
                                6,
                                LightDirections,
                                0
                        );
                        GLES20.glUniform3fv(
                                LightReflectionDirectionsUniform,
                                6,
                                LightReflectionDirections,
                                0
                        );
                        GLES20.glUniform3fv(
                                LightColorsUniform,
                                6,
                                LightColors,
                                0
                        );

                        GLES20.glUniform2f(PixelSizeUniform, 0, 0);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, BaseColorTexture);
                        GLES20.glUniform1i(BaseColorUniform, 0);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ReflectionsColorTexture);
                        GLES20.glUniform1i(ReflectionsColorUniform, 1);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, NormalTexture);
                        GLES20.glUniform1i(NormalUniform, 2);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ShininessTexture);
                        GLES20.glUniform1i(ShininessUniform, 3);

                        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, BrushTexture);
                        GLES20.glUniform1i(BrushUniform, 4);

                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                    }

                    int LoadTextureFromFile(String path, int default_texture_resource_id, boolean pixelated)
                    {
                        // TODO
                        return LoadTextureFromResource(default_texture_resource_id, pixelated);
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
                                height = 4096;
                                width = 4096 * aspect_ratio;
                            }
                            else
                            {
                                width = 4096;
                                height = 4096 / aspect_ratio;
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
                        InputStream stream = getResources().openRawResource(id);
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
        }
    }
}
