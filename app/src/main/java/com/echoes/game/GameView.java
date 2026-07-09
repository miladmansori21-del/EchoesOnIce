package com.echoes.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable {

    private Thread gameThread;
    private boolean playing = false;
    private boolean paused = false;
    private SurfaceHolder holder;
    private Canvas canvas;
    private Paint paint;
    private Random random = new Random();

    private int screenX, screenY;

    private Paddle paddleTop, paddleBottom;
    private Ball ball;
    private List<Block> blocks;
    private List<Particle> particles;
    private List<Snowflake> snowflakes;

    private int score = 0;
    private int lives = 5;
    private int combo = 0;
    private int nextLifeScore = 100;
    private int roundsCleared = 0;
    private boolean gameOver = false;
    private boolean gameStarted = false;

    private float touchX = -1;

    private static final int MAX_LIVES = 5;
    private static final int MAX_BLOCKS = 6;
    private static final float PADDLE_WIDTH_RATIO = 0.22f;
    private static final float PADDLE_HEIGHT_RATIO = 0.018f;
    private static final float BALL_RADIUS_RATIO = 0.018f;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        paint = new Paint();
        blocks = new ArrayList<>();
        particles = new ArrayList<>();
        snowflakes = new ArrayList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenX = w;
        screenY = h;
        initGame();
    }

    private void initGame() {
        float paddleW = screenX * PADDLE_WIDTH_RATIO;
        float paddleH = screenY * PADDLE_HEIGHT_RATIO;
        float margin = screenY * 0.06f;

        paddleBottom = new Paddle(screenX / 2f - paddleW / 2f, screenY - margin - paddleH, paddleW, paddleH, Color.parseColor("#9fd3f0"));
        paddleTop = new Paddle(screenX / 2f - paddleW / 2f, margin, paddleW, paddleH, Color.parseColor("#c9b8ff"));

        ball = new Ball(screenX / 2f, screenY / 2f, screenX * BALL_RADIUS_RATIO);

        blocks.clear();
        particles.clear();
        snowflakes.clear();

        int snowCount = (screenX * screenY) / 9000;
        for (int i = 0; i < snowCount; i++) {
            snowflakes.add(new Snowflake());
        }

        spawnBlocks();
    }

    private void resetBall() {
        ball.x = screenX / 2f;
        ball.y = screenY / 2f;
        float speed = screenY * 0.0045f * (1 + roundsCleared * 0.03f);
        ball.vx = (random.nextFloat() - 0.5f) * speed * 2f;
        ball.vy = random.nextBoolean() ? speed : -speed;
        combo = 0;
    }

    private void spawnBlocks() {
        blocks.clear();
        float areaTop = screenY * 0.28f;
        float areaBottom = screenY * 0.72f;
        for (int i = 0; i < 4; i++) {
            float bw = screenX * (0.05f + random.nextFloat() * 0.06f);
            float bh = screenY * (0.018f + random.nextFloat() * 0.018f);
            float x = random.nextFloat() * (screenX - bw);
            float y = areaTop + random.nextFloat() * (areaBottom - areaTop - bh);
            int hp = 1 + random.nextInt(3);
            blocks.add(new Block(x, y, bw, bh, hp));
        }
    }

    @Override
    public void run() {
        while (playing) {
            long startFrameTime = System.currentTimeMillis();
            if (!paused) {
                update();
            }
            draw();
            long timeThisFrame = System.currentTimeMillis() - startFrameTime;
            if (timeThisFrame < 16) {
                try {
                    Thread.sleep(16 - timeThisFrame);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void update() {
        if (!gameStarted) return;
        if (gameOver) return;

        if (touchX >= 0) {
            float target = touchX - paddleBottom.width / 2f;
            paddleBottom.x = Math.max(0, Math.min(screenX - paddleBottom.width, target));
            paddleTop.x = paddleBottom.x;
        }

        ball.x += ball.vx;
        ball.y += ball.vy;

        if (ball.x - ball.radius < 0) {
            ball.x = ball.radius;
            ball.vx = Math.abs(ball.vx);
        }
        if (ball.x + ball.radius > screenX) {
            ball.x = screenX - ball.radius;
            ball.vx = -Math.abs(ball.vx);
        }

        if (ball.vy > 0 && RectF.intersects(ball.getBounds(), paddleBottom.getBounds())) {
            ball.y = paddleBottom.y - ball.radius - 1;
            ball.vy = -Math.abs(ball.vy);
            float hit = (ball.x - paddleBottom.x) / paddleBottom.width;
            ball.vx = (hit - 0.5f) * Math.abs(ball.vy) * 3f;
            combo++;
            spawnParticles(ball.x, paddleBottom.y, paddleBottom.color, 10);
        }

        if (ball.vy < 0 && RectF.intersects(ball.getBounds(), paddleTop.getBounds())) {
            ball.y = paddleTop.y + paddleTop.height + ball.radius + 1;
            ball.vy = Math.abs(ball.vy);
            float hit = (ball.x - paddleTop.x) / paddleTop.width;
            ball.vx = (hit - 0.5f) * Math.abs(ball.vy) * 3f;
            combo++;
            spawnParticles(ball.x, paddleTop.y + paddleTop.height, paddleTop.color, 10);
        }

        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (RectF.intersects(ball.getBounds(), b.getBounds())) {
                b.hp--;
                float cx = b.x + b.w / 2f;
                float cy = b.y + b.h / 2f;
                if (Math.abs(ball.x - cx) / b.w > Math.abs(ball.y - cy) / b.h) {
                    ball.vx = -ball.vx;
                } else {
                    ball.vy = -ball.vy;
                }
                if (b.hp <= 0) {
                    it.remove();
                    score += 10;
                    spawnParticles(cx, cy, b.color, 14);
                    checkLifeBonus();
                } else {
                    spawnParticles(cx, cy, b.color, 6);
                }
                break;
            }
        }

        if (blocks.size() < MAX_BLOCKS && random.nextInt(120) == 0) {
            float areaTop = screenY * 0.28f;
            float areaBottom = screenY * 0.72f;
            float bw = screenX * (0.05f + random.nextFloat() * 0.06f);
            float bh = screenY * (0.018f + random.nextFloat() * 0.018f);
            float x = random.nextFloat() * (screenX - bw);
            float y = areaTop + random.nextFloat() * (areaBottom - areaTop - bh);
            int hp = 1 + random.nextInt(3);
            blocks.add(new Block(x, y, bw, bh, hp));
        }

        if (ball.y - ball.radius > screenY || ball.y + ball.radius < 0) {
            lives--;
            combo = 0;
            spawnParticles(ball.x, ball.y > screenY / 2f ? screenY : 0, Color.parseColor("#ff5577"), 16);
            if (lives <= 0) {
                gameOver = true;
            } else {
                resetBall();
            }
        }

        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update();
            if (p.life <= 0) pit.remove();
        }

        for (Snowflake s : snowflakes) {
            s.update();
        }
    }

    private void checkLifeBonus() {
        while (score >= nextLifeScore) {
            lives++;
            nextLifeScore += 100;
            spawnParticles(screenX / 2f, screenY / 2f, Color.parseColor("#e8c873"), 20);
        }
    }

    private void spawnParticles(float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(x, y, color));
        }
    }

    private void draw() {
        if (!holder.getSurface().isValid()) return;
        canvas = holder.lockCanvas();
        if (canvas == null) return;

        drawBackground();
        drawSnow();
        for (Block b : blocks) {
            b.draw(canvas, paint);
        }
        paddleTop.draw(canvas, paint);
        paddleBottom.draw(canvas, paint);
        ball.draw(canvas, paint);
        for (Particle p : particles) {
            p.draw(canvas, paint);
        }
        drawHUD();

        if (!gameStarted) {
            drawCenterText("ECHOES ON ICE", "Tap to start");
        } else if (gameOver) {
            drawCenterText("GAME OVER", "Score: " + score + "  Tap to restart");
        } else if (paused) {
            drawCenterText("PAUSED", "Tap to resume");
        }

        holder.unlockCanvasAndPost(canvas);
    }

    private void drawBackground() {
        LinearGradient gradient = new LinearGradient(0, 0, 0, screenY,
                new int[]{Color.parseColor("#0a0e1f"), Color.parseColor("#05060f"), Color.parseColor("#0a0a18")},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, screenX, screenY, paint);
        paint.setShader(null);

        paint.setColor(Color.parseColor("#1a2540"));
        paint.setStrokeWidth(1);
        for (int x = 0; x < screenX; x += 40) {
            canvas.drawLine(x, 0, x, screenY, paint);
        }
        for (int y = 0; y < screenY; y += 40) {
            canvas.drawLine(0, y, screenX, y, paint);
        }

        RadialGradient glow = new RadialGradient(screenX / 2f, screenY / 2f, screenX * 0.4f,
                Color.parseColor("#1a2342"), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint.setShader(glow);
        canvas.drawRect(0, 0, screenX, screenY, paint);
        paint.setShader(null);
    }

    private void drawSnow() {
        paint.setColor(Color.parseColor("#dff3ff"));
        for (Snowflake s : snowflakes) {
            paint.setAlpha((int) (s.alpha * 255));
            canvas.drawCircle(s.x, s.y, s.r, paint);
        }
        paint.setAlpha(255);
    }

    private void drawHUD() {
        paint.setColor(Color.parseColor("#e8c873"));
        paint.setTextSize(screenY * 0.035f);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("SCORE: " + score, 30, 60, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("LIVES: " + lives, screenX - 30, 60, paint);

        if (combo > 1) {
            paint.setColor(Color.parseColor("#9fd3f0"));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(screenY * 0.04f);
            canvas.drawText("x" + combo, screenX / 2f, 60, paint);
        }
    }

    private void drawCenterText(String main, String sub) {
        paint.setColor(Color.parseColor("#e8c873"));
        paint.setTextSize(screenY * 0.07f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setShadowLayer(15, 0, 0, Color.parseColor("#8a6f3a"));
        canvas.drawText(main, screenX / 2f, screenY / 2f - 40, paint);
        paint.setShadowLayer(0, 0, 0, 0);

        paint.setColor(Color.parseColor("#9fd3f0"));
        paint.setTextSize(screenY * 0.035f);
        canvas.drawText(sub, screenX / 2f, screenY / 2f + 40, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                touchX = event.getX();
                if (!gameStarted) {
                    gameStarted = true;
                    resetBall();
                } else if (gameOver) {
                    restartGame();
                } else if (paused) {
                    paused = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                touchX = -1;
                break;
        }
        return true;
    }

    private void restartGame() {
        score = 0;
        lives = MAX_LIVES;
        combo = 0;
        nextLifeScore = 100;
        roundsCleared = 0;
        gameOver = false;
        gameStarted = true;
        initGame();
        resetBall();
    }

    public void pause() {
        playing = false;
        paused = true;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void resume() {
        playing = true;
        paused = false;
        gameThread = new Thread(this);
        gameThread.start();
    }

    private class Paddle {
        float x, y, width, height;
        int color;

        Paddle(float x, float y, float width, float height, int color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
        }

        RectF getBounds() {
            return new RectF(x, y, x + width, y + height);
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setShadowLayer(15, 0, 0, color);
            canvas.drawRoundRect(x, y, x + width, y + height, height / 2f, height / 2f, paint);
            paint.setShadowLayer(0, 0, 0, 0);

            paint.setColor(Color.WHITE);
            paint.setAlpha(80);
            canvas.drawRoundRect(x + 3, y + 2, x + width - 3, y + height * 0.4f, height / 3f, height / 3f, paint);
            paint.setAlpha(255);
        }
    }

    private class Ball {
        float x, y, radius;
        float vx, vy;

        Ball(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        RectF getBounds() {
            return new RectF(x - radius, y - radius, x + radius, y + radius);
        }

        void draw(Canvas canvas, Paint paint) {
            RadialGradient gradient = new RadialGradient(x - radius * 0.3f, y - radius * 0.3f, radius,
                    Color.WHITE, Color.parseColor("#9fd3f0"), Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            paint.setShadowLayer(20, 0, 0, Color.parseColor("#9fd3f0"));
            canvas.drawCircle(x, y, radius, paint);
            paint.setShadowLayer(0, 0, 0, 0);
            paint.setShader(null);
        }
    }

    private class Block {
        float x, y, w, h;
        int hp, maxHp;
        int color;

        Block(float x, float y, float w, float h, int hp) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.hp = hp;
            this.maxHp = hp;
            this.color = Color.HSVToColor(new float[]{random.nextInt(360), 0.7f, 0.9f});
        }

        RectF getBounds() {
            return new RectF(x, y, x + w, y + h);
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setShadowLayer(10, 0, 0, color);
            canvas.drawRoundRect(x, y, x + w, y + h, h / 3f, h / 3f, paint);
            paint.setShadowLayer(0, 0, 0, 0);

            paint.setColor(Color.WHITE);
            paint.setAlpha(60);
            canvas.drawRoundRect(x + 2, y + 2, x + w - 2, y + h * 0.35f, h / 4f, h / 4f, paint);
            paint.setAlpha(255);

            paint.setColor(Color.BLACK);
            paint.setTextSize(Math.max(20, h * 0.7f));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf(hp), x + w / 2f, y + h * 0.7f, paint);
        }
    }

    private class Particle {
        float x, y, vx, vy, life, maxLife;
        int color;
        float size;

        Particle(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 2 + random.nextFloat() * 4;
            this.vx = (float) Math.cos(angle) * speed;
            this.vy = (float) Math.sin(angle) * speed;
            this.life = 1f;
            this.maxLife = 1f;
            this.size = 3 + random.nextFloat() * 4;
        }

        void update() {
            x += vx;
            y += vy;
            vy += 0.15f;
            life -= 0.025f;
            size *= 0.98f;
        }

        void draw(Canvas canvas, Paint paint) {
            paint.setColor(color);
            paint.setAlpha((int) (life * 255));
            canvas.drawCircle(x, y, size, paint);
            paint.setAlpha(255);
        }
    }

    private class Snowflake {
        float x, y, r, vy, alpha, drift;

        Snowflake() {
            reset();
            y = random.nextFloat() * screenY;
        }

        void reset() {
            x = random.nextFloat() * screenX;
            y = -5;
            r = 1 + random.nextFloat() * 2;
            vy = 0.5f + random.nextFloat() * 1.5f;
            alpha = 0.2f + random.nextFloat() * 0.5f;
            drift = random.nextFloat() * (float) Math.PI * 2f;
        }

        void update() {
            y += vy;
            x += (float) Math.sin((y + drift * 30) * 0.01f) * 0.5f;
            if (y > screenY + 5) reset();
        }
    }
}
