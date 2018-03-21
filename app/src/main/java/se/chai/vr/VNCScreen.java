package se.chai.vr;

import com.google.vr.sdk.base.Eye;
import android.androidVNC.BitmapImplHint;
import android.androidVNC.COLORMODEL;
import android.androidVNC.ConnectionBean;
import android.androidVNC.VncDatabase;
import android.androidVNC.VncView;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import se.chai.cardboardremotedesktop.R;
import se.chai.cardboardremotedesktop.WorldLayoutData;

/**
 * Created by henrik on 15. 6. 16.
 */
public class VNCScreen extends TexturedThing implements SurfaceTexture.OnFrameAvailableListener,
        VncView.IConnectionInfo {

    private boolean mVideoFrameAvailable = false;
    private SurfaceTexture mVideoSurfaceTexture;
    private Surface mVideoSurface;
    private float[] mVideoTextureTransform;

    private VncView vncView;
    private VncDatabase db;
    private ConnectionBean connection;
    private boolean mVncReady = false;
    private int mX = 0, mY = 0;
    private String mExtraHost;
    private String mExtraUsername;
    private String mExtraPassword;
    private String mExtraColorMode;

    private OnVideoSizeChangeListener onVideoSizeChangeListener = null;

    private float size, distance, height, ratio;

    private Context context;
    private float mSpanRads;

    private int mouseU;
    private int aspectU;
    private int centerU;
    private int magU;

    private boolean magnifyEnabled;
    private boolean curveEnabled;
    private boolean viewerMode;
    private boolean useLocalMouse;

    public static final int TEXTURE_MAX_SIZE = 4096;

    public VNCScreen(Engine engine, Context context) {
        super(engine);
        this.context = context;
        useLocalMouse = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_useLocalCursor", true);
    }

    @Override
    public void setupShaders() {
        texture = engine.getTextureId();
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);

        vertexShader = engine.loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.vertex);
        textureShader = engine.loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.texture_external_fragment);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, textureShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);

        positionA = GLES20.glGetAttribLocation(program, "a_Position");
        //mScreenNormalA = GLES20.glGetAttribLocation(screen.program, "a_Normal");
        //mScreenColorA = GLES20.glGetAttribLocation(screen.program, "a_Color");
        textureA = GLES20.glGetAttribLocation(program, "a_TexCoordIn");
        textureU = GLES20.glGetUniformLocation(program, "u_Texture");
        textureTransformU = GLES20.glGetUniformLocation(program, "u_TexTransform");

        mouseU = GLES20.glGetUniformLocation(program, "u_Mouse");
        aspectU = GLES20.glGetUniformLocation(program, "u_Aspect");
        centerU = GLES20.glGetUniformLocation(program, "u_Center");
        magU = GLES20.glGetUniformLocation(program, "u_Mag");

        //mScreenModelA = GLES20.glGetUniformLocation(screen.program, "u_Model");
        //mScreenModelViewA = GLES20.glGetUniformLocation(screen.program, "u_MVMatrix");
        modelViewProjectionA = GLES20.glGetUniformLocation(program, "u_MVP");
        //mScreenLightPosA = GLES20.glGetUniformLocation(screen.program, "u_LightPos");

        GLES20.glEnableVertexAttribArray(positionA);
        //GLES20.glEnableVertexAttribArray(mScreenNormalA);
        //GLES20.glEnableVertexAttribArray(mScreenColorA);
        GLES20.glEnableVertexAttribArray(textureA);

        Engine.checkGLError("Screen program params");
    }

    public boolean initGeometry(int spanDegrees) {
        mSpanRads = (float) Math.toRadians(spanDegrees);
        int subs = spanDegrees / 5;
        if (subs % 2 == 0) subs++;

        float[] u, tm, tl, tr;
        if (curveEnabled) {
            u = WorldLayoutData.GenerateCylinder(subs, 2, mSpanRads);
            tm = WorldLayoutData.GeneratePanoramicTexCords(subs, 2, Eye.Type.MONOCULAR, 1, false);
        } else {
            u = WorldLayoutData.FACE_COORDS;
            tm = WorldLayoutData.FACE_TEXCOORDS_MONO;
        }

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(u.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        vertices = bbVertices.asFloatBuffer();
        vertices.put(u);
        vertices.position(0);

        ByteBuffer bbTexcoords = ByteBuffer.allocateDirect(tm.length * 4);
        bbTexcoords.order(ByteOrder.nativeOrder());
        texCords = bbTexcoords.asFloatBuffer();
        texCords.put(tm);
        texCords.position(0);

        return true;
    }

    public boolean initVnc(String host, String userName, String password, String colorMode, Context con) {
        mExtraHost = host;
        mExtraUsername = userName;
        mExtraPassword = password;
        mExtraColorMode = colorMode;

        vncView = (VncView) ((Activity)con).findViewById(R.id.vncview);

        connection = new ConnectionBean();

        URL url;

        try {
            url = new URL("http://" + mExtraHost);

            connection.setAddress(url.getHost());
            int port = url.getPort();
            connection.setPort(port == -1 ? 5900 : port);
            connection.setUserName(mExtraUsername);
            connection.setPassword(mExtraPassword);
            String colorModel = COLORMODEL.C256.nameString();
            switch (mExtraColorMode) {
                case "24bit":
                    colorModel = COLORMODEL.C24bit.nameString();
                    break;
                case "256":
                    colorModel = COLORMODEL.C256.nameString();
                    break;
                case "64":
                    colorModel = COLORMODEL.C64.nameString();
                    break;
            }
            connection.setColorModel(colorModel);
            connection.setForceFull(BitmapImplHint.FULL);

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        }

        vncView.initializeVncCanvas(connection, new Runnable() {
            public void run() {
                setModes();
            }
        });
        vncView.setConnectionInfoCallback(this);
        return true;
    }

    public void setOnVideoSizeChangeListener(OnVideoSizeChangeListener listener) {
        this.onVideoSizeChangeListener = listener;
    }

    public void setupPosition(float size, float height, float distance) {
        setHeight(height);
        setDistance(distance);
        setSize(size);

        Matrix.setIdentityM(model, 0);
        if (curveEnabled) {
            Matrix.scaleM(model, 0, size, size, size);
        } else {
            Matrix.scaleM(model, 0, size * ratio / 2, size /2, 1.0f);
            Matrix.translateM(model, 0, 0, height, distance);
        }
    }

    public void setSize(float size) {
        this.size = size;
    }

    public float getRatio() {
        return ratio;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    void setModes() {
        mVideoSurfaceTexture = new SurfaceTexture(texture);
        mVideoSurfaceTexture.setOnFrameAvailableListener(this);
        int w = vncView.getImageWidth();
        int h = vncView.getImageHeight();
        ratio = (float) w / (float) h;

        if (w > TEXTURE_MAX_SIZE) { // pref
            w = TEXTURE_MAX_SIZE;
            h = (int)(TEXTURE_MAX_SIZE /ratio);
        }
        mVideoSurfaceTexture.setDefaultBufferSize(w,h);

        mVideoSurface = new Surface(mVideoSurfaceTexture);
        mVideoTextureTransform = new float[16];
        mVideoSurfaceTexture.getTransformMatrix(mVideoTextureTransform);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        p.height = h; p.width = w;
        vncView.setLayoutParams(p);
        vncView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        vncView.layout(0, 0, w, h);

        vncView.setSurface(mVideoSurface);
        if (onVideoSizeChangeListener != null) {
            onVideoSizeChangeListener.onVideoSizeChange(vncView.getImageWidth(), vncView.getImageHeight());
        }
        mVncReady = true;
    }

    public boolean onTrigger(float[] headView) {
        if (vncView == null)
            return false;
        if (mX >= 0 && mX < vncView.getImageWidth()) {
            if (mY >= 0 && mY < vncView.getImageHeight()) {
                vncView.processPointerEvent(mX, mY, MotionEvent.ACTION_DOWN, 0, false, false);
                vncView.processPointerEvent(mX, mY, MotionEvent.ACTION_UP, 0, false, false);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isLookingAtObject(float[] view) {
        if (curveEnabled) {
            float[] res = new float[16];
            Matrix.multiplyMM(res, 0, view, 0, model, 0);
            float[] euler = new float[3];
            Vec4f.getEulerAngles(euler, 0, res);
            return isLookingAtObjectEuler(euler);
        } else {
            return isLookingAtObjectMatrix(view);
        }
    }

    public boolean isLookingAtObjectMatrix(float[] mHeadView) {
        if (!mVncReady)
            return false;

        float[] dist = intersects(mHeadView);

        mX = (int) ((dist[0] + 1)/2.0f * vncView.getImageWidth());
        mY = (int) ((-dist[1] + 1)/2.0f * vncView.getImageHeight());
        Log.d("VNC", "dist[0,1] : (" + dist[0] + ", " + dist[1] + ")  --  MX, MY : (" + mX + ", " + mY + ")");

        if (mX >= 0 && mX < vncView.getImageWidth()) {
            if (mY >= 0 && mY < vncView.getImageHeight()) {
                if (!viewerMode)
                    vncView.processPointerEvent(mX, mY, MotionEvent.ACTION_MOVE, 0, false, false);
                return true;
            }
        }
        return false;
    }


    @Override
    public void draw(int eye, float[] modelViewProjection) {
        if (!mVncReady)
            return;

        GLES20.glUseProgram(program);

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);

        synchronized (this) {
            if (mVideoFrameAvailable) {
                mVideoSurfaceTexture.updateTexImage();
                mVideoSurfaceTexture.getTransformMatrix(mVideoTextureTransform);
                mVideoFrameAvailable = false;
            }
        }

        GLES20.glUniformMatrix4fv(textureTransformU, 1, false, mVideoTextureTransform, 0);

        GLES20.glVertexAttribPointer(positionA, Thing.COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, vertices);

        GLES20.glUniformMatrix4fv(modelViewProjectionA, 1, false, modelViewProjection, 0);

        GLES20.glVertexAttribPointer(textureA, 2, GLES20.GL_FLOAT, false, 0, texCords);
        GLES20.glUniform1f(aspectU, ratio);
        GLES20.glUniform1f(centerU, useLocalMouse ? 0.005f : 0.0f);
        if (magnifyEnabled) {
            GLES20.glUniform1f(magU, 1.5f);
        } else {
            GLES20.glUniform1f(magU, 1.0f);
        }
        GLES20.glUniform2f(mouseU, (float) mX / vncView.getImageWidth(), (float) mY / vncView.getImageHeight());

        GLES20.glUniform1i(textureU, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.limit()/ Thing.COORDS_PER_VERTEX);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            mVideoFrameAvailable = true;
        }
    }

    @Override
    public void show(String message) {
        Log.d("VNC", "Connection message: " + message);
    }

    public void onPause() {
        if (vncView != null) {
            vncView.closeConnection();
        }
    }


    public boolean processKeyEvent(int keycode, KeyEvent evt) {
        if (vncView != null) {
            return vncView.processLocalKeyEvent(keycode, evt);
        }

        return false;
    }

    public boolean processPointerEvent(int x, int y) {
        if (vncView != null) {
            return vncView.processPointerEvent(x, y, MotionEvent.ACTION_MOVE, 0, false, false);
        }

        return false;
    }

    public boolean zoom(int quad) {
        return false;
    }

    public boolean isLookingAtObjectEuler(float[] euler) {
        float rotY, rotX;

        if (!mVncReady)
            return false;

        rotX = -euler[0];
        rotY = -euler[1] + mSpanRads/2;
        mX = (int)((rotY / mSpanRads) * vncView.getImageWidth());
        mY = (int)((Math.tan(rotX) + .5)* vncView.getImageHeight());

        if (mX >= 0 && mX < vncView.getImageWidth()) {
            if (mY >= 0 && mY < vncView.getImageHeight()) {
                if (!viewerMode)
                    vncView.processPointerEvent(mX, mY, MotionEvent.ACTION_MOVE, 0, false, false);
                return true;
            }
        }
        return false;
    }

    public void setMagnifyEnabled(boolean magnifyEnabled) {
        this.magnifyEnabled = magnifyEnabled;
    }

    public void setCurveEnabled(boolean curveEnabled) {
        this.curveEnabled = curveEnabled;
    }

    public void setViewerMode(boolean viewerMode) {
        this.viewerMode = viewerMode;
    }
}

