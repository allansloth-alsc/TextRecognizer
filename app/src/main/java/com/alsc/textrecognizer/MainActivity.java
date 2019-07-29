package com.alsc.textrecognizer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_TAKE_PHOTO = 1;

    String mCurrentPhotoPath;
    ImageView imageView;
    Button takePicture;
    Button analyzePicture;
    TextView recognizedText;
    Bitmap mSelectedImage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.image_view);
        recognizedText = findViewById(R.id.recognizedText);
        takePicture = findViewById(R.id.takePictureButton);
        takePicture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
        analyzePicture = findViewById(R.id.analyzePictureButton);
        analyzePicture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runTextRecognition();
            }
        });
        boolean hasPermission = isStoragePermissionGranted();
        if(hasPermission){
            takePicture.setEnabled(true);
            analyzePicture.setEnabled(true);
        }
        else{
            takePicture.setEnabled(false);
            analyzePicture.setEnabled(false);
        }
    }
    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            return true;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            takePicture.setEnabled(true);
            analyzePicture.setEnabled(true);
        }
        else{
            takePicture.setEnabled(false);
            analyzePicture.setEnabled(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            galleryAddPic();
            try {
                Bitmap bitmap = getBitmap();
                bitmap = modifyOrientation(bitmap, mCurrentPhotoPath);
                setPic(bitmap);
                recognizedText.setText("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void runTextRecognition() {

        if(mSelectedImage == null)
            return;
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(mSelectedImage);


        FirebaseVisionTextRecognizer recognizer = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        takePicture.setEnabled(false);
        recognizer.processImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText texts) {
                                takePicture.setEnabled(true);
                                AsyncProcessor processor = new AsyncProcessor();
                                processor.execute(texts);
                                //recognizedText.setText(processTextRecognitionResult(texts));
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                takePicture.setEnabled(true);
                                recognizedText.setText("Could not recognize any text");
                                e.printStackTrace();
                            }
                        });
    }

    public static String processTextRecognitionResult(FirebaseVisionText texts) {
        List<FirebaseVisionText.TextBlock> blocks = texts.getTextBlocks();
        if (blocks.size() == 0) {
            return  "No text found";
        }
        String allText = "";

        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                allText += "\n";
                for (int k = 0; k < elements.size(); k++) {
                    allText +=  elements.get(k).getText() + "\t";

                }
            }
        }
        return allText;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
      //  String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
      //  String imageFileName = "TEXT_RECOGNIZER_IMAGE" + timeStamp + "_";
       String imageFileName = "TEXT_RECOGNIZER_IMAGE";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ; //external file but still private. Use getExternalStoragePublicDirectory() to create a public file and the then galleryAddPic will work
//        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES+"/TextRecognizer") ; //when saved in the public picture folder, then create a folder with the app name as well (Don't have to)
//        if(storageDir.exists() == false)
 //           storageDir.mkdir(); //Create the TextRecognizer folder in the picture folder if it does not exist
        boolean doDelete = true; // if it is getExternalStoragePublicDirectory then we delete the picture else set it to false, because the picture will be availeble in the gallery and the user can delete themself.
        if (storageDir != null && doDelete) {

            // so we can list all files
            File[] filenames = storageDir.listFiles();

            // loop through each file and delete
            for (File tmpf : filenames) {
                if(tmpf.isFile()){
                    String extension = tmpf.getAbsolutePath().substring(tmpf.getAbsolutePath().lastIndexOf("."));
                    if(tmpf.getName().contains("TEXT_RECOGNIZER_IMAGE") && extension.equals(".jpg")){
                        tmpf.delete();
                    }

                }
            }
        }

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                String error = ex.getMessage();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private void galleryAddPic() {
        //external file but still private. Use getExternalStoragePublicDirectory() to create a public file and the then galleryAddPic will work
        File file = new File(mCurrentPhotoPath);

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
    public static Bitmap modifyOrientation(Bitmap bitmap, String image_absolute_path) throws IOException {
        ExifInterface ei = new ExifInterface(image_absolute_path);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotate(bitmap, 90);

            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotate(bitmap, 180);

            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotate(bitmap, 270);

            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return flip(bitmap, true, false);

            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return flip(bitmap, false, true);

            default:
                return bitmap;
        }
    }

    public static Bitmap rotate(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap flip(Bitmap bitmap, boolean horizontal, boolean vertical) {
        Matrix matrix = new Matrix();
        matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    private Bitmap getBitmap(){
        // Get the dimensions of the View
        int targetW = imageView.getWidth();
        int targetH = imageView.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        return  bitmap;
    }
    private void setPic(Bitmap bitmap ) {

        imageView.setImageBitmap(bitmap);
        mSelectedImage = bitmap;
    }
    private class AsyncProcessor extends AsyncTask<FirebaseVisionText, String, String> {

        TextView asyncRecognizedText;
        Button asyncTakePicture;
        Button asyncAnalyzePicture;

        @Override
        protected String doInBackground(FirebaseVisionText... params) {
            publishProgress("Analyzing..."); // Calls onProgressUpdate()
            FirebaseVisionText text = params[0];
            String result = MainActivity.processTextRecognitionResult(text);
            return  result;
        }


        @Override
        protected void onPostExecute(String result) {
            asyncRecognizedText.setText(result);
            asyncTakePicture.setEnabled(true);
            asyncAnalyzePicture.setEnabled(true);
        }


        @Override
        protected void onPreExecute() {
            asyncRecognizedText = findViewById(R.id.recognizedText);
            asyncTakePicture= findViewById(R.id.takePictureButton);
            asyncAnalyzePicture  = findViewById(R.id.analyzePictureButton);
            asyncTakePicture.setEnabled(false);
            asyncAnalyzePicture.setEnabled(false);
        }


        @Override
        protected void onProgressUpdate(String... text) {
            asyncRecognizedText.setText(text[0]);

        }


    }
}
