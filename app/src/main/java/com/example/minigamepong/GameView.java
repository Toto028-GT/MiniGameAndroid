package com.example.minigamepong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import com.example.minigamepong.game.ControlMode;
import com.example.minigamepong.game.GameModel;
import com.example.minigamepong.game.GameState;
import com.example.minigamepong.input.JoystickController;
import com.example.minigamepong.input.TiltController;
import com.example.minigamepong.rendering.GameRenderer;

public class GameView extends View {
    public interface PhotoRequestListener {
        void onPhotoRequested();
    }

    private final GameModel model = new GameModel();
    private final GameRenderer renderer = new GameRenderer(model);
    private final JoystickController joystick = new JoystickController();
    private final TiltController tiltController;
    private PhotoRequestListener photoListener;
    private ControlMode controlMode = ControlMode.JOYSTICK;

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            float input = controlMode == ControlMode.TILT
                    ? tiltController.getInput() : joystick.getInput();
            model.update(input);
            invalidate();
            postDelayed(this, 16);
        }
    };

    public GameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(20, 24, 38));
        tiltController = new TiltController(context, () -> {
            if (getDisplay() == null) {
                return Surface.ROTATION_0;
            }
            return getDisplay().getRotation();
        });
    }

    public void setPhotoRequestListener(PhotoRequestListener listener) {
        photoListener = listener;
    }

    public float getGridAspect() {
        return model.getGridAspect();
    }

    public void setPhoto(Bitmap bitmap) {
        model.setPhoto(bitmap);
        model.setGameState(GameState.PREVIEW);
        invalidate();
    }

    public void startGame() {
        removeCallbacks(gameLoop);
        if (controlMode == ControlMode.TILT) {
            enableTilt();
        }
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
        tiltController.disable();
        if (model.getGameState() == GameState.PLAYING) {
            model.setGameState(GameState.PAUSED);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopGame();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        model.resize(width, height);
        renderer.layout(width, height, joystick);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        renderer.draw(canvas, getWidth(), getHeight(), joystick);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        switch (model.getGameState()) {
            case MENU:
                if (action == MotionEvent.ACTION_UP) {
                    if (renderer.isPlayJoystickButton(x, y)) {
                        setControlMode(ControlMode.JOYSTICK);
                        requestPhoto();
                    } else if (renderer.isPlayTiltButton(x, y)) {
                        setControlMode(ControlMode.TILT);
                        requestPhoto();
                    }
                }
                break;
            case PREVIEW:
                if (action == MotionEvent.ACTION_UP) {
                    if (renderer.isRetakeButton(x, y)) {
                        requestPhoto();
                    } else if (renderer.isUseButton(x, y)) {
                        startPlaying();
                    }
                }
                break;
            case GAME_OVER:
                if (action == MotionEvent.ACTION_UP) {
                    if (renderer.isReplayButton(x, y)) {
                        startPlaying();
                    } else if (renderer.isMenuButton(x, y)) {
                        model.setGameState(GameState.MENU);
                    }
                }
                break;
            case PLAYING:
                if (action == MotionEvent.ACTION_DOWN && renderer.isPauseButton(x, y)) {
                    joystick.reset();
                    model.setGameState(GameState.PAUSED);
                    break;
                }
                if (controlMode == ControlMode.TILT) {
                    if (action == MotionEvent.ACTION_UP && model.isBallStuck()) {
                        model.launchBall();
                    }
                } else {
                    joystick.handleTouch(event, model.isBallStuck(), model::launchBall);
                }
                break;
            case PAUSED:
                if (action == MotionEvent.ACTION_UP) {
                    if (renderer.isResumeButton(x, y)) {
                        model.setGameState(GameState.PLAYING);
                    } else if (renderer.isQuitButton(x, y)) {
                        model.setGameState(GameState.MENU);
                    }
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void setControlMode(ControlMode mode) {
        controlMode = mode;
        joystick.reset();
        if (mode == ControlMode.TILT) {
            enableTilt();
        } else {
            tiltController.disable();
        }
    }

    private void enableTilt() {
        if (!tiltController.enable()) {
            controlMode = ControlMode.JOYSTICK;
        }
    }

    private void startPlaying() {
        model.startPlaying();
        joystick.reset();
    }

    private void requestPhoto() {
        if (photoListener != null) {
            photoListener.onPhotoRequested();
        }
    }
}
