package com.example.minigamepong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View {

    enum GameState { MENU, PREVIEW, PLAYING, GAME_OVER }

    /** L'activité implémente ceci pour ouvrir l'appareil photo. */
    public interface PhotoRequestListener {
        void onPhotoRequested();
    }

    private PhotoRequestListener photoListener;
    private GameState gameState = GameState.MENU;

    // ---------------- Paramètres ----------------
    private static final int ROWS = 7;
    private static final int COLS = 7;
    private static final int START_LIVES = 3;

    // ---------------- Etat du jeu ----------------
    private float ballX, ballY, ballRadius;
    private float ballVX, ballVY;
    private float ballSpeed;
    private boolean ballStuck = true;

    private float paddleX;
    private float paddleW, paddleH, paddleTop;

    private final RectF[] bricks = new RectF[ROWS * COLS];
    private final Rect[] brickSrc = new Rect[ROWS * COLS];   // morceau d'image de chaque brique
    private final boolean[] brickAlive = new boolean[ROWS * COLS];
    private int bricksLeft;

    private int score;
    private int lives;
    private boolean won;

    // ---------------- Photo ----------------
    private Bitmap photo;        // photo d'origine
    private Bitmap gridBitmap;   // photo recadrée + mise à la taille de la grille de briques

    // ---------------- Boutons (menu / confirmation) ----------------
    private final RectF playBtn = new RectF();
    private final RectF retakeBtn = new RectF();
    private final RectF useBtn = new RectF();
    private final RectF replayBtn = new RectF();
    private final RectF menuBtn = new RectF();

    // ---------------- Peintures ----------------
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF tmpRect = new RectF();

    // ---------------- Joystick flottant ----------------
    private boolean joyActive = false;
    private boolean joyMoved = false;
    private float joyBaseX, joyBaseY;
    private float joyKnobX, joyKnobY;
    private float joyRadius;
    private float joyInput = 0f;
    private float paddleMaxSpeed;
    private final Paint joyBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joyKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GameView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(20, 24, 38));
        ballPaint.setColor(Color.WHITE);
        paddlePaint.setColor(Color.rgb(0, 200, 220));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        joyBasePaint.setColor(Color.WHITE);
        joyBasePaint.setAlpha(30);
        joyKnobPaint.setColor(Color.WHITE);
        joyKnobPaint.setAlpha(90);
    }

    public void setPhotoRequestListener(PhotoRequestListener l) {
        photoListener = l;
    }

    /** Ratio largeur/hauteur de la grille de briques (pour cadrer l'appareil photo). */
    public float getGridAspect() {
        if (bricks[0] == null) return 1f;
        float gw = bricks[COLS - 1].right - bricks[0].left;
        float gh = bricks[ROWS * COLS - 1].bottom - bricks[0].top;
        return gw / gh;
    }

    /** Appelé par l'activité quand une photo vient d'être prise. */
    public void setPhoto(Bitmap bmp) {
        photo = bmp;
        buildTiles();
        gameState = GameState.PREVIEW;   // on demande confirmation
        invalidate();
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
        joyRadius = w * 0.08f;
        paddleMaxSpeed = w * 0.035f;

        // Boutons
        float bw = w * 0.5f, bh = h * 0.07f;
        playBtn.set(w / 2f - bw / 2, h / 2f - bh / 2, w / 2f + bw / 2, h / 2f + bh / 2);
        retakeBtn.set(w / 2f - bw / 2, h * 0.70f, w / 2f + bw / 2, h * 0.70f + bh);
        useBtn.set(w / 2f - bw / 2, h * 0.80f, w / 2f + bw / 2, h * 0.80f + bh);
        replayBtn.set(w / 2f - bw / 2, h * 0.55f, w / 2f + bw / 2, h * 0.55f + bh);
        menuBtn.set(w / 2f - bw / 2, h * 0.66f, w / 2f + bw / 2, h * 0.66f + bh);

        // Grille de briques (un peu plus hautes pour que l'image reste lisible)
        float margin = w * 0.04f;
        float gap = w * 0.008f;
        float top = h * 0.12f;
        float brickW = (w - 2 * margin - (COLS - 1) * gap) / COLS;
        float brickH = h * 0.045f;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float left = margin + c * (brickW + gap);
                float t = top + r * (brickH + gap);
                bricks[r * COLS + c] = new RectF(left, t, left + brickW, t + brickH);
            }
        }
        buildTiles();
        resetGame();
    }

    /** Recadre la photo au format de la grille et calcule le morceau de chaque brique. */
    private void buildTiles() {
        if (photo == null || bricks[0] == null) return;

        float gl = bricks[0].left, gt = bricks[0].top;
        float gr = bricks[COLS - 1].right, gb = bricks[ROWS * COLS - 1].bottom;
        int gw = Math.round(gr - gl), gh = Math.round(gb - gt);
        if (gw <= 0 || gh <= 0) return;

        // Recadrage centré au même ratio que la grille
        float targetRatio = (float) gw / gh;
        int pw = photo.getWidth(), ph = photo.getHeight();
        int cw, ch;
        if ((float) pw / ph > targetRatio) {
            ch = ph;
            cw = Math.round(ph * targetRatio);
        } else {
            cw = pw;
            ch = Math.round(pw / targetRatio);
        }
        Bitmap cropped = Bitmap.createBitmap(photo, (pw - cw) / 2, (ph - ch) / 2, cw, ch);
        gridBitmap = Bitmap.createScaledBitmap(cropped, gw, gh, true);

        // Chaque brique = la zone de la grille qu'elle occupe
        for (int i = 0; i < bricks.length; i++) {
            RectF b = bricks[i];
            brickSrc[i] = new Rect(
                    Math.max(0, Math.round(b.left - gl)),
                    Math.max(0, Math.round(b.top - gt)),
                    Math.min(gw, Math.round(b.right - gl)),
                    Math.min(gh, Math.round(b.bottom - gt)));
        }
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

    private void startPlaying() {
        resetGame();
        resetJoystick();
        gameState = GameState.PLAYING;
    }

    private void requestPhoto() {
        if (photoListener != null) photoListener.onPhotoRequested();
    }

    // ==================== Mise à jour ====================
    private void update() {
        if (gameState != GameState.PLAYING) return;

        float half = paddleW / 2f;
        paddleX += joyInput * paddleMaxSpeed;
        paddleX = Math.max(half, Math.min(getWidth() - half, paddleX));

        if (ballStuck) {
            ballX = paddleX;
            ballY = paddleTop - ballRadius;
            return;
        }

        ballX += ballVX;
        ballY += ballVY;

        if (ballX - ballRadius < 0) { ballX = ballRadius; ballVX = Math.abs(ballVX); }
        if (ballX + ballRadius > getWidth()) { ballX = getWidth() - ballRadius; ballVX = -Math.abs(ballVX); }
        if (ballY - ballRadius < 0) { ballY = ballRadius; ballVY = Math.abs(ballVY); }

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
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) continue;
            RectF b = bricks[i];
            float cx = Math.max(b.left, Math.min(ballX, b.right));
            float cy = Math.max(b.top, Math.min(ballY, b.bottom));
            float dx = ballX - cx;
            float dy = ballY - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 <= ballRadius * ballRadius && d2 < bestDist) {
                bestDist = d2;
                hit = i;
            }
        }
        if (hit < 0) return;

        brickAlive[hit] = false;
        bricksLeft--;
        score += 10;

        RectF b = bricks[hit];
        float cx = Math.max(b.left, Math.min(ballX, b.right));
        float cy = Math.max(b.top, Math.min(ballY, b.bottom));
        float dx = ballX - cx;
        float dy = ballY - cy;

        if (dx == 0 && dy == 0) {
            ballVY = -ballVY;
        } else if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) { ballVX = -Math.abs(ballVX); ballX = b.left - ballRadius; }
            else        { ballVX =  Math.abs(ballVX); ballX = b.right + ballRadius; }
        } else {
            if (dy < 0) { ballVY = -Math.abs(ballVY); ballY = b.top - ballRadius; }
            else        { ballVY =  Math.abs(ballVY); ballY = b.bottom + ballRadius; }
        }
    }

    // ==================== Dessin ====================
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (gameState) {
            case MENU:      drawMenu(canvas); break;
            case PREVIEW:   drawPreview(canvas); break;
            case PLAYING:   drawGame(canvas); break;
            case GAME_OVER: drawGameOverScreen(canvas); break;
        }
    }

    private void drawCentered(Canvas canvas, String text, float y) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, getWidth() / 2f, y, textPaint);
    }

    private void drawButton(Canvas canvas, RectF r, String label, int color) {
        btnPaint.setColor(color);
        canvas.drawRoundRect(r, r.height() / 4f, r.height() / 4f, btnPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(label, r.centerX(), y, textPaint);
    }

    private void drawMenu(Canvas canvas) {
        drawCentered(canvas, "CASSE-BRIQUES", getHeight() * 0.30f);
        drawButton(canvas, playBtn, "Play", Color.rgb(0, 160, 180));
    }

    private void drawPreview(Canvas canvas) {
        drawCentered(canvas, "Utiliser cette photo ?", getHeight() * 0.08f);
        if (gridBitmap != null) {
            float w = getWidth() * 0.9f;
            float h = w * gridBitmap.getHeight() / gridBitmap.getWidth();
            float left = (getWidth() - w) / 2f;
            float top = getHeight() * 0.12f;
            tmpRect.set(left, top, left + w, top + h);
            canvas.drawBitmap(gridBitmap, null, tmpRect, bitmapPaint);
        }
        drawButton(canvas, retakeBtn, "Reprendre", Color.rgb(90, 90, 100));
        drawButton(canvas, useBtn, "Utiliser", Color.rgb(0, 160, 180));
    }

    private void drawGame(Canvas canvas) {
        for (int i = 0; i < bricks.length; i++) {
            if (!brickAlive[i]) continue;
            if (gridBitmap != null && brickSrc[i] != null) {
                canvas.drawBitmap(gridBitmap, brickSrc[i], bricks[i], bitmapPaint);
            } else {
                brickPaint.setColor(Color.rgb(30, 136, 229));
                canvas.drawRoundRect(bricks[i], 8, 8, brickPaint);
            }
        }

        tmpRect.set(paddleX - paddleW / 2f, paddleTop, paddleX + paddleW / 2f, paddleTop + paddleH);
        canvas.drawRoundRect(tmpRect, paddleH / 2f, paddleH / 2f, paddlePaint);

        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Score : " + score, getWidth() * 0.04f, getHeight() * 0.07f, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Vies : " + lives, getWidth() * 0.96f, getHeight() * 0.07f, textPaint);

        if (ballStuck) {
            drawCentered(canvas, "Toucher pour lancer", getHeight() * 0.65f);
        }

        if (joyActive) {
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, joyBasePaint);
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.4f, joyKnobPaint);
        }
    }

    private void drawGameOverScreen(Canvas canvas) {
        drawCentered(canvas, won ? "BRAVO !" : "GAME OVER", getHeight() * 0.38f);
        drawCentered(canvas, "Score : " + score, getHeight() * 0.48f);
        drawButton(canvas, replayBtn, "Replay", Color.rgb(0, 160, 180));
        drawButton(canvas, menuBtn, "Menu", Color.rgb(90, 90, 100));
    }

    // ==================== Interaction tactile ====================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (gameState) {
            case MENU:
                if (action == MotionEvent.ACTION_UP && playBtn.contains(event.getX(), event.getY())) {
                    requestPhoto();
                }
                break;

            case PREVIEW:
                if (action == MotionEvent.ACTION_UP) {
                    if (retakeBtn.contains(event.getX(), event.getY())) {
                        requestPhoto();
                    } else if (useBtn.contains(event.getX(), event.getY())) {
                        startPlaying();
                    }
                }
                break;

            case GAME_OVER:
                if (action == MotionEvent.ACTION_UP) {
                    if (replayBtn.contains(event.getX(), event.getY())) {
                        startPlaying();                    // même photo
                    } else if (menuBtn.contains(event.getX(), event.getY())) {
                        gameState = GameState.MENU;        // Play -> nouvelle photo
                    }
                }
                break;

            case PLAYING:
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        joyActive = true;
                        joyMoved = false;
                        joyBaseX = joyKnobX = event.getX();
                        joyBaseY = joyKnobY = event.getY();
                        joyInput = 0f;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!joyActive) break;
                        float dx = event.getX() - joyBaseX;
                        float dy = event.getY() - joyBaseY;
                        float dist = (float) Math.hypot(dx, dy);
                        if (dist > joyRadius) {
                            dx *= joyRadius / dist;
                            dy *= joyRadius / dist;
                        }
                        joyKnobX = joyBaseX + dx;
                        joyKnobY = joyBaseY + dy;
                        joyInput = dx / joyRadius;
                        if (Math.abs(joyInput) < 0.01f) joyInput = 0f;
                        else joyMoved = true;
                        break;

                    case MotionEvent.ACTION_UP:
                        if (ballStuck && !joyMoved) launchBall();
                        resetJoystick();
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        resetJoystick();
                        break;
                }
                break;
        }
        return true;
    }

    private void resetJoystick() {
        joyActive = false;
        joyMoved = false;
        joyInput = 0f;
    }
}