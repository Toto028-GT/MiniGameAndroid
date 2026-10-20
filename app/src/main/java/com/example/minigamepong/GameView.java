package com.example.minigamepong;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;


public class GameView extends View implements SensorEventListener {

    enum GameState { START, PLAYING, GAME_OVER }
    enum ControlMode { JOYSTICK, TILT }

    private GameState gameState = GameState.START;
    private ControlMode controlMode = ControlMode.JOYSTICK;

    // ---------------- Paramètres ----------------
    private static final int ROWS = 7;
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

    // ---------------- Joystick flottant ----------------
    private boolean joyActive = false;
    private boolean joyMoved = false;
    private float joyBaseX, joyBaseY;      // où le doigt a touché l'écran
    private float joyKnobX, joyKnobY;      // position du petit rond
    private float joyRadius;               // rayon max du joystick
    private float joyInput = 0f;           // -1 (gauche) .. +1 (droite)
    private float paddleMaxSpeed;          // px / tick à fond de joystick
    private final Paint joyBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joyKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---------------- Inclinaison de la tablette ----------------
    private static final float TILT_SENSITIVITY = 4f;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private boolean sensorRegistered;
    private float tiltX;
    private float tiltInput;

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

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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
        registerTiltSensor();
        post(gameLoop);
    }

    public void stopGame() {
        removeCallbacks(gameLoop);
        unregisterTiltSensor();
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
        ballSpeed = w * 0.01f;
        textPaint.setTextSize(w * 0.055f);
        joyRadius = w * 0.08f;
        paddleMaxSpeed = w * 0.015f;

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

        float half = paddleW / 2f;
        // Pendant l'attente du lancement, la passerelle reste immobile.
        if (ballStuck) {
            ballX = paddleX;
            ballY = paddleTop - ballRadius;
            return;
        }

        float horizontalInput = controlMode == ControlMode.TILT ? tiltInput : joyInput;
        paddleX += horizontalInput * paddleMaxSpeed;   // si vous avez le delta-temps : * dt
        paddleX = Math.max(half, Math.min(getWidth() - half, paddleX));

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
        int hit = -1;
        float bestDist = Float.MAX_VALUE;

        // 1. Trouver la brique la PLUS PROCHE parmi celles qui touchent la balle
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

        // 2. Détruire la brique
        brickAlive[hit] = false;
        bricksLeft--;
        score += 10;

        // 3. Choisir l'axe selon la direction de l'écart le plus grand
        RectF b = bricks[hit];
        float cx = Math.max(b.left, Math.min(ballX, b.right));
        float cy = Math.max(b.top, Math.min(ballY, b.bottom));
        float dx = ballX - cx;
        float dy = ballY - cy;

        if (dx == 0 && dy == 0) {
            // Centre dans la brique : on se base sur la vitesse
            ballVY = -ballVY;
        } else if (Math.abs(dx) > Math.abs(dy)) {
            // Choc latéral
            if (dx < 0) { ballVX = -Math.abs(ballVX); ballX = b.left - ballRadius; }
            else        { ballVX =  Math.abs(ballVX); ballX = b.right + ballRadius; }
        } else {
            // Choc vertical
            if (dy < 0) { ballVY = -Math.abs(ballVY); ballY = b.top - ballRadius; }
            else        { ballVY =  Math.abs(ballVY); ballY = b.bottom + ballRadius; }
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
        if (controlMode == ControlMode.JOYSTICK) {
            drawCentered(canvas, "Glissez le doigt pour", getHeight() * 0.48f);
            drawCentered(canvas, "déplacer la passerelle", getHeight() * 0.53f);
        } else {
            drawCentered(canvas, "Inclinez la tablette pour", getHeight() * 0.48f);
            drawCentered(canvas, "déplacer la passerelle", getHeight() * 0.53f);
        }
        drawCentered(canvas, "Choisir le contrôle", getHeight() * 0.64f);
        drawCentered(canvas, controlMode == ControlMode.JOYSTICK
                ? "[ JOYSTICK ]     INCLINAISON"
                : "JOYSTICK     [ INCLINAISON ]", getHeight() * 0.72f);
        drawCentered(canvas, "Toucher ici pour jouer", getHeight() * 0.84f);
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

        if (joyActive) {
            canvas.drawCircle(joyBaseX, joyBaseY, joyRadius, joyBasePaint);
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.4f, joyKnobPaint);
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
        int action = event.getActionMasked();

        switch (gameState) {
            case START:
            case GAME_OVER:
                if (action == MotionEvent.ACTION_DOWN) {
                    if (gameState == GameState.START && event.getY() >= getHeight() * 0.67f
                            && event.getY() <= getHeight() * 0.78f) {
                        controlMode = event.getX() < getWidth() / 2f
                                ? ControlMode.JOYSTICK
                                : ControlMode.TILT;
                        if (controlMode == ControlMode.TILT) {
                            registerTiltSensor();
                        } else {
                            unregisterTiltSensor();
                        }
                        invalidate();
                        break;
                    }
                    resetGame();
                    resetJoystick();
                    gameState = GameState.PLAYING;
                }
                break;

            case PLAYING:
                if (controlMode == ControlMode.TILT) {
                    if (action == MotionEvent.ACTION_UP && ballStuck) {
                        launchBall();
                    }
                    return true;
                }
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

                        // Le rond reste dans le cercle du joystick
                        float dist = (float) Math.hypot(dx, dy);
                        if (dist > joyRadius) {
                            dx *= joyRadius / dist;
                            dy *= joyRadius / dist;
                        }
                        joyKnobX = joyBaseX + dx;
                        joyKnobY = joyBaseY + dy;

                        // Seul l'axe horizontal commande la passerelle
                        joyInput = dx / joyRadius;
                        if (Math.abs(joyInput) < 0.01f) joyInput = 0f;   // zone morte
                        else joyMoved = true;
                        break;

                    case MotionEvent.ACTION_UP:
                        // Un simple tap (sans bouger) lance la balle
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

    private void registerTiltSensor() {
        if (controlMode != ControlMode.TILT || sensorRegistered
                || sensorManager == null || accelerometer == null) {
            return;
        }
        sensorRegistered = sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (!sensorRegistered) {
            controlMode = ControlMode.JOYSTICK;
        }
    }

    private void unregisterTiltSensor() {
        if (!sensorRegistered || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        sensorRegistered = false;
        tiltX = 0f;
        tiltInput = 0f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER
                || controlMode != ControlMode.TILT) {
            return;
        }

        // L'axe horizontal depend de la rotation actuelle de l'ecran.
        float screenHorizontalAcceleration = getScreenHorizontalAcceleration(event.values);

        // Filtre passe-bas : les petits tremblements de la tablette sont attenues.
        tiltX = tiltX * 0.85f + screenHorizontalAcceleration * 0.15f;
        float input = -tiltX / TILT_SENSITIVITY;
        if (Math.abs(input) < 0.08f) {
            input = 0f;
        }
        tiltInput = Math.max(-1f, Math.min(1f, input));
    }

    private float getScreenHorizontalAcceleration(float[] acceleration) {
        int rotation = getDisplay() == null
                ? Surface.ROTATION_0
                : getDisplay().getRotation();

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
        // La precision n'a pas besoin d'etre utilisee pour controler la passerelle.
    }
}