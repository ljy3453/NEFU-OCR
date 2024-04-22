package com.nefu.ocr;

import static com.nefu.ocr.NetworkUtils.isNetworkConnected;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.nefu.ocr.NetworkUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class BinarizationActivity extends AppCompatActivity {

    private ImageView binarizedImageView;
    private LinearLayout innerLayout;
    private Bitmap binarizedBitmap;
    private Button copy;
    private Button save;
    private static final int REQUEST_PERMISSION_CODE = 123;
    private EditText editText;
    private boolean isOcrTaskRunning = false;
    private OcrTask currentOcrTask;
    private String txt;
    private String output;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_binarization);

        binarizedImageView = findViewById(R.id.binarizedImageView);
        innerLayout = findViewById(R.id.deal);
        copy=findViewById(R.id.copy);
        save=findViewById(R.id.save);

        // Initialize OpenCV if not already initialized
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }

        // 读取用户的设置并根据需要开启或关闭GPU加速功能
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableOptimization = prefs.getBoolean("enable_optimization", false);
        if (enableOptimization) {
            Core.setUseOptimized(true);
            Log.d("OPENCV","open");
        } else {
            Core.setUseOptimized(false);
            Log.d("OPENCV","close");
        }

        Toolbar toolbar = findViewById(R.id.toolbar2);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // 执行返回操作
            }
        });
        String imageUriString=getIntent().getStringExtra("imagePath");
        Bitmap bitmap = BitmapFactory.decodeFile(imageUriString);
        binarizedBitmap = binarizeImage(bitmap);
        binarizedImageView.setImageBitmap(binarizedBitmap);
        Button ocr=(Button) findViewById(R.id.ocr);
        editText=(EditText) findViewById(R.id.text);
        //复制语言数据到内部存储进行OCR
        ocr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences prefs1 = PreferenceManager.getDefaultSharedPreferences(BinarizationActivity.this);
                boolean enableOptimization = prefs1.getBoolean("enable_aliyun", false);
                if (enableOptimization) {
                    if(isNetworkConnected(BinarizationActivity.this)){
                        ProgressBar progressBar = findViewById(R.id.progressBar);
                        progressBar.setVisibility(View.VISIBLE);
                        // 在按钮点击事件中启动线程执行网络操作
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                // 在子线程中执行网络操作
                                txt=ocrOnline(bitmapToBase64(binarizedBitmap));
                                // 使用 Gson 解析 JSON
                                Gson gson = new Gson();
                                JsonObject jsonObject = gson.fromJson(txt, JsonObject.class);
                                StringBuilder out = new StringBuilder();
                                // 检查是否包含 "ret" 字段，并且是一个数组
                                if (jsonObject.has("ret") && jsonObject.get("ret").isJsonArray()) {
                                    JsonArray retArray = jsonObject.getAsJsonArray("ret");

                                    // 遍历 "ret" 数组中的每个元素
                                    for (JsonElement element : retArray) {
                                        // 检查是否是一个对象
                                        if (element.isJsonObject()) {
                                            JsonObject retObject = element.getAsJsonObject();
                                            // 检查是否包含 "word" 字段
                                            if (retObject.has("word")) {
                                                String word = retObject.get("word").getAsString();
                                                // 提取 "word" 字段的值
                                                out.append(word);
                                                output=out.toString();
                                            }
                                        }
                                    }
                                }
                                if (output != null) {
                                    // 在主线程中更新 UI 控件
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            progressBar.setVisibility(View.GONE);
                                            editText.setText(output);
                                            innerLayout.setVisibility(View.VISIBLE);
                                        }
                                    });
                                }
                            }
                        }).start();
                        Log.d("OCR","云端");
                    }else {
                        Toast.makeText(BinarizationActivity.this, "请检查当前网络状况后重试或关闭高精度识别", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    new OcrTask().execute();
                    // 检查当前是否有任务在执行
                    if (isOcrTaskRunning) {
                        // 如果有任务在执行，则取消当前任务
                        cancelOcrTask();
                    }
                    // 创建并执行新的任务
                    currentOcrTask = new OcrTask();
                    currentOcrTask.execute();
                    Log.d("OCR","本地");
                }
            }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String textToCopy = editText.getText().toString();
                copyToClipboard(textToCopy);
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStoragePermissionGranted()) {
                    showFileNameDialog();
                } else {
                    // 如果没有权限，则请求权限
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (!Environment.isExternalStorageManager()) {
                            Intent intent = new Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + BinarizationActivity.this.getPackageName()));
                            BinarizationActivity.this.startActivity(intent);
                            Toast.makeText(BinarizationActivity.this, "请授予应用所有文件访问权限", Toast.LENGTH_SHORT).show();
                        }else {
                            showFileNameDialog();
                        }
                    }else{
                        ActivityCompat.requestPermissions(BinarizationActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_CODE);
                    }
                }
            }
        });
    }
    private class OcrTask extends AsyncTask<Void, Integer, String> {

        private ProgressBar progressBar;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            isOcrTaskRunning = true;
            // 在任务开始前显示进度条
            progressBar = findViewById(R.id.progressBar);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {
            // 在后台线程中执行文件复制和文本识别操作
            String dataPath = getFilesDir() + "/tessdata/";
            File tessdataDir = new File(dataPath);
            if (!tessdataDir.exists()) {
                tessdataDir.mkdirs(); // 如果不存在tessdata文件夹则创建
            }

            String languageDataPath = dataPath + "chi_sim.traineddata"; // 替换 "eng" 为你需要的语言
            File languageDataFile = new File(languageDataPath);
            if (!languageDataFile.exists()) {
                try {
                    InputStream inputStream = getAssets().open("chi_sim.traineddata"); // 替换 "eng" 为你需要的语言
                    OutputStream outputStream = new FileOutputStream(languageDataFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    outputStream.flush();
                    outputStream.close();
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            String filePath = getFilesDir() + "/";
            TessBaseAPI tessBaseAPI = new TessBaseAPI();
            tessBaseAPI.init(filePath, "chi_sim");
            tessBaseAPI.setImage(binarizedBitmap);
            String recognizedText = tessBaseAPI.getUTF8Text();

            return recognizedText;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            // 更新进度条
            progressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(String recognizedText) {
            super.onPostExecute(recognizedText);
            // 在任务完成后隐藏进度条，并显示识别结果
            progressBar.setVisibility(View.GONE);
            editText.setText(recognizedText);
            innerLayout.setVisibility(View.VISIBLE);
        }
    }

    // 取消任务方法
    private void cancelOcrTask() {
        if (currentOcrTask != null && currentOcrTask.getStatus() == AsyncTask.Status.RUNNING) {
            currentOcrTask.cancel(true);
        }
    }
    //二值化
    private Bitmap binarizeImage(Bitmap inputBitmap) {
        // 固定宽度，等比缩小输入图像的尺寸，双线性插值
        Bitmap resizedBitmap = resizeBitmap(inputBitmap, 1200); // 传递所需的目标宽度
        //将缩放后的 Bitmap 转换为 OpenCV 的 Mat 对象
        Mat src = new Mat();
        Utils.bitmapToMat(resizedBitmap, src);

        // 将彩色图像转换为灰度图像
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        // 图像去噪处理,高斯模糊，正奇数尺寸
        Mat dstMat = new Mat();
        Imgproc.GaussianBlur(gray, dstMat, new Size(1, 1), 0);

        // 对去噪图像进行二值化处理， Otsu 自动阈值算法，自动确定最佳的二值化阈值。
        Mat binary = new Mat();
        Imgproc.threshold(dstMat, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // Convert the binary image back to bitmap
        Bitmap outputBitmap = Bitmap.createBitmap(binary.cols(), binary.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(binary, outputBitmap); 

        return outputBitmap;
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int targetSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float aspectRatio = (float) width / height;
        int targetWidth, targetHeight;
        if (width > height) {
            targetWidth = targetSize;
            targetHeight = Math.round(targetSize / aspectRatio);
        } else {
            targetHeight = targetSize;
            targetWidth = Math.round(targetSize * aspectRatio);
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }
    //复制文本内容到剪切板
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(BinarizationActivity.this, "已复制到剪切板", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(BinarizationActivity.this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }
    // 显示文件名对话框
    private void showFileNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入文件名");

        // Inflate the layout for the dialog
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View dialogView = inflater.inflate(R.layout.dialog_file_name, null);
        builder.setView(dialogView);

        final EditText fileNameEditText = dialogView.findViewById(R.id.file_name_edit_text);

        builder.setPositiveButton("保存", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String fileName = fileNameEditText.getText().toString();
                String content = editText.getText().toString();

                if (!fileName.isEmpty()) {
                    saveToFile(content, fileName);
                }
            }
        });

        builder.setNegativeButton("取消", null);

        builder.show();
    }

    // 保存内容到文件
    private void saveToFile(String content, String fileName) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, fileName + ".txt");
        if(file.exists()){
            showToast("文件名重复。");
        }else{
            try {
                FileWriter writer = new FileWriter(file);
                writer.append(content);
                writer.flush();
                writer.close();
                // Show a message indicating successful save
                showToast("已存储在Download目录下。");
            } catch (IOException e) {
                e.printStackTrace();
                // Show a message indicating save failure
                showToast("保存失败！");
            }
        }
    }

    // 检查是否已授予存储权限
    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // 对于Android版本低于M的设备，默认权限已授予
            return true;
        }
    }

    // 显示Toast消息
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户授予了权限，显示文件名对话框
                showFileNameDialog();
            } else {
                // 用户拒绝了权限请求，显示一个消息或执行其他操作
                showToast("您拒绝了权限");
            }
        }
    }
    //将Bitmap对象转化为base64编码供云端识别
    public static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
    //阿里云云端识别
    public static String ocrOnline(String strPictureBase64) {
        String strRet = "";
        String host = "https://tysbgpu.market.alicloudapi.com";
        String path = "/api/predict/ocr_general";
        String method = "POST";
        String appcode = "70b65a9a6b6b48de87b206dd84128cef";

        String bodys = "{\"image\":\"" + strPictureBase64 + "\",\"configure\":{\"output_prob\":false,\"output_keypoints\":false,\"skip_detection\":false,\"without_predicting_direction\":false}}";
        //Log.i("OCR", "bodys:" + bodys);

        String strURL = host + path; //请求地址
        Log.i("OCR", "strURL:" + strURL);

        try {
            // 创建URL对象
            URL url = new URL(strURL);

            // 创建HttpURLConnection对象
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // 设置请求方法为POST
            conn.setRequestMethod(method);

            // 设置请求属性
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "APPCODE " + appcode);

            // 发送请求
            OutputStream os = conn.getOutputStream();
            os.write(bodys.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            // 处理服务器响应
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                strRet += line;
            }
            in.close();
            Log.i("OCR", "ret :" + strRet);

            JSONObject jsonObject = JSON.parseObject(strRet);
            if(jsonObject.getBooleanValue("success"))
            {
                JSONArray jsonArray = jsonObject.getJSONArray("ret");
                String str = jsonArray.getJSONObject(0).getString("word");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return strRet;
    }
}