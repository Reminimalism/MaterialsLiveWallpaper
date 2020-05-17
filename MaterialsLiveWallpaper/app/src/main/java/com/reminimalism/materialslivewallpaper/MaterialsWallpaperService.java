package com.reminimalism.materialslivewallpaper;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
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
                GLSurface.setEGLContextClientVersion(2);
                GLSurface.setPreserveEGLContextOnPause(true);

                // ---------------------------------------------------------------- //

                GLSurface.setRenderer(
                        new GLSurfaceView.Renderer() // -------- RENDERER BEGIN -------- //
                {
                    int PositionAttribute;
                    FloatBuffer TriangleStripPositionValues;

                    float[] RotationVector = new float[4];
                    float[] DeviceRotationMatrix = new float[9];
                    float AspectRatio = 1;

                    int ScreenFrontDirectionUniform;
                    int ScreenUpDirectionUniform;
                    int ScreenRightDirectionUniform;
                    int FOVUniform;

                    int LightDirectionsUniform;
                    int LightReflectionDirectionsUniform;
                    int LightColorsUniform;

                    int PixelSizeUniform;

                    @Override
                    public void onSurfaceCreated(GL10 gl, EGLConfig config)
                    {
                        // Sensors

                        SensorManager SensorManagerInstance = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                        Sensor RotationSensor = SensorManagerInstance.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                        SensorManagerInstance.registerListener(new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event)
                            {
                                if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR)
                                    System.arraycopy(event.values, 0, RotationVector, 0, 4);
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                        }, RotationSensor, SensorManager.SENSOR_DELAY_UI); // TODO: Destroy OnDestroy

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

                        LightDirectionsUniform = GLES20.glGetUniformLocation(Program, "LightDirections");
                        LightReflectionDirectionsUniform = GLES20.glGetUniformLocation(Program, "LightReflectionDirections");
                        LightColorsUniform = GLES20.glGetUniformLocation(Program, "LightColors");

                        PixelSizeUniform = GLES20.glGetUniformLocation(Program, "PixelSize");

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
                                DeviceRotationMatrix[5]
                        );
                        GLES20.glUniform3f(
                                ScreenRightDirectionUniform,
                                DeviceRotationMatrix[0],
                                DeviceRotationMatrix[3],
                                DeviceRotationMatrix[6]
                        ); // TODO: Fix rotation

                        GLES20.glUniform2f(FOVUniform, 0.25f * AspectRatio, 0.25f);

                        float[] LightDirections = {
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
                        GLES20.glUniform3fv(
                                LightDirectionsUniform,
                                6,
                                LightDirections,
                                0
                        );
                        GLES20.glUniform3fv(
                                LightReflectionDirectionsUniform,
                                6,
                                LightDirections, // TODO: Calculate LightReflectionDirections (2x apart)
                                0
                        );
                        GLES20.glUniform3fv(
                                LightColorsUniform,
                                6,
                                LightColors,
                                0
                        );

                        GLES20.glUniform2f(PixelSizeUniform, 0, 0);

                        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
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
        }
    }
}
