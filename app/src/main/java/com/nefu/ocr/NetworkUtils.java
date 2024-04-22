package com.nefu.ocr;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtils {

    public static boolean isNetworkConnected(Context context) {
        // 获取ConnectivityManager实例
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // 获取当前活动的网络信息
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        // 检查网络连接状态
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
