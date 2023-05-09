package uni.cgs.io.uniplugin_faceid_ks.util;


import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class FaceIDSocketFactory {

    public static SSLSocketFactory getSslSocketFactory(){
        SSLSocketFactory sslSocketFactory = null;
        try{
            SSLContext context = SSLContext.getInstance("TLS");

            TrustManager[] tm = new TrustManager[]{
                    new TrustAllManager()
            };
            context.init(null,tm,new SecureRandom());
            sslSocketFactory = context.getSocketFactory();
        }catch (Exception e) {
            e.printStackTrace();
        }
        return sslSocketFactory;
    }
}
