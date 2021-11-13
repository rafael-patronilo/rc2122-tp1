package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;

/**
 * Implements a basic HTTP1.0 client
 * @author smduarte
 *
 */


public class HttpClient10 extends HttpClient1x {
    private static final String GET_FORMAT_STR = "GET %s HTTP/1.0\r\n%s\r\n\r\n";

    public HttpClient10(String url) {
        super(url);
    }

    @Override
    public void close() {
        //Nothing to do on close
    }

    @Override
    protected byte[] makeRequest(String request, String expectedStatus) {
        try (Socket cs = new Socket(this.host, this.port)){
            return makeRequest(cs, request, expectedStatus);
        }
        catch (Exception x){
            x.printStackTrace();
        }
        return null;
    }

    @Override
    protected String requestFormat(String path, String additionalFields) {
        return String.format(GET_FORMAT_STR, path, additionalFields);
    }
}
