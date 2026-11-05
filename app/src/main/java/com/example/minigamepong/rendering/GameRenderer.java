package com.example.minigamepong.rendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.minigamepong.game.GameModel;
import com.example.minigamepong.game.GameState;
import com.example.minigamepong.input.JoystickController;
import com.example.minigamepong.game.GameModel.PowerUpType;

public final class GameRenderer {
    private final GameModel model;
    private final RectF playJoystickButton = new RectF();
    private final RectF playTiltButton = new RectF();
    private final RectF retakeButton = new RectF();
    private final RectF useButton = new RectF();
    private final RectF replayButton = new RectF();
    private final RectF menuButton = new RectF();
    private final RectF pauseButton = new RectF();
    private final RectF resumeButton = new RectF();
    private final RectF quitButton = new RectF();
    private final Paint overlayPaint = new Paint();
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint joystickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joystickKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF temporaryRect = new RectF();
    private final Paint powerUpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint powerUpTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameRenderer(GameModel gameModel) {
        model = gameModel;
        ballPaint.setColor(Color.WHITE);
        paddlePaint.setColor(Color.rgb(0, 200, 220));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        joystickBasePaint.setColor(Color.WHITE);
        joystickBasePaint.setAlpha(30);
        joystickKnobPaint.setColor(Color.WHITE);
        joystickKnobPaint.setAlpha(90);
        overlayPaint.setColor(Color.argb(170, 0, 0, 0));
        powerUpTextPaint.setColor(Color.WHITE);
        powerUpTextPaint.setTextAlign(Paint.Align.CENTER);
        powerUpTextPaint.setFakeBoldText(true);
        wallPaint.setColor(Color.rgb(120, 130, 0));
    }

    public void layout(int width, int height, JoystickController joystick) {
        textPaint.setTextSize(width * 0.055f);
        float buttonWidth = width * 0.5f;
        float buttonHeight = height * 0.07f;
        playJoystickButton.set(width / 2f - buttonWidth / 2, height * 0.42f,
                width / 2f + buttonWidth / 2, height * 0.42f + buttonHeight);
        playTiltButton.set(width / 2f - buttonWidth / 2, height * 0.52f,
                width / 2f + buttonWidth / 2, height * 0.52f + buttonHeight);
        retakeButton.set(width / 2f - buttonWidth / 2, height * 0.70f,
                width / 2f + buttonWidth / 2, height * 0.70f + buttonHeight);
        useButton.set(width / 2f - buttonWidth / 2, height * 0.80f,
                width / 2f + buttonWidth / 2, height * 0.80f + buttonHeight);
        replayButton.set(width / 2f - buttonWidth / 2, height * 0.55f,
                width / 2f + buttonWidth / 2, height * 0.55f + buttonHeight);
        menuButton.set(width / 2f - buttonWidth / 2, height * 0.66f,
                width / 2f + buttonWidth / 2, height * 0.66f + buttonHeight);
        float pauseSize = width * 0.1f;
        pauseButton.set(width / 2f - pauseSize / 2f, height * 0.025f,
                width / 2f + pauseSize / 2f, height * 0.025f + pauseSize);
        resumeButton.set(width / 2f - buttonWidth / 2, height * 0.50f,
                width / 2f + buttonWidth / 2, height * 0.50f + buttonHeight);
        quitButton.set(width / 2f - buttonWidth / 2, height * 0.61f,
                width / 2f + buttonWidth / 2, height * 0.61f + buttonHeight);
        joystick.setRadius(width * 0.08f);
    }

    public void draw(Canvas canvas, int width, int height, JoystickController joystick) {
        GameState state = model.getGameState();
        switch (state) {
            case MENU:
                drawMenu(canvas, width, height);
                break;
            case PREVIEW:
                drawPreview(canvas, width, height);
                break;
            case PLAYING:
                drawGame(canvas, width, height, joystick);
                break;
            case GAME_OVER:
                drawGameOver(canvas, width, height);
                break;
            case PAUSED:
                drawGame(canvas, width, height, joystick);
                drawPauseOverlay(canvas, width, height);
                break;
            default:
                break;
        }
    }

    public boolean isPlayJoystickButton(float x, float y) {
        return playJoystickButton.contains(x, y);
    }

    public boolean isPlayTiltButton(float x, float y) {
        return playTiltButton.contains(x, y);
    }

    public boolean isRetakeButton(float x, float y) {
        return retakeButton.contains(x, y);
    }

    public boolean isUseButton(float x, float y) {
        return useButton.contains(x, y);
    }

    public boolean isReplayButton(float x, float y) {
        return replayButton.contains(x, y);
    }

    public boolean isMenuButton(float x, float y) {
        return menuButton.contains(x, y);
    }

    private void drawCentered(Canvas canvas, String text, float y, int width) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, width / 2f, y, textPaint);
    }

    private void drawButton(Canvas canvas, RectF button, String label, int color) {
        buttonPaint.setColor(color);
        canvas.drawRoundRect(button, button.height() / 4f, button.height() / 4f, buttonPaint);

        float savedSize = textPaint.getTextSize();
        float maxWidth = button.width() * 0.9f;
        float textWidth = textPaint.measureText(label);
        if (textWidth > maxWidth) {
            textPaint.setTextSize(savedSize * maxWidth / textWidth);
        }
        textPaint.setTextAlign(Paint.Align.CENTER);
        float y = button.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, button.centerX(), y, textPaint);
        textPaint.setTextSize(savedSize);
    }

    private void drawMenu(Canvas canvas, int width, int height) {
        drawCentered(canvas, "CASSE-BRIQUES", height * 0.30f, width);
        drawButton(canvas, playJoystickButton, "Play joystick", Color.rgb(0, 160, 180));
        drawButton(canvas, playTiltButton, "Play inclinaison", Color.rgb(0, 160, 180));
    }

    private void drawPreview(Canvas canvas, int width, int height) {
        drawCentered(canvas, "Utiliser cette photo ?", height * 0.08f, width);
        Bitmap bitmap = model.getGridBitmap();
        if (bitmap != null) {
            float previewWidth = width * 0.9f;
            float previewHeight = previewWidth * bitmap.getHeight() / bitmap.getWidth();
            float left = (width - previewWidth) / 2f;
            float top = height * 0.12f;
            temporaryRect.set(left, top, left + previewWidth, top + previewHeight);
            canvas.drawBitmap(bitmap, null, temporaryRect, bitmapPaint);
        }
        drawButton(canvas, retakeButton, "Reprendre", Color.rgb(90, 90, 100));
        drawButton(canvas, useButton, "Utiliser", Color.rgb(0, 160, 180));
    }

    private void drawGame(Canvas canvas, int width, int height, JoystickController joystick) {
        RectF[] bricks = model.getBricks();
        for (int i = 0; i < bricks.length; i++) {
            if (!model.isBrickAlive(i)) {
                continue;
            }
            if (model.getGridBitmap() != null) {
                canvas.drawBitmap(model.getGridBitmap(), model.getBrickSources()[i], bricks[i], bitmapPaint);
            } else {
                brickPaint.setColor(Color.rgb(30, 136, 229));
                canvas.drawRoundRect(bricks[i], 8, 8, brickPaint);
            }
        }

        RectF[] wall = model.getWallBricks();
        for (int i = 0; i < wall.length; i++) {
            if (model.isWallPresent(i)) {
                canvas.drawRoundRect(wall[i], 4, 4, wallPaint);
            }
        }

        temporaryRect.set(
                model.getPaddleX() - model.getPaddleWidth() / 2f,
                model.getPaddleTop(),
                model.getPaddleX() + model.getPaddleWidth() / 2f,
                model.getPaddleTop() + model.getPaddleHeight());
        canvas.drawRoundRect(temporaryRect, model.getPaddleHeight() / 2f,
                model.getPaddleHeight() / 2f, paddlePaint);
        for (int i = 0; i < model.getBallCount(); i++) {
            canvas.drawCircle(model.getBallX(i), model.getBallY(i), model.getBallRadius(), ballPaint);
        }
        drawPowerUps(canvas);
        drawEffects(canvas, width, height);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Score : " + model.getScore(), width * 0.04f, height * 0.07f, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Vies : " + model.getLives(), width * 0.96f, height * 0.07f, textPaint);
        drawPauseIcon(canvas);
        if (model.isBallStuck()) {
            drawCentered(canvas, "Toucher pour lancer", height * 0.65f, width);
        }
        if (joystick.isActive()) {
            canvas.drawCircle(joystick.getBaseX(), joystick.getBaseY(), joystick.getRadius(),
                    joystickBasePaint);
            canvas.drawCircle(joystick.getKnobX(), joystick.getKnobY(), joystick.getRadius() * 0.4f,
                    joystickKnobPaint);
        }
    }

    private void drawPowerUps(Canvas canvas) {
        float r = model.getPowerUpRadius();
        powerUpTextPaint.setTextSize(r * 0.95f);
        for (int i = 0; i < model.getPowerUpCount(); i++) {
            PowerUpType type = model.getPowerUpType(i);
            float x = model.getPowerUpX(i);
            float y = model.getPowerUpY(i);
            powerUpPaint.setColor(type.isBonus() ? Color.rgb(46, 170, 90) : Color.rgb(210, 60, 60));
            canvas.drawCircle(x, y, r, powerUpPaint);
            float textY = y - (powerUpTextPaint.descent() + powerUpTextPaint.ascent()) / 2f;
            canvas.drawText(type.getLabel(), x, textY, powerUpTextPaint);
        }
    }

    /** Affiche en bas de l'écran les effets actifs avec leur temps restant. */
    private void drawEffects(Canvas canvas, int width, int height) {
        StringBuilder sb = new StringBuilder();
        appendEffect(sb, "Vitesse x2", model.getFastBallTime());
        appendEffect(sb, "Balle lente", model.getSlowBallTime());
        appendEffect(sb, "Raquette lente", model.getSlowPaddleTime());
        if (sb.length() == 0) {
            return;
        }
        String text = sb.toString();
        float savedSize = textPaint.getTextSize();
        float size = width * 0.035f;
        textPaint.setTextSize(size);
        float maxWidth = width * 0.94f;
        float textWidth = textPaint.measureText(text);
        if (textWidth > maxWidth) {
            textPaint.setTextSize(size * maxWidth / textWidth);
        }
        drawCentered(canvas, text, height * 0.96f, width);
        textPaint.setTextSize(savedSize);
    }

    private void appendEffect(StringBuilder sb, String name, float remaining) {
        if (remaining <= 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("   |   ");
        }
        sb.append(name).append(' ').append((int) Math.ceil(remaining)).append('s');
    }

    private void drawGameOver(Canvas canvas, int width, int height) {
        drawCentered(canvas, model.hasWon() ? "BRAVO !" : "GAME OVER", height * 0.38f, width);
        drawCentered(canvas, "Score : " + model.getScore(), height * 0.48f, width);
        drawButton(canvas, replayButton, "Replay", Color.rgb(0, 160, 180));
        drawButton(canvas, menuButton, "Menu", Color.rgb(90, 90, 100));
    }

    public boolean isPauseButton(float x, float y) {
        temporaryRect.set(pauseButton);
        temporaryRect.inset(-pauseButton.width() * 0.5f, -pauseButton.width() * 0.5f);
        return temporaryRect.contains(x, y);
    }

    public boolean isResumeButton(float x, float y) {
        return resumeButton.contains(x, y);
    }

    public boolean isQuitButton(float x, float y) {
        return quitButton.contains(x, y);
    }

    private void drawPauseIcon(Canvas canvas) {
        buttonPaint.setColor(Color.argb(90, 255, 255, 255));
        float r = pauseButton.width() / 4f;
        canvas.drawRoundRect(pauseButton, r, r, buttonPaint);

        buttonPaint.setColor(Color.WHITE);
        float barW = pauseButton.width() * 0.14f;
        float barH = pauseButton.height() * 0.5f;
        float cx = pauseButton.centerX();
        float cy = pauseButton.centerY();
        float gap = pauseButton.width() * 0.12f;
        canvas.drawRect(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f, buttonPaint);
        canvas.drawRect(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f, buttonPaint);
    }

    private void drawPauseOverlay(Canvas canvas, int width, int height) {
        canvas.drawRect(0, 0, width, height, overlayPaint);
        drawCentered(canvas, "PAUSE", height * 0.38f, width);
        drawButton(canvas, resumeButton, "Reprendre", Color.rgb(0, 160, 180));
        drawButton(canvas, quitButton, "Menu", Color.rgb(90, 90, 100));
    }
}
