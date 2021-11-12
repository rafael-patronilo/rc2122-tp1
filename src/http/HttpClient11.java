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


public class HttpClient11 implements HttpClient {
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String HTTP_200_OK = "200";
    private static final String HTTP_206_OK = "206";
	private static final String GET_FORMAT_STR = "GET %s HTTP/1.1\r\nHost: %s:%s\r\n%s\r\n\r\n";
    private static final String RANGE_FORMAT_STR = "Range: bytes=%d-%s";

    private Socket socket = null;

    /**
     * Gets or opens the socket
     * @param host the host of the url
     * @param port the port of the url
     * @return the Socket
     * @throws IOException if opening the socket results in an error
     */
    private Socket getSocket(String host, int port) throws IOException {
        if(socket != null && !socket.isClosed()){
            return socket;
        }
        System.out.println("Connecting...\n");
        socket = new Socket(host, port);
        return socket;
    }

    /**
     * Send a given HTTP Request and returns the contents
     * @param url the url to send the request to
     * @param request the request to send
     * @param expectedStatus the expected status code on the reply
     * @return the contents of the reply or null if there's an error
     */
    private byte[] makeRequest(URL url, String request, String expectedStatus){
        System.out.println(request);
        int port = url.getPort();
        try {
            Socket cs = getSocket(url.getHost(), port > 0 ? port : HTTP_DEFAULT_PORT);
            cs.getOutputStream().write(request.getBytes());

            InputStream in = cs.getInputStream();

            String statusLine = Http.readLine(in);
            String[] statusParts = Http.parseHttpReply(statusLine);
            System.out.println(statusLine);

            if (statusParts[1].equals(expectedStatus)) {
                String headerLine;
                int contentLength = -1;
                while ((headerLine = Http.readLine(in)).length() > 0) {
                    System.out.println(headerLine);
                    String[] headerParts = Http.parseHttpHeader(headerLine);
                    if (headerParts[0].equalsIgnoreCase(CONTENT_LENGTH))
                        contentLength = Integer.valueOf(headerParts[1]);
                }

                return in.readNBytes(contentLength);
            }
            else System.out.println(statusLine);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }
	
	@Override
	public byte[] doGet(String url) {
        try {
            URL u = new URL(url);
            String header = String.format(GET_FORMAT_STR, u.getPath(), u.getHost(), u.getPort(), USER_AGENT);
            return makeRequest(u, header, HTTP_200_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
	}


    @Override
    public byte[] doGetRange(String url, long start) {
        try {
            URL u = new URL(url);
            String rangeString = String.format(RANGE_FORMAT_STR, start, "");
            String header = String.format(GET_FORMAT_STR, u.getPath(), u.getHost(), u.getPort(), rangeString);
            return makeRequest(u, header, HTTP_206_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }
    

    @Override
    public byte[] doGetRange(String url, long start, long end) {
        try {
            URL u = new URL(url);
            String rangeString = String.format(RANGE_FORMAT_STR, start, end);
            String header = String.format(GET_FORMAT_STR, u.getPath(), u.getHost(), u.getPort(), rangeString);
            return makeRequest(u, header, HTTP_206_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
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
}
