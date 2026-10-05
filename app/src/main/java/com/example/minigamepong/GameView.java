package com.example.minigamepong;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;


public class GameView extends View {

    enum GameState { START, PLAYING, GAME_OVER }

    private GameState gameState = GameState.START;

    // ---------------- Paramètres ----------------
    private static final int ROWS = 5;
    private static final int COLS = 7;
    private static final int START_LIVES = 3;

    // ---------------- Etat du jeu ----------------
    private float ballX, ballY, ballRadius;
    private float ballVX, ballVY;
    private float ballSpeed;            // norme de la vitesse (px / tick)
    private boolean ballStuck = true;   // balle posée sur la passerelle

    private float paddleX;              // centre de la passerelle
    private float paddleW, paddleH, paddleTop;

    private final RectF[] bricks = new RectF[ROWS * COLS];
    private final boolean[] brickAlive = new boolean[ROWS * COLS];
    private int bricksLeft;

    private int score;
    private int lives;
    private boolean won;

    // ---------------- Peintures (créées une seule fois) ----------------
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    public GameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(20, 24, 38));
        ballPaint.setColor(Color.WHITE);
        paddlePaint.setColor(Color.rgb(0, 200, 220));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
    }

    // ==================== Boucle de jeu ====================
    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            update();
            invalidate();
            postDelayed(this, 16);
        }
    };

    public void startGame() {
        removeCallbacks(gameLoop);
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopGame();
    }

    // ==================== Dimensions ====================
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        paddleW = w * 0.22f;
        paddleH = h * 0.025f;
        paddleTop = h - h * 0.10f - paddleH;
        ballRadius = w * 0.022f;
        ballSpeed = w * 0.012f;
        textPaint.setTextSize(w * 0.055f);

        // Grille de briques adaptée à la taille de l'écran
        float margin = w * 0.04f;
        float gap = w * 0.008f;
        float top = h * 0.12f;
        float brickW = (w - 2 * margin - (COLS - 1) * gap) / COLS;
        float brickH = h * 0.035f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = margin + c * (brickW + gap);
                float t = top + r * (brickH + gap);
                bricks[r * COLS + c] = new RectF(left, t, left + brickW, t + brickH);
            }
        }
        resetGame();
    }

    // ==================== Réinitialisation ====================
    private void resetGame() {
        score = 0;
        lives = START_LIVES;
        won = false;
        bricksLeft = ROWS * COLS;
        for (int i = 0; i < brickAlive.length; i++) brickAlive[i] = true;
        paddleX = getWidth() / 2f;
        resetBall();
    }

    private void resetBall() {
        ballStuck = true;
        ballX = paddleX;
        ballY = paddleTop - ballRadius;
        ballVX = 0;
        ballVY = 0;
    }

    private void launchBall() {
        ballStuck = false;
        float dir = Math.random() < 0.5 ? -1 : 1;
        ballVX = dir * ballSpeed * 0.5f;
        ballVY = -ballSpeed * 0.87f;
    }

    // ==================== Mise à jour ====================
    private void update() {
        if (gameState != GameState.PLAYING) return;

        // Balle posée : elle suit la passerelle
        if (ballStuck) {
            ballX = paddleX;
            ballY = paddleTop - ballRadius;
            return;
        }

        ballX += ballVX;
        ballY += ballVY;

        // Murs gauche / droite / haut
        if (ballX - ballRadius < 0) { ballX = ballRadius; ballVX = Math.abs(ballVX); }
        if (ballX + ballRadius > getWidth()) { ballX = getWidth() - ballRadius; ballVX = -Math.abs(ballVX); }
        if (ballY - ballRadius < 0) { ballY = ballRadius; ballVY = Math.abs(ballVY); }

        // Sortie par le bas : une vie en moins
        if (ballY - ballRadius > getHeight()) {
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
        boolean touchesTop = ballY + ballRadius >= paddleTop && ballY + ballRadius <= paddleTop + paddleH + ballSpeed;

        if (ballVY > 0 && overPaddle && touchesTop) {
            // L'angle de renvoi dépend de l'endroit touché sur la passerelle
            float offset = (ballX - paddleX) / (paddleW / 2f);
            offset = Math.max(-1f, Math.min(1f, offset));
            double angle = Math.toRadians(offset * 60);
            ballVX = (float) (ballSpeed * Math.sin(angle));
            ballVY = (float) (-ballSpeed * Math.cos(angle));
            ballY = paddleTop - ballRadius;
        }
    }

    private void collideBricks() {
        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) continue;
            RectF b = bricks[i];

            // Point du rectangle le plus proche du centre de la balle
            float cx = Math.max(b.left, Math.min(ballX, b.right));
            float cy = Math.max(b.top, Math.min(ballY, b.bottom));
            float dx = ballX - cx;
            float dy = ballY - cy;

            if (dx * dx + dy * dy <= ballRadius * ballRadius) {
                brickAlive[i] = false;
                bricksLeft--;
                score += 10;

                // Rebond horizontal si on touche un côté, vertical sinon
                boolean side = ballX < b.left || ballX > b.right;
                if (side) {
                    ballVX = (ballX < b.left) ? -Math.abs(ballVX) : Math.abs(ballVX);
                } else {
                    ballVY = (ballY < b.top) ? -Math.abs(ballVY) : Math.abs(ballVY);
                }
                return; // une seule brique par tick
            }
        }
    }

    // ==================== Dessin ====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (gameState) {
            case START:     drawStartScreen(canvas); break;
            case PLAYING:   drawGame(canvas); break;
            case GAME_OVER: drawGameOverScreen(canvas); break;
        }
    }

    private void drawCentered(Canvas canvas, String text, float y) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, getWidth() / 2f, y, textPaint);
    }

    private void drawStartScreen(Canvas canvas) {
        drawCentered(canvas, "CASSE-BRIQUES", getHeight() * 0.35f);
        drawCentered(canvas, "Glissez le doigt pour", getHeight() * 0.48f);
        drawCentered(canvas, "déplacer la passerelle", getHeight() * 0.53f);
        drawCentered(canvas, "Toucher pour jouer", getHeight() * 0.65f);
    }

    private void drawGame(Canvas canvas) {
        // Briques
        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) continue;
            brickPaint.setColor(Color.rgb(30, 136, 229));
            canvas.drawRoundRect(bricks[i], 8, 8, brickPaint);
        }

        // Passerelle
        tmpRect.set(paddleX - paddleW / 2f, paddleTop, paddleX + paddleW / 2f, paddleTop + paddleH);
        canvas.drawRoundRect(tmpRect, paddleH / 2f, paddleH / 2f, paddlePaint);

        // Balle
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);

        // HUD
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Score : " + score, getWidth() * 0.04f, getHeight() * 0.07f, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Vies : " + lives, getWidth() * 0.96f, getHeight() * 0.07f, textPaint);

        if (ballStuck) {
            drawCentered(canvas, "Toucher pour lancer", getHeight() * 0.65f);
        }
    }

    private void drawGameOverScreen(Canvas canvas) {
        drawCentered(canvas, won ? "BRAVO !" : "GAME OVER", getHeight() * 0.38f);
        drawCentered(canvas, "Score : " + score, getHeight() * 0.48f);
        drawCentered(canvas, "Toucher pour rejouer", getHeight() * 0.62f);
    }

    // ==================== Interaction tactile ====================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();

        switch (gameState) {
            case START:
                if (action == MotionEvent.ACTION_DOWN) {
                    resetGame();
                    gameState = GameState.PLAYING;
                }
                break;

            case PLAYING:
                // La passerelle suit le doigt horizontalement
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    float half = paddleW / 2f;
                    paddleX = Math.max(half, Math.min(getWidth() - half, event.getX()));
                }
                if (action == MotionEvent.ACTION_DOWN && ballStuck) {
                    launchBall();
                }
                break;

            case GAME_OVER:
                if (action == MotionEvent.ACTION_DOWN) {
                    resetGame();
                    gameState = GameState.PLAYING;
                }
                break;
        }
        return true;
    }
}