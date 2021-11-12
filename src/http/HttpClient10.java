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

//TODO HTTP 1.1 (OPCIONAL OBRIGATORIO)

public class HttpClient10 implements HttpClient {

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final Object HTTP_200_OK = "200";
    private static final Object HTTP_206_OK = "206";

	private static final String HTTP_SUCCESS = "20";
	private static final String GET_FORMAT_STR = "GET %s HTTP/1.0\r\n%s\r\n\r\n";
    private static final String RANGE_FORMAT_STR = "Range: bytes=%d-%s";

	static private byte[] getContents(InputStream in) throws IOException {

		String reply = Http.readLine(in);
		System.out.println(reply);//TODO estava comentado

		if (!reply.contains(HTTP_SUCCESS)) {
			throw new RuntimeException(String.format("HTTP request failed: [%s]", reply));
		}
		while ((reply = Http.readLine(in)).length() > 0) {
			System.out.println(reply);//TODO estava comentado
		}
		return in.readAllBytes();
	}
	
	@Override
	public byte[] doGet(String urlStr) {
		try {
			URL url = new URL(urlStr);
			int port = url.getPort();
			try (Socket cs = new Socket(url.getHost(), port < 0 ? url.getDefaultPort(): port)) {
				String request = String.format(GET_FORMAT_STR, url.getFile(), USER_AGENT);
				System.out.println(request);
				cs.getOutputStream().write(request.getBytes());
				return getContents(cs.getInputStream());
			}
		} catch (Exception x) {
			x.printStackTrace();
			return null;
		}
	}


    @Override
    public byte[] doGetRange(String url, long start) {
        try {
            URL u = new URL(url);
            int port = u.getPort();
            try (Socket cs = new Socket(u.getHost(), port > 0 ? port : HTTP_DEFAULT_PORT)) {
                String rangeString = String.format(RANGE_FORMAT_STR, start, "");
                cs.getOutputStream().write(String.format(GET_FORMAT_STR, u.getPath(), rangeString).getBytes());

                InputStream in = cs.getInputStream();

                String statusLine = Http.readLine(in);
                String[] statusParts = Http.parseHttpReply(statusLine);

                if (statusParts[1].equals(HTTP_206_OK)) {
                    String headerLine;
                    int contentLength = -1;
                    while ((headerLine = Http.readLine(in)).length() > 0) {
                        String[] headerParts = Http.parseHttpHeader(headerLine);
                        if (headerParts[0].equalsIgnoreCase(CONTENT_LENGTH))
                            contentLength = Integer.valueOf(headerParts[1]);
                    }

                    if (contentLength >= 0)
                        return in.readNBytes(contentLength);
                    else
                        return in.readAllBytes();
                }
                else System.out.println(statusLine);
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }


    @Override
    public byte[] doGetRange(String url, long start, long end) {
        try {
            URL u = new URL(url);
            int port = u.getPort();
            try (Socket cs = new Socket(u.getHost(), port > 0 ? port : HTTP_DEFAULT_PORT)) {
                String rangeString = String.format(RANGE_FORMAT_STR, start, end);
                cs.getOutputStream().write(String.format(GET_FORMAT_STR, u.getPath(), rangeString).getBytes());

                InputStream in = cs.getInputStream();

                String statusLine = Http.readLine(in);
                String[] statusParts = Http.parseHttpReply(statusLine);

                if (statusParts[1].equals(HTTP_206_OK)) {
                    String headerLine;
                    int contentLength = -1;
                    while ((headerLine = Http.readLine(in)).length() > 0) {
                        String[] headerParts = Http.parseHttpHeader(headerLine);
                        if (headerParts[0].equalsIgnoreCase(CONTENT_LENGTH))
                            contentLength = Integer.valueOf(headerParts[1]);
                    }

                    if (contentLength >= 0)
                        return in.readNBytes(contentLength);
                    else
                        return in.readAllBytes();
                }
                else System.out.println(statusLine);
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }

    @Override
    public void close() {
        //Nothing to do on close
    }
}
