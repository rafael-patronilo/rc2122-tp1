package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import media.MovieManifest;
import media.MovieManifest.*;
import media.MovieManifest.SegmentContent;
import proxy.server.ProxyServer;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:9999";

	public static void main(String[] args) throws Exception {

		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}
	/**
	 * 
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritize:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		private static final String MANIFEST_REQUEST_FORMAT = MEDIA_SERVER_BASE_URL + "/%s/manifest.txt";
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient10();
			
			String rawManifest = new String(http.doGet(String.format(MANIFEST_REQUEST_FORMAT, movie)));
			
			this.manifest = MovieManifest.parse(rawManifest); //TODO F main manifest
		}

		void send(SegmentContent segment){
			try {
				queue.put(segment);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Runs automatically in a dedicated thread...
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment
		 */
		public void run() {
			// TODO main.run
			int cTrackSegments = this.manifest.tracks().get(0).segments().size();
			int cTracks = this.manifest.tracks().size(); 
			int quality = 0; //cTracks-1;
			for(int i = 0; i < cTrackSegments; i++) {
				Track track = this.manifest.tracks().get(quality); 
				Segment segmentData =track.segments().get(i);
				long startTime = System.currentTimeMillis();
				byte[] data = http.doGetRange(MEDIA_SERVER_BASE_URL + "/" + this.movie + "/" + track.filename(), segmentData.offset(), segmentData.offset()+segmentData.length()-1);
				long downloadTime = System.currentTimeMillis() - startTime;
				SegmentContent segment = new SegmentContent(track.contentType(), data);
				send(segment);
				
				
			}
			send(new SegmentContent(this.manifest.tracks().get(quality).contentType(), new byte[0]));
		}
	}
}
