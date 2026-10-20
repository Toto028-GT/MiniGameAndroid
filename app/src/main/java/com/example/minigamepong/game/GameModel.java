package com.example.minigamepong.game;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

public final class GameModel {
    public static final int ROWS = 7;
    public static final int COLS = 7;
    private static final int START_LIVES = 3;

    private final RectF[] bricks = new RectF[ROWS * COLS];
    private final Rect[] brickSources = new Rect[ROWS * COLS];
    private final boolean[] brickAlive = new boolean[ROWS * COLS];

    private int width;
    private int height;
    private float ballX;
    private float ballY;
    private float ballRadius;
    private float ballVX;
    private float ballVY;
    private float ballSpeed;
    private boolean ballStuck = true;
    private float paddleX;
    private float paddleW;
    private float paddleH;
    private float paddleTop;
    private float paddleMaxSpeed;
    private int bricksLeft;
    private int score;
    private int lives;
    private boolean won;
    private Bitmap photo;
    private Bitmap gridBitmap;
    private GameState gameState = GameState.MENU;

    public GameModel() {
        for (int i = 0; i < bricks.length; i++) {
            bricks[i] = new RectF();
            brickSources[i] = new Rect();
        }
    }

    public void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;

        paddleW = width * 0.22f;
        paddleH = height * 0.025f;
        paddleTop = height - height * 0.10f - paddleH;
        ballRadius = width * 0.022f;
        ballSpeed = width * 0.012f;
        paddleMaxSpeed = width * 0.015f;

        float margin = width * 0.04f;
        float gap = width * 0.008f;
        float top = height * 0.12f;
        float brickWidth = (width - 2 * margin - (COLS - 1) * gap) / COLS;
        float brickHeight = height * 0.045f;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                float left = margin + column * (brickWidth + gap);
                float brickTop = top + row * (brickHeight + gap);
                bricks[row * COLS + column].set(
                        left, brickTop, left + brickWidth, brickTop + brickHeight);
            }
        }
        buildTiles();
        resetGame();
    }

    public void setPhoto(Bitmap bitmap) {
        photo = bitmap;
        buildTiles();
    }

    public float getGridAspect() {
        if (bricks[0] == null || bricks[0].width() == 0 || bricks[ROWS * COLS - 1].height() == 0) {
            return 1f;
        }
        float gridWidth = bricks[COLS - 1].right - bricks[0].left;
        float gridHeight = bricks[ROWS * COLS - 1].bottom - bricks[0].top;
        return gridWidth / gridHeight;
    }

    private void buildTiles() {
        if (photo == null || width <= 0 || height <= 0) {
            return;
        }

        float gridLeft = bricks[0].left;
        float gridTop = bricks[0].top;
        float gridRight = bricks[COLS - 1].right;
        float gridBottom = bricks[ROWS * COLS - 1].bottom;
        int gridWidth = Math.round(gridRight - gridLeft);
        int gridHeight = Math.round(gridBottom - gridTop);
        if (gridWidth <= 0 || gridHeight <= 0) {
            return;
        }

        float targetRatio = (float) gridWidth / gridHeight;
        int photoWidth = photo.getWidth();
        int photoHeight = photo.getHeight();
        int cropWidth;
        int cropHeight;
        if ((float) photoWidth / photoHeight > targetRatio) {
            cropHeight = photoHeight;
            cropWidth = Math.round(photoHeight * targetRatio);
        } else {
            cropWidth = photoWidth;
            cropHeight = Math.round(photoWidth / targetRatio);
        }
        Bitmap cropped = Bitmap.createBitmap(
                photo, (photoWidth - cropWidth) / 2, (photoHeight - cropHeight) / 2,
                cropWidth, cropHeight);
        gridBitmap = Bitmap.createScaledBitmap(cropped, gridWidth, gridHeight, true);

        for (int i = 0; i < bricks.length; i++) {
            RectF brick = bricks[i];
            brickSources[i].set(
                    Math.max(0, Math.round(brick.left - gridLeft)),
                    Math.max(0, Math.round(brick.top - gridTop)),
                    Math.min(gridWidth, Math.round(brick.right - gridLeft)),
                    Math.min(gridHeight, Math.round(brick.bottom - gridTop)));
        }
    }

    public void startPlaying() {
        resetGame();
        gameState = GameState.PLAYING;
    }

    public void launchBall() {
        if (!ballStuck) {
            return;
        }
        ballStuck = false;
        float direction = Math.random() < 0.5 ? -1 : 1;
        ballVX = direction * ballSpeed * 0.5f;
        ballVY = -ballSpeed * 0.87f;
    }

    public void update(float horizontalInput) {
        if (gameState != GameState.PLAYING) {
            return;
        }

        if (ballStuck) {
            ballX = paddleX;
            ballY = paddleTop - ballRadius;
            return;
        }

        float halfPaddle = paddleW / 2f;
        paddleX += horizontalInput * paddleMaxSpeed;
        paddleX = Math.max(halfPaddle, Math.min(width - halfPaddle, paddleX));

        ballX += ballVX;
        ballY += ballVY;

        if (ballX - ballRadius < 0) {
            ballX = ballRadius;
            ballVX = Math.abs(ballVX);
        }
        if (ballX + ballRadius > width) {
            ballX = width - ballRadius;
            ballVX = -Math.abs(ballVX);
        }
        if (ballY - ballRadius < 0) {
            ballY = ballRadius;
            ballVY = Math.abs(ballVY);
        }

        if (ballY - ballRadius > height) {
            lives--;
            if (lives <= 0) {
                gameState = GameState.GAME_OVER;
            } else {
                resetBall();
            }
            return;
        }

        collidePaddle();
        collideBricks();
        if (bricksLeft == 0) {
            won = true;
            gameState = GameState.GAME_OVER;
        }
    }

    private void collidePaddle() {
        float left = paddleX - paddleW / 2f;
        float right = paddleX + paddleW / 2f;
        boolean overPaddle = ballX + ballRadius >= left && ballX - ballRadius <= right;
        boolean touchesTop = ballY + ballRadius >= paddleTop
                && ballY + ballRadius <= paddleTop + paddleH + ballSpeed;

        if (ballVY > 0 && overPaddle && touchesTop) {
            float offset = (ballX - paddleX) / (paddleW / 2f);
            offset = Math.max(-1f, Math.min(1f, offset));
            double angle = Math.toRadians(offset * 60);
            ballVX = (float) (ballSpeed * Math.sin(angle));
            ballVY = (float) (-ballSpeed * Math.cos(angle));
            ballY = paddleTop - ballRadius;
        }
    }

    private void collideBricks() {
        int hit = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) {
                continue;
            }
            RectF brick = bricks[i];
            float closestX = Math.max(brick.left, Math.min(ballX, brick.right));
            float closestY = Math.max(brick.top, Math.min(ballY, brick.bottom));
            float dx = ballX - closestX;
            float dy = ballY - closestY;
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared <= ballRadius * ballRadius && distanceSquared < bestDistance) {
                bestDistance = distanceSquared;
                hit = i;
            }
        }
        if (hit < 0) {
            return;
        }

        brickAlive[hit] = false;
        bricksLeft--;
        score += 10;

        RectF brick = bricks[hit];
        float closestX = Math.max(brick.left, Math.min(ballX, brick.right));
        float closestY = Math.max(brick.top, Math.min(ballY, brick.bottom));
        float dx = ballX - closestX;
        float dy = ballY - closestY;
        if (dx == 0 && dy == 0) {
            ballVY = -ballVY;
        } else if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) {
                ballVX = -Math.abs(ballVX);
                ballX = brick.left - ballRadius;
            } else {
                ballVX = Math.abs(ballVX);
                ballX = brick.right + ballRadius;
            }
        } else if (dy < 0) {
            ballVY = -Math.abs(ballVY);
            ballY = brick.top - ballRadius;
        } else {
            ballVY = Math.abs(ballVY);
            ballY = brick.bottom + ballRadius;
        }
    }

    private void resetGame() {
        score = 0;
        lives = START_LIVES;
        won = false;
        bricksLeft = ROWS * COLS;
        for (int i = 0; i < brickAlive.length; i++) {
            brickAlive[i] = true;
        }
        paddleX = width / 2f;
        resetBall();
    }

    private void resetBall() {
        ballStuck = true;
        ballX = paddleX;
        ballY = paddleTop - ballRadius;
        ballVX = 0;
        ballVY = 0;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState state) {
        gameState = state;
    }

    public RectF[] getBricks() {
        return bricks;
    }

    public Rect[] getBrickSources() {
        return brickSources;
    }

    public boolean isBrickAlive(int index) {
        return brickAlive[index];
    }

    public Bitmap getGridBitmap() {
        return gridBitmap;
    }

    public float getBallX() {
        return ballX;
    }

    public float getBallY() {
        return ballY;
    }

    public float getBallRadius() {
        return ballRadius;
    }

    public float getPaddleX() {
        return paddleX;
    }

    public float getPaddleWidth() {
        return paddleW;
    }

    public float getPaddleHeight() {
        return paddleH;
    }

    public float getPaddleTop() {
        return paddleTop;
    }

    public boolean isBallStuck() {
        return ballStuck;
    }

    public int getScore() {
        return score;
    }

    public int getLives() {
        return lives;
    }

    public boolean hasWon() {
        return won;
    }
}
