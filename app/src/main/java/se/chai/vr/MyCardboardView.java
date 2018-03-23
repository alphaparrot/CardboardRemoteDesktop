package se.chai.vr;

import android.os.Handler;
import android.content.Context;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.Controller.ConnectionStates;
import com.google.vr.sdk.controller.ControllerManager;
import com.google.vr.sdk.controller.ControllerManager.ApiStatus;

/**
 * Created by henrik on 15. 9. 1.
 */
public class MyCardboardView extends GvrView {
    private float mPreviousX, mPreviousY, rotX, rotY;
    private float[] rotMatrix;
    private boolean useManual;

    // These two objects are the primary APIs for interacting with the Daydream controller.
    private ControllerManager controllerManager;
    private Controller controller;

    // The various events we need to handle happen on arbitrary threads. They need to be reposted to
    // the UI thread in order to manipulate the TextViews. This is only required if your app needs to
    // perform actions on the UI thread in response to controller events.
    private Handler uiHandler = new Handler();

    private static final String TAG = "MyCardboardView";

    public MyCardboardView(Context context) {
        super(context);
        init();
    }

    public MyCardboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        rotMatrix = new float[16];
        Matrix.setIdentityM(rotMatrix,0);
        mPreviousX = mPreviousY = 0;
        useManual = false;

        // Start the ControllerManager and acquire a Controller object which represents a single
        // physical controller. Bind our listener to the ControllerManager and Controller.
        EventListener listener = new EventListener();
        controllerManager = new ControllerManager(this.getContext(), listener);
        controller = controllerManager.getController();
        controller.setEventListener(listener);
        controllerManager.start(); //start later...

        Log.i(TAG, "Listening to Controller: " + controller);
    }

    public void setUseManual(boolean use) {
       useManual = use;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!useManual)
            return super.onTouchEvent(e);

        float x = e.getX();
        float y = e.getY();
        float dx = (x - mPreviousX);
        float dy = (y - mPreviousY);
        mPreviousX = x;
        mPreviousY = y;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                rotX += dx / 4;
                rotY += dy / 4;

                Matrix.setIdentityM(rotMatrix, 0);
                Matrix.rotateM(rotMatrix, 0, rotY, 1, 0, 0);
                Matrix.rotateM(rotMatrix, 0, rotX, 0, 1, 0);
                break;
        }

        return true;
    }

    public float[] getRotMatrix() {
        return rotMatrix;
    }

    public void resetRot() {
        rotX = rotY = 0;
        Matrix.setIdentityM(rotMatrix, 0);
    }

    // We receive all events from the Controller through this listener. In this example, our
    // listener handles both ControllerManager.EventListener and Controller.EventListener events.
    // This class is also a Runnable since the events will be reposted to the UI thread.
    private class EventListener extends Controller.EventListener
            implements ControllerManager.EventListener, Runnable {

        // The status of the overall controller API. This is primarily used for error handling since
        // it rarely changes.
        private String apiStatus;

        // The state of a specific Controller connection.
        private int controllerState = ConnectionStates.DISCONNECTED;

        @Override
        public void onApiStatusChanged(int state) {
            apiStatus = ApiStatus.toString(state);
            uiHandler.post(this);
        }

        @Override
        public void onConnectionStateChanged(int state) {
            controllerState = state;
            uiHandler.post(this);
        }

        @Override
        public void onRecentered() {
            // In a real GVR application, this would have implicitly called recenterHeadTracker().
            // Most apps don't care about this, but apps that want to implement custom behavior when a
            // recentering occurs should use this callback.
            // controllerOrientationView.resetYaw();
        }

        @Override
        public void onUpdate() {
            uiHandler.post(this);
        }

        // Process state to UI events.
        @Override
        public void run() {
            // apiStatusView.setText(apiStatus);
            // ConnectionStates.toString(controllerState);
            controller.update();

            //Log.v(TAG, "Controller Orientation: " + controller.orientation);

            Log.v(TAG, String.format("[%s][%s][%s][%s][%s]",
                    controller.appButtonState ? "A" : " ",
                    controller.homeButtonState ? "H" : " ",
                    controller.clickButtonState ? "T" : " ",
                    controller.volumeUpButtonState ? "+" : " ",
                    controller.volumeDownButtonState ? "-" : " "));

            //float[] angles = new float[3];
            //controller.orientation.toYawPitchRollDegrees(angles);
            if (controller.clickButtonState) {
                //set orientation
                Log.i(TAG, "attempting to set orientation");
                //controller.orientation.toRotationMatrix(rotMatrix);
                recenterHeadTracker();
                //Log.d(TAG, "resulting orientation" + rotMatrix);
                //Matrix.setIdentityM(rotMatrix, 0);
            }
        }
    }
}
