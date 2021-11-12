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
	private static final int SLACK = 200000;

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
		private SegmentContent[] trackHeaders;
		final BlockingQueue<SegmentContent> queue;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient10();
			
			String rawManifest = new String(http.doGet(String.format(MANIFEST_REQUEST_FORMAT, movie)));
			
			this.manifest = MovieManifest.parse(rawManifest); //TODO F main manifest
		}

		private static long millisToSeconds(long millis){
			return millis / 1000;
		}

		private static int avgBandwidth(long byteCount, long millisElapsed){
			long bitCount = byteCount * 8;
			double secondsElapsed = millisElapsed / 1000.0;
			return (int)(bitCount / secondsElapsed);
		}

		void send(SegmentContent segment){
			try {
				queue.put(segment);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		byte[] downloadSegmentData(Track track, Segment segment){
			String trackUrl = MEDIA_SERVER_BASE_URL + "/" + this.movie + "/" + track.filename();
			long start = segment.offset();
			long end = start + segment.length() - 1;
			return http.doGetRange(trackUrl, start, end);
		}

		int pickBestQuality(int currentBandwidth){
			for (int i = this.manifest.tracks().size() - 1; i >= 0; i--) {
				Track track = this.manifest.tracks().get(i);
				if(track.avgBandwidth() <= currentBandwidth - SLACK){
					return i;
				}
			}
			return 0;
		}

		/**
		 * TODO
		 * @return
		 */
		int downloadTrackHeaders(){
			int i = 0;
			long totalTime = 0;
			long totalData = 0;
			this.trackHeaders = new SegmentContent[this.manifest.tracks().size()];
			for(Track track : this.manifest.tracks()){
				long startTime = System.currentTimeMillis();
				byte[] data = downloadSegmentData(track, track.segments().get(0));
				totalTime += System.currentTimeMillis() - startTime;
				totalData += data.length;
				this.trackHeaders[i++] = new SegmentContent(track.contentType(), data);
			}
			return avgBandwidth(totalData, totalTime);
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
			int avgBandwidth = downloadTrackHeaders();
			int quality = pickBestQuality(avgBandwidth);
			send(this.trackHeaders[quality]);
			for(int i = 1; i < cTrackSegments; i++) {
				Track track = this.manifest.tracks().get(quality); 
				Segment segmentData = track.segments().get(i);
				long startTime = System.currentTimeMillis();
				byte[] data = downloadSegmentData(track, segmentData);
				avgBandwidth = avgBandwidth(data.length, System.currentTimeMillis() - startTime);
				SegmentContent segment = new SegmentContent(track.contentType(), data);
				send(segment);
				int newQuality = pickBestQuality(avgBandwidth);
				if(newQuality!= quality){
					send(this.trackHeaders[newQuality]);
					quality = newQuality;
				}
			}
			send(new SegmentContent(this.manifest.tracks().get(quality).contentType(), new byte[0]));
		}
	}
}
