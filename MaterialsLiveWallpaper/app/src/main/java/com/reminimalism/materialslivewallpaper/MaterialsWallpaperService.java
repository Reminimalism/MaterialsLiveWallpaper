package com.reminimalism.materialslivewallpaper;

import android.app.ActivityManager;
import android.content.Context;
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
                        GLES20.glUseProgram(Program);
                    }

                    @Override
                    public void onSurfaceChanged(GL10 gl, int width, int height)
                    {
                        GLES20.glViewport(0, 0, width, height);
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
