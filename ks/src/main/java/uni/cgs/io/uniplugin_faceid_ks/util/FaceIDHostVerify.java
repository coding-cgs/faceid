package uni.cgs.io.uniplugin_faceid_ks.util;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class FaceIDHostVerify implements HostnameVerifier {
    @Override
    public boolean verify(String hostname, SSLSession session) {
        return true;
    }
}
