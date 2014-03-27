package com.eaglesakura.view.sample;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.eaglesakura.view.GLTextureView;
import com.eaglesakura.view.GLTextureView.GLESVersion;
import com.eaglesakura.view.GLTextureView.Renderer;
import com.eaglesakura.view.GLTextureView.RenderingThreadType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CameraPreviewFilter extends Activity implements Renderer, SurfaceTexture.OnFrameAvailableListener {
    static final String TAG = CameraPreviewFilter.class.getSimpleName();

    GLTextureView glTextureView;

    Handler uiHandler = new Handler(Looper.getMainLooper());
    private SurfaceTexture mCameraSurfaceTexture;
    private int mCameraSurfaceGlTexture;
    private int mUniformTexture;
    private int mProgram;
    private int mAttribPosition;
    private int mAttribTexCoords;
    private FloatBuffer mTriangleVertices;
    private int mLoadedTexture;

    public CameraPreviewFilter() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        glTextureView = new GLTextureView(this);

        // Setup GLTextureView
        {
            glTextureView.setVersion(GLESVersion.OpenGLES20); // set OpenGL Version
            //            glTextureView.setSurfaceSpec(SurfaceColorSpec.RGBA8, true, false); // Default RGBA8 depth(true) stencil(false)
            glTextureView.setRenderingThreadType(RenderingThreadType.RequestThread); // Default BackgroundThread
            glTextureView.setRenderer(this);
        }
                final ImageView view = new ImageView(this);
        addContentView(view, new ViewGroup.LayoutParams(200,200));
        view.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.addbank_choice_cell_active_middle));

        RelativeLayout rl = new RelativeLayout(this);
        rl.setBackgroundColor(0);
        rl.addView(glTextureView);
        glTextureView.setOpaque(false);
        rl.setAlpha(1.0f);
        addContentView(rl, new ViewGroup.LayoutParams(800, 800));

    }

    @Override
    protected void onPause() {
        glTextureView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glTextureView.onResume();

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        createCameraPreview();

        createQuadShader();

        // rendering loop on UI thread
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                glTextureView.requestRender();
                uiHandler.postDelayed(this, 16);
            }
        });
    }

    private int buildProgram(String vertex, String fragment) {
        int vertexShader = buildShader(vertex, GLES20.GL_VERTEX_SHADER);
        if (vertexShader == 0) return 0;

        int fragmentShader = buildShader(fragment, GLES20.GL_FRAGMENT_SHADER);
        if (fragmentShader == 0) return 0;

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        checkGlError();

        GLES20.glAttachShader(program, fragmentShader);
        checkGlError();

        GLES20.glLinkProgram(program);
        checkGlError();

        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(program);
            Log.d(TAG, "Error while linking program:\n" + error);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            GLES20.glDeleteProgram(program);
            return 0;
        }

        return program;
    }

    private int buildShader(String source, int type) {
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, source);
        checkGlError();

        GLES20.glCompileShader(shader);
        checkGlError();

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetShaderInfoLog(shader);
            Log.d(TAG, "Error while compiling shader:\n" + error);
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    private void createQuadShader() {

        mLoadedTexture = loadTexture(R.drawable.addbank_choice_cell_active_middle);

        mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length
                * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);

        mProgram = buildProgram(sSimpleVS, sSimpleFS);

        mAttribPosition = GLES20.glGetAttribLocation(mProgram, "position");
        checkGlError();

        mAttribTexCoords = GLES20.glGetAttribLocation(mProgram, "texCoords");
        checkGlError();

        mUniformTexture = GLES20.glGetUniformLocation(mProgram, "texture");
        checkGlError();


    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, String.format("onSurfaceChanged(%d x %d)", width, height));
    }

    private static final String sSimpleVS =
            "attribute vec4 position;\n" +
                    "attribute vec2 texCoords;\n" +
                    "varying highp vec2 outTexCoords;\n" +
                    "\nvoid main(void) {\n" +
                    "    outTexCoords = texCoords;\n" +
                    "    gl_Position = position;\n" +
                    "}\n\n";
//    private static final String sSimpleFS =
//                    "precision mediump float;\n\n" +
//                    "varying vec2 outTexCoords;\n" +
//                    "uniform sampler2D texture;\n" +
//                    "\nvoid main(void) {\n" +
//                    "    vec4 texColor = texture2D(texture, outTexCoords);\n" +
//                    "    gl_FragColor = vec4(outTexCoords.y, outTexCoords.y, outTexCoords.y, 1.0);\n" +
//                    "    gl_FragColor = vec4(texColor.x, texColor.y, texColor.z,1.0);\n" +
//                    "}\n\n";
    private static final String sSimpleFS =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n\n" +
                    "varying highp vec2 outTexCoords;\n" +
                    "uniform samplerExternalOES texture;\n" +
                    "\nvoid main(void) {\n" +
                    "    highp vec4 texColor = texture2D(texture, outTexCoords);\n" +
                    " gl_FragColor = vec4(texColor.x, 0,0,0.5); \n" +
                    "}\n\n";

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
            1.0f,  1.0f, 0.0f, 1.0f, 1.0f,
    };

    private int loadTexture(int resource) {
        int[] textures = new int[1];

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, textures, 0);
        checkGlError();

        int texture = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        checkGlError();

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resource);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap, GLES20.GL_UNSIGNED_BYTE, 0);
        checkGlError();

        bitmap.recycle();

        return texture;
    }

    @Override
    public void onDrawFrame(GL10 gl) {

        mCameraSurfaceTexture.updateTexImage();

        GLES20.glClearColor(0, 0, 0, 0.0f);
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_BLEND);
//        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ZERO);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ZERO);

        GLES20.glUseProgram(mProgram);
        checkGlError();

        GLES20.glEnableVertexAttribArray(mAttribPosition);
        checkGlError();

        GLES20.glEnableVertexAttribArray(mAttribTexCoords);
        checkGlError();

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, mCameraSurfaceGlTexture);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mLoadedTexture);
        checkGlError();
        GLES20.glUniform1i(mUniformTexture, 0);
        checkGlError();

        // drawQuad
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(mAttribPosition, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(mAttribTexCoords, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);


    }

    @Override
    public void onSurfaceDestroyed(GL10 gl) {
        Log.d(TAG, String.format("onSurfaceDestroyed"));
    }

    private Camera mCamera;
    private TextureView mTextureView;

    private void checkGlError() {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "GL error = 0x" + Integer.toHexString(error));
            throw new RuntimeException("GL ERROR");
        }
    }

    private static int GL_TEXTURE_EXTERNAL_OES = 0x8D65;


    void createCameraPreview() {

        int[] textures = new int[1];

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, textures, 0);
        checkGlError();
        mCameraSurfaceGlTexture = textures[0];

        int texture = textures[0];
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);
        checkGlError();

        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);


        checkGlError();

        mCameraSurfaceTexture = new SurfaceTexture(texture);
        mCameraSurfaceTexture.setOnFrameAvailableListener(this);
        mCamera = Camera.open();
        try {
            mCamera.setPreviewTexture(mCameraSurfaceTexture);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

//        addContentView(mTextureView, new ViewGroup.LayoutParams(200, 200));
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    }
}
