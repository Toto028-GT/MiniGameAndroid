package com.example.minigamepong;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;

/**
 * Appareil photo cadré au format exact de la grille de briques :
 * ce qu'on voit dans le cadre est exactement ce qui sera dans le jeu.
 */
public class CameraActivity extends AppCompatActivity {

    public static final String EXTRA_RATIO = "ratio";   // largeur / hauteur de la grille

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private File photoFile;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoFile = new File(getCacheDir(), "photo.jpg");
        float ratio = getIntent().getFloatExtra(EXTRA_RATIO, 1f);

        // Taille du cadre = format de la grille
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = (int) (dm.widthPixels * 0.92f);
        int h = (int) (w / ratio);
        int maxH = (int) (dm.heightPixels * 0.62f);
        if (h > maxH) { h = maxH; w = (int) (h * ratio); }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(20, 24, 38));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(w, h, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        pp.topMargin = (int) (dm.heightPixels * 0.08f);
        root.addView(previewView, pp);

        // Boutons : même style que ceux dessinés dans GameView
        int btnW = (int) (dm.widthPixels * 0.5f);
        int btnH = (int) (dm.heightPixels * 0.07f);

        TextView shoot = makeButton("Prendre la photo", Color.rgb(0, 160, 180), btnW, btnH, dm);
        shoot.setOnClickListener(v -> takePhoto());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(btnW, btnH,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        sp.bottomMargin = (int) (dm.heightPixels * 0.18f);
        root.addView(shoot, sp);

        TextView cancel = makeButton("Annuler", Color.rgb(90, 90, 100), btnW, btnH, dm);
        cancel.setOnClickListener(v -> finish());   // RESULT_CANCELED par défaut
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(btnW, btnH,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        cp.bottomMargin = (int) (dm.heightPixels * 0.08f);
        root.addView(cancel, cp);

        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Bouton dessiné comme ceux de GameView (TextView = pas de marges ni de style imposés par le thème). */
    private TextView makeButton(String text, int color, int w, int h, DisplayMetrics dm) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(1);
        b.setIncludeFontPadding(false);
        b.setPadding((int) (w * 0.05f), 0, (int) (w * 0.05f), 0);
        // Le texte rétrécit tout seul s'il ne rentre pas dans le bouton
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                b, (int) (dm.widthPixels * 0.03f), (int) (dm.widthPixels * 0.05f), 1, TypedValue.COMPLEX_UNIT_PX);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(h / 4f);
        b.setBackground(bg);
        b.setClickable(true);
        return b;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                // Le ViewPort fait coïncider la zone visible à l'écran et la photo enregistrée
                previewView.post(() -> {
                    UseCaseGroup.Builder group = new UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(imageCapture);
                    ViewPort viewPort = previewView.getViewPort();
                    if (viewPort != null) group.setViewPort(viewPort);

                    provider.unbindAll();
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group.build());
                });
            } catch (Exception e) {
                Toast.makeText(this, "Caméra indisponible", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults results) {
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onError(ImageCaptureException e) {
                        Toast.makeText(CameraActivity.this, "Erreur de capture", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}