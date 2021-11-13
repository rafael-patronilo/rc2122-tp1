package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

/**
 * Implements a basic HTTP1.1 client
 * @author João Padrão 58288 e Rafael Patronilo 57473
 *
 */


public class HttpClient11 extends HttpClient1x {
    private static final String GET_FORMAT_STR = "GET %s HTTP/1.1\r\nHost: %s:%s\r\n%s\r\n\r\n";
    private Socket socket = null;

    public HttpClient11(String url) {
        super(url);
    }

    /**
     * Gets or opens the socket
     * @return the Socket
     * @throws IOException if opening the socket results in an error
     */
    private Socket getSocket() throws IOException {
        if(socket != null && !socket.isClosed()){
            return socket;
        }
        System.out.println("Connecting...\n");
        socket = new Socket(this.host, this.port);
        return socket;
    }



    @Override
    public void close() {
        if(socket!=null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected byte[] makeRequest(String request, String expectedStatus) {
        try{
            return makeRequest(getSocket(), request, expectedStatus);
        }
        catch (Exception x){
            x.printStackTrace();
        }
        return null;
    }

    @Override
    protected String requestFormat(String path, String additionalFields) {
        return String.format(GET_FORMAT_STR, path, this.host, this.port, additionalFields);
    }
}
