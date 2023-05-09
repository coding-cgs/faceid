package uni.cgs.io.uniplugin_faceid_ks.core;

import static android.os.Build.VERSION_CODES.M;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.megvii.meglive_sdk.listener.DetectCallback;
import com.megvii.meglive_sdk.listener.PreCallback;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.dcloud.common.core.permission.PermissionControler;
import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import uni.cgs.io.uniplugin_faceid_ks.util.FaceIDHostVerify;
import uni.cgs.io.uniplugin_faceid_ks.util.FaceIDSocketFactory;
import uni.cgs.io.uniplugin_faceid_ks.util.GenerateSign;
import uni.cgs.io.uniplugin_faceid_ks.util.TrustAllManager;

public class FaceIDModule extends UniModule implements PreCallback, DetectCallback {

    private final static int REQUEST_CODE = 10086;
    private static String[] permissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private OkHttpClient okHttpClient;

    @Override
    public void onActivityCreate() {
        super.onActivityCreate();
        init();
    }

    private void init() {
        okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(FaceIDSocketFactory.getSslSocketFactory(), new TrustAllManager())
                .hostnameVerifier(new FaceIDHostVerify())
                .build();
    }

    @Override
    public void onActivityDestroy() {
        super.onActivityDestroy();
        okHttpClient = null;
    }

    @UniJSMethod(uiThread = true)
    public void requestPermissions() {
        if (mUniSDKInstance.getContext() != null) {
            Activity activity = (Activity) mUniSDKInstance.getContext();
            PermissionControler.requestPermissions(activity, permissions, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            boolean cameraAccess = false;
            boolean writeAccess = false;
            for (int i = 0; i < permissions.length; i++) {
                String preName = permissions[i];
                int granted = grantResults[i];
                if (Manifest.permission.CAMERA.equals(preName) && granted == PackageManager.PERMISSION_GRANTED) {
                    //获取权限结果
                    cameraAccess = true;
                }
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(preName) && granted == PackageManager.PERMISSION_GRANTED) {
                    //获取权限结果
                    writeAccess = true;
                }
            }
            Map<String, Object> params = new HashMap<>();
            if (cameraAccess && writeAccess) {
                params.put("code", 1);
            } else {
                params.put("code", -1);
                params.put("note", "人脸识别需要文件读写权限和照相机权限");
            }
            mUniSDKInstance.fireGlobalEventCallback("permissionEvent", params);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    //run JS thread
    @UniJSMethod(uiThread = false)
    public JSONObject getSign(JSONObject options) {
        JSONObject result = new JSONObject();
        if (options == null || options.size() == 0) {
            result.put("code", -1);
            result.put("note", "获取sign参数不能为空");
            return result;
        }
        String apiKey = options.getString("apiKey");
        String apiSecret = options.getString("apiSecret");
        long currTime = System.currentTimeMillis();
        long expireTime = currTime + 60 * 60 * 1000;

        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(apiSecret)) {
            result.put("code", -1);
            result.put("note", "apiKey、apiSecret不能为空");
            return result;
        }

        String sign = GenerateSign.appSign(apiKey, apiSecret, currTime / 1000, expireTime / 1000);
        result.put("code", 1);
        result.put("sign", sign);
        return result;
    }

    @UniJSMethod(uiThread = false)
    public void getBizToken(JSONObject options, final UniJSCallback callback) {
        final JSONObject result = new JSONObject();
        String bizTokenUrl = options.getString("bizTokenUrl");
        String sign = options.getString("sign");
        String livenessType = options.getString("livenessType");
        int comparisonType = options.getIntValue("comparisonType");
        String idcardName = options.getString("idcardName");
        String idcardNum = options.getString("idcardNum");
        byte[] image_ref1 = options.getBytes("image_ref1");
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        builder.addFormDataPart("sign", sign);
        builder.addFormDataPart("sign_version", "hmac_sha1");
        builder.addFormDataPart("liveness_type", livenessType);
        builder.addFormDataPart("comparison_type", "" + comparisonType);
        if (comparisonType == 1) {
            builder.addFormDataPart("idcard_name", idcardName);
            builder.addFormDataPart("idcard_number", idcardNum);
        } else if (comparisonType == 0) {
            builder.addFormDataPart("uuid", UUID.randomUUID().toString());
            RequestBody fileBody = RequestBody.create(image_ref1, MediaType.parse("image/*"));
            builder.addFormDataPart("image_ref1", "image_ref1", fileBody);
        }
        Request request = new Request.Builder()
                .url(bizTokenUrl)
                .post(builder.build())
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                result.put("code", -1);
                result.put("note", "获取bizToken异常：" + e.getMessage());
                callback.invoke(result);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String respStr = response.body().string();
                    JSONObject resp = JSONObject.parseObject(respStr);
                    if (resp.containsKey("error")) {
                        String errorStr = resp.getString("error");
                        result.put("code", -1);
                        result.put("note", "获取bizToken失败：" + errorStr);
                    } else {
                        result.put("code", 1);
                        result.put("data", resp);
                    }
                } else {
                    result.put("code", -1);
                    result.put("note", "获取bizToken失败，状态码：" + response.code());
                }
                callback.invoke(result);
            }
        });
    }

    @Override
    public void onDetectFinish(String s, int i, String s1, String s2) {

    }

    @Override
    public void onPreStart() {

    }

    @Override
    public void onPreFinish(String s, int i, String s1) {

    }
}
