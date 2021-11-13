package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

/**
 * Basic functionality common to both HttpClient10 and HttpClient11
 */
public abstract class HttpClient1x implements HttpClient{
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String HTTP_200_OK = "200";
    private static final String HTTP_206_OK = "206";
    private static final String RANGE_FORMAT_STR = "Range: bytes=%d-%s";

    protected String host;
    protected int port;

    protected HttpClient1x(String url){
        try {
            URL u = new URL(url);
            this.host = u.getHost();
            this.port = getPortOrDefault(u);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    protected static int getPortOrDefault(URL url){
        int urlPort = url.getPort();
        return urlPort > 0 ? urlPort : HTTP_DEFAULT_PORT;
    }

    protected void checkUrl(URL url) throws Exception {
        if((!url.getHost().equalsIgnoreCase(this.host)) || (getPortOrDefault(url) != this.port)){
            throw new Exception("Invalid URL for this client");
        }
    }

    /**
     * Makes the request to the given socket and parses the contents
     * @param cs the socket to send the request and read the reply
     * @param request the request to send
     * @param expectedStatus the expected success status code
     * @return the content of the reply or null in case of wrong status code
     * @throws IOException in case there is an IO error
     */
    protected byte[] makeRequest(Socket cs, String request, String expectedStatus) throws IOException {
        System.out.println();
        System.out.println("REQUEST");
        System.out.println(request);
        cs.getOutputStream().write(request.getBytes());
        System.out.println("REPLY");
        InputStream in = cs.getInputStream();

        String statusLine = Http.readLine(in);
        String[] statusParts = Http.parseHttpReply(statusLine);

        if (statusParts[1].equals(expectedStatus)) {
            System.out.println(statusLine);
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
        else System.err.println(statusLine);
        return null;
    }

    /**
     * Should get the socket and call the super makeRequest
     * @param request the request to make
     * @param expectedStatus the expected status message
     * @return the contents of the reply or null if there's an error
     */
    protected abstract byte[] makeRequest(String request, String expectedStatus);

    /**
     * Should format the request given the parameters
     * @param path the path to the page
     * @param additionalFields any additionalFields
     * @return the request to make
     */
    protected abstract String requestFormat(String path, String additionalFields);

    @Override
    public byte[] doGet(String url) {
        try {
            URL u = new URL(url);
            checkUrl(u);
            String header = requestFormat(u.getPath(), USER_AGENT);
            return makeRequest(header, HTTP_200_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }


    @Override
    public byte[] doGetRange(String url, long start) {
        try {
            URL u = new URL(url);
            checkUrl(u);
            String rangeString = String.format(RANGE_FORMAT_STR, start, "");
            String header = requestFormat(u.getPath(), String.format("%s\r\n%s", USER_AGENT, rangeString));
            return makeRequest(header, HTTP_206_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }


    @Override
    public byte[] doGetRange(String url, long start, long end) {
        try {
            URL u = new URL(url);
            checkUrl(u);
            String rangeString = String.format(RANGE_FORMAT_STR, start, end);
            String header = requestFormat(u.getPath(), String.format("%s\r\n%s", USER_AGENT, rangeString));
            return makeRequest(header, HTTP_206_OK);
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }
}
