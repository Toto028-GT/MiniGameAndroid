package com.example.minigamepong.input;

import android.view.MotionEvent;

public final class JoystickController {
    private boolean active;
    private boolean moved;
    private float baseX;
    private float baseY;
    private float knobX;
    private float knobY;
    private float radius;
    private float input;

    public void setRadius(float joystickRadius) {
        radius = joystickRadius;
    }

    public boolean handleTouch(MotionEvent event, boolean ballStuck, Runnable launchAction) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                active = true;
                moved = false;
                baseX = knobX = event.getX();
                baseY = knobY = event.getY();
                input = 0f;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!active) {
                    break;
                }
                float dx = event.getX() - baseX;
                float dy = event.getY() - baseY;
                float distance = (float) Math.hypot(dx, dy);
                if (distance > radius) {
                    dx *= radius / distance;
                    dy *= radius / distance;
                }
                knobX = baseX + dx;
                knobY = baseY + dy;
                input = dx / radius;
                if (Math.abs(input) < 0.01f) {
                    input = 0f;
                } else {
                    moved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (ballStuck && !moved) {
                    launchAction.run();
                }
                reset();
                break;
            case MotionEvent.ACTION_CANCEL:
                reset();
                break;
            default:
                break;
        }
        return true;
    }

    public void reset() {
        active = false;
        moved = false;
        input = 0f;
    }

    public boolean isActive() {
        return active;
    }

    public float getInput() {
        return input;
    }

    public float getBaseX() {
        return baseX;
    }

    public float getBaseY() {
        return baseY;
    }

    public float getKnobX() {
        return knobX;
    }

    public float getKnobY() {
        return knobY;
    }

    public float getRadius() {
        return radius;
    }
}
