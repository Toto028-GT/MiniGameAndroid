package com.example.minigamepong.input;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

public final class TiltController implements SensorEventListener {
    private static final float SENSITIVITY = 4f;

    public interface RotationProvider {
        int getRotation();
    }

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final RotationProvider rotationProvider;
    private boolean registered;
    private float tiltX;
    private float input;

    public TiltController(Context context, RotationProvider provider) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationProvider = provider;
    }

    public boolean enable() {
        if (registered) {
            return true;
        }
        if (sensorManager == null || accelerometer == null) {
            return false;
        }
        registered = sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (!registered) {
            reset();
        }
        return registered;
    }

    public void disable() {
        if (registered && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        registered = false;
        reset();
    }

    private void reset() {
        tiltX = 0f;
        input = 0f;
    }

    public float getInput() {
        return input;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!registered || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        float horizontalAcceleration = getScreenHorizontalAcceleration(event.values);
        tiltX = tiltX * 0.85f + horizontalAcceleration * 0.15f;
        float nextInput = -tiltX / SENSITIVITY;
        if (Math.abs(nextInput) < 0.08f) {
            nextInput = 0f;
        }
        input = Math.max(-1f, Math.min(1f, nextInput));
    }

    private float getScreenHorizontalAcceleration(float[] acceleration) {
        int rotation = rotationProvider.getRotation();
        switch (rotation) {
            case Surface.ROTATION_90:
                return -acceleration[1];
            case Surface.ROTATION_180:
                return -acceleration[0];
            case Surface.ROTATION_270:
                return acceleration[1];
            case Surface.ROTATION_0:
            default:
                return acceleration[0];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No calibration is needed for this controller.
    }
}
