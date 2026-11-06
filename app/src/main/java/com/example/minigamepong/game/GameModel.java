package com.example.minigamepong.game;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

public final class GameModel {
    // Briques plus petites et plus nombreuses
    public static final int ROWS = 14;
    public static final int COLS = 14;
    private static final int START_LIVES = 3;
    private static final int MAX_BALLS = 50;

    // --- Réglages des bonus / malus ---
    private static final float SPAWN_PER_SECOND = 0.18f;   // ~1 objet toutes les 5-6 s
    private static final float SPAWN_SPREAD = 0.25f;       // dispersion horizontale autour du milieu (0 = pile au centre)
    private static final float SPEED_UP_DURATION = 10f;    // secondes
    private static final float BALL_SLOW_DURATION = 10f;   // secondes
    private static final float PADDLE_SLOW_DURATION = 5f;  // secondes
    private static final float PADDLE_SLOW_FACTOR = 0.35f;

    public enum PowerUpType {
        //          label  bonus  poids (plus petit = plus rare)
        MULTI_2("x2", true, 3f),
        MULTI_5("x5", true, 2f),
        SPEED_UP("V+", false, 3f),
        BALL_SLOW("V-", true, 3f),
        PADDLE_SLOW("P-", false, 3f);

        private final String label;
        private final boolean bonus;
        private final float weight;

        PowerUpType(String label, boolean bonus, float weight) {
            this.label = label;
            this.bonus = bonus;
            this.weight = weight;
        }

        public String getLabel() { return label; }
        public boolean isBonus() { return bonus; }
    }

    private static final class Ball {
        float x, y, vx, vy;
    }

    private static final class PowerUp {
        float x, y;
        PowerUpType type;
    }

    private final RectF[] bricks = new RectF[ROWS * COLS];
    private final Rect[] brickSources = new Rect[ROWS * COLS];
    private final boolean[] brickAlive = new boolean[ROWS * COLS];
    private final List<Ball> balls = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final RectF[] wallBricks = new RectF[COLS];
    private final boolean[] wallPresent = new boolean[COLS];
    private final java.util.Random random = new java.util.Random();

    private int width;
    private int height;
    private float ballRadius;
    private float ballSpeed;
    private boolean ballStuck = true;
    private float paddleX;
    private float paddleW;
    private float paddleH;
    private float paddleTop;
    private float paddleMaxSpeed;
    private float powerUpRadius;
    private float powerUpFallSpeed;
    private int bricksLeft;
    private int score;
    private int lives;
    private boolean won;
    private Bitmap photo;
    private Bitmap gridBitmap;
    private GameState gameState = GameState.MENU;

    // Effets actifs (temps restant en secondes)
    private float fastBallTime;
    private float slowBallTime;
    private float slowPaddleTime;
    private long lastNanos;

    public GameModel() {
        for (int i = 0; i < bricks.length; i++) {
            bricks[i] = new RectF();
            brickSources[i] = new Rect();
        }
        for (int i = 0; i < wallBricks.length; i++) {
            wallBricks[i] = new RectF();
        }
    }

    public void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;

        paddleW = width * 0.22f;
        paddleH = height * 0.025f;
        paddleTop = height - height * 0.10f - paddleH;
        ballRadius = width * 0.018f;
        ballSpeed = width * 0.012f;
        paddleMaxSpeed = width * 0.015f;
        powerUpRadius = width * 0.035f;
        powerUpFallSpeed = height * 0.004f;

        float margin = width * 0.04f;
        float gap = width * 0.004f;
        float top = height * 0.12f;
        float brickWidth = (width - 2 * margin - (COLS - 1) * gap) / COLS;
        float brickHeight = height * 0.022f;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                float left = margin + column * (brickWidth + gap);
                float brickTop = top + row * (brickHeight + gap);
                bricks[row * COLS + column].set(
                        left, brickTop, left + brickWidth, brickTop + brickHeight);
            }
        }
        float wallTop = top + ROWS * (brickHeight + gap);
        for (int column = 0; column < COLS; column++) {
            float left = margin + column * (brickWidth + gap);
            wallBricks[column].set(left, wallTop, left + brickWidth, wallTop + brickHeight);
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
        if (!ballStuck || balls.isEmpty()) {
            return;
        }
        ballStuck = false;
        Ball ball = balls.get(0);
        float direction = Math.random() < 0.5 ? -1 : 1;
        ball.vx = direction * ballSpeed * 0.5f;
        ball.vy = -ballSpeed * 0.87f;
    }

    public void update(float horizontalInput) {
        if (gameState != GameState.PLAYING) {
            return;
        }
        float dt = computeDt();

        if (ballStuck) {
            Ball ball = balls.get(0);
            ball.x = paddleX;
            ball.y = paddleTop - ballRadius;
            return;
        }

        updateEffects(dt);

        float halfPaddle = paddleW / 2f;
        float paddleSpeed = paddleMaxSpeed * (slowPaddleTime > 0 ? PADDLE_SLOW_FACTOR : 1f);
        paddleX += horizontalInput * paddleSpeed;
        paddleX = Math.max(halfPaddle, Math.min(width - halfPaddle, paddleX));

        float speedFactor = getBallSpeedFactor();
        for (int i = balls.size() - 1; i >= 0; i--) {
            if (!moveBall(balls.get(i), speedFactor)) {
                balls.remove(i);
            }
        }

        if (balls.isEmpty()) {
            lives--;
            if (lives <= 0) {
                gameState = GameState.GAME_OVER;
            } else {
                resetBall();
            }
            return;
        }

        if (bricksLeft == 0) {
            won = true;
            gameState = GameState.GAME_OVER;
            return;
        }

        updatePowerUps(dt);
    }

    // ---------- Temps et effets ----------

    /** Le temps n'avance que pendant PLAYING : les effets sont donc gelés pendant la pause. */
    private float computeDt() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min((now - lastNanos) / 1_000_000_000f, 0.05f);
        lastNanos = now;
        return dt;
    }

    private void updateEffects(float dt) {
        fastBallTime = Math.max(0f, fastBallTime - dt);
        slowBallTime = Math.max(0f, slowBallTime - dt);
        slowPaddleTime = Math.max(0f, slowPaddleTime - dt);
    }

    /** x2 si bonus vitesse, x0.5 si malus, les deux s'annulent. */
    private float getBallSpeedFactor() {
        float factor = 1f;
        if (fastBallTime > 0) factor *= 1.5f;
        if (slowBallTime > 0) factor *= 0.5f;
        return factor;
    }

    // ---------- Bonus / malus ----------

    private void updatePowerUps(float dt) {
        if (Math.random() < SPAWN_PER_SECOND * dt) {
            spawnPowerUp();
        }

        float left = paddleX - paddleW / 2f;
        float right = paddleX + paddleW / 2f;
        for (int i = powerUps.size() - 1; i >= 0; i--) {
            PowerUp p = powerUps.get(i);
            p.y += powerUpFallSpeed;

            boolean inX = p.x + powerUpRadius >= left && p.x - powerUpRadius <= right;
            boolean inY = p.y + powerUpRadius >= paddleTop
                    && p.y - powerUpRadius <= paddleTop + paddleH;
            if (inX && inY) {
                applyPowerUp(p.type);
                powerUps.remove(i);
            } else if (p.y - powerUpRadius > height) {
                powerUps.remove(i);
            }
        }
    }

    private void spawnPowerUp() {
        PowerUp p = new PowerUp();
        p.x = width / 2f + (float) (Math.random() * 2 - 1) * width * SPAWN_SPREAD;
        p.y = height / 2f;
        p.type = randomType();
        powerUps.add(p);
    }

    private PowerUpType randomType() {
        PowerUpType[] types = PowerUpType.values();
        float total = 0f;
        for (PowerUpType t : types) total += t.weight;
        float r = (float) Math.random() * total;
        for (PowerUpType t : types) {
            r -= t.weight;
            if (r <= 0f) return t;
        }
        return types[0];
    }

    private void applyPowerUp(PowerUpType type) {
        switch (type) {
            case MULTI_2:
                multiplyBalls(2);
                break;
            case MULTI_5:
                multiplyBalls(5);
                break;
            case SPEED_UP:
                fastBallTime = SPEED_UP_DURATION;
                break;
            case BALL_SLOW:
                slowBallTime = BALL_SLOW_DURATION;
                break;
            case PADDLE_SLOW:
                slowPaddleTime = PADDLE_SLOW_DURATION;
                break;
        }
    }

    /** Chaque balle existante devient n balles, avec des angles légèrement différents. */
    private void multiplyBalls(int n) {
        int count = balls.size();
        for (int i = 0; i < count; i++) {
            Ball src = balls.get(i);
            for (int k = 1; k < n; k++) {
                if (balls.size() >= MAX_BALLS) {
                    return;
                }
                double degrees = ((k + 1) / 2) * 15 * (k % 2 == 1 ? 1 : -1);
                double rad = Math.toRadians(degrees);
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);
                Ball clone = new Ball();
                clone.x = src.x;
                clone.y = src.y;
                clone.vx = src.vx * cos - src.vy * sin;
                clone.vy = src.vx * sin + src.vy * cos;
                balls.add(clone);
            }
        }
    }

    // ---------- Physique d'une balle ----------

    /** @return false si la balle est tombée en bas de l'écran. */
    private boolean moveBall(Ball b, float speedFactor) {
        b.x += b.vx * speedFactor;
        b.y += b.vy * speedFactor;

        if (b.x - ballRadius < 0) {
            b.x = ballRadius;
            b.vx = Math.abs(b.vx);
        }
        if (b.x + ballRadius > width) {
            b.x = width - ballRadius;
            b.vx = -Math.abs(b.vx);
        }
        if (b.y - ballRadius < 0) {
            b.y = ballRadius;
            b.vy = Math.abs(b.vy);
        }
        if (b.y - ballRadius > height) {
            return false;
        }

        keepBallMovingVertically(b);

        collidePaddle(b);
        if (!collideWall(b)) {
            collideBricks(b);
        }
        return true;
    }

    private void collidePaddle(Ball b) {
        float left = paddleX - paddleW / 2f;
        float right = paddleX + paddleW / 2f;
        boolean overPaddle = b.x + ballRadius >= left && b.x - ballRadius <= right;
        boolean touchesTop = b.y + ballRadius >= paddleTop
                && b.y + ballRadius <= paddleTop + paddleH + ballSpeed * 2f;

        if (b.vy > 0 && overPaddle && touchesTop) {
            float offset = (b.x - paddleX) / (paddleW / 2f);
            offset = Math.max(-1f, Math.min(1f, offset));
            double angle = Math.toRadians(offset * 60);
            b.vx = (float) (ballSpeed * Math.sin(angle));
            b.vy = (float) (-ballSpeed * Math.cos(angle));
            b.y = paddleTop - ballRadius;
        }
    }

    private boolean collideWall(Ball b) {
        for (int i = 0; i < wallBricks.length; i++) {
            if (!wallPresent[i]) {
                continue;
            }
            RectF w = wallBricks[i];
            float closestX = Math.max(w.left, Math.min(b.x, w.right));
            float closestY = Math.max(w.top, Math.min(b.y, w.bottom));
            float dx = b.x - closestX;
            float dy = b.y - closestY;
            if (dx * dx + dy * dy <= ballRadius * ballRadius) {
                bounceOff(b, w);   // rebond, mais la brique n'est jamais détruite
                return true;
            }
        }
        return false;
    }

    private void collideBricks(Ball b) {
        int hit = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) {
                continue;
            }
            RectF brick = bricks[i];
            float closestX = Math.max(brick.left, Math.min(b.x, brick.right));
            float closestY = Math.max(brick.top, Math.min(b.y, brick.bottom));
            float dx = b.x - closestX;
            float dy = b.y - closestY;
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
        bounceOff(b, bricks[hit]);
    }

    private void bounceOff(Ball b, RectF brick) {
        float closestX = Math.max(brick.left, Math.min(b.x, brick.right));
        float closestY = Math.max(brick.top, Math.min(b.y, brick.bottom));
        float dx = b.x - closestX;
        float dy = b.y - closestY;
        if (dx == 0 && dy == 0) {
            b.vy = -b.vy;
        } else if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) {
                b.vx = -Math.abs(b.vx);
                b.x = brick.left - ballRadius;
            } else {
                b.vx = Math.abs(b.vx);
                b.x = brick.right + ballRadius;
            }
        } else if (dy < 0) {
            b.vy = -Math.abs(b.vy);
            b.y = brick.top - ballRadius;
        } else {
            b.vy = Math.abs(b.vy);
            b.y = brick.bottom + ballRadius;
        }
    }

    /** Empêche les trajectoires quasi horizontales (balle qui ne fait que des allers-retours gauche/droite). */
    private void keepBallMovingVertically(Ball b) {
        float speed = (float) Math.hypot(b.vx, b.vy);
        if (speed == 0f) {
            return;
        }
        float minVy = speed * 0.3f;   // ~17° minimum par rapport à l'horizontale
        if (Math.abs(b.vy) < minVy) {
            float signY = b.vy > 0 ? 1f : -1f;   // si vy == 0, la balle part vers le haut
            float signX = b.vx < 0 ? -1f : 1f;
            b.vy = signY * minVy;
            b.vx = signX * (float) Math.sqrt(speed * speed - minVy * minVy);
        }
    }

    // ---------- Reset ----------

    private void resetGame() {
        score = 0;
        lives = START_LIVES;
        won = false;
        bricksLeft = ROWS * COLS;
        /** Entre 1 et COLS-1 briques incassables, à des colonnes aléatoires : il reste toujours au moins un trou. */
        generateWall();
        for (int i = 0; i < brickAlive.length; i++) {
            brickAlive[i] = true;
        }
        paddleX = width / 2f;
        lastNanos = 0;
        resetBall();
    }

    /** Entre 1 et COLS-1 briques incassables, à des colonnes aléatoires : il reste toujours au moins un trou. */
    private void generateWall() {
        java.util.List<Integer> columns = new ArrayList<>();
        for (int i = 0; i < COLS; i++) {
            columns.add(i);
            wallPresent[i] = false;
        }
        java.util.Collections.shuffle(columns, random);
        int count = 1 + random.nextInt(COLS - 1);
        for (int i = 0; i < count; i++) {
            wallPresent[columns.get(i)] = true;
        }
    }

    /** Une seule balle collée à la raquette ; bonus, malus et objets en chute sont effacés. */
    private void resetBall() {
        ballStuck = true;
        balls.clear();
        Ball b = new Ball();
        b.x = paddleX;
        b.y = paddleTop - ballRadius;
        balls.add(b);
        powerUps.clear();
        fastBallTime = 0;
        slowBallTime = 0;
        slowPaddleTime = 0;
    }

    // ---------- Getters ----------

    public RectF[] getWallBricks() { return wallBricks; }

    public boolean isWallPresent(int index) { return wallPresent[index]; }
    public GameState getGameState() { return gameState; }

    public void setGameState(GameState state) { gameState = state; }

    public RectF[] getBricks() { return bricks; }

    public Rect[] getBrickSources() { return brickSources; }

    public boolean isBrickAlive(int index) { return brickAlive[index]; }

    public Bitmap getGridBitmap() { return gridBitmap; }

    public int getBallCount() { return balls.size(); }

    public float getBallX(int index) { return balls.get(index).x; }

    public float getBallY(int index) { return balls.get(index).y; }

    public float getBallRadius() { return ballRadius; }

    public int getPowerUpCount() { return powerUps.size(); }

    public float getPowerUpX(int index) { return powerUps.get(index).x; }

    public float getPowerUpY(int index) { return powerUps.get(index).y; }

    public PowerUpType getPowerUpType(int index) { return powerUps.get(index).type; }

    public float getPowerUpRadius() { return powerUpRadius; }

    public float getFastBallTime() { return fastBallTime; }

    public float getSlowBallTime() { return slowBallTime; }

    public float getSlowPaddleTime() { return slowPaddleTime; }

    public float getPaddleX() { return paddleX; }

    public float getPaddleWidth() { return paddleW; }

    public float getPaddleHeight() { return paddleH; }

    public float getPaddleTop() { return paddleTop; }

    public boolean isBallStuck() { return ballStuck; }

    public int getScore() { return score; }

    public int getLives() { return lives; }

    public boolean hasWon() { return won; }
}