package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import http.HttpClient11;
import media.MovieManifest;
import media.MovieManifest.*;
import media.MovieManifest.SegmentContent;
import proxy.server.ProxyServer;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:9999";

	// How much better than the picked track bandwidth the current bandwidth must be.
	// Avoids stalling the streaming
	private static final int SLACK = 300000;

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

		// The first segment of each track
		private SegmentContent[] trackHeaders;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient11(MEDIA_SERVER_BASE_URL);
			
			String rawManifest = new String(http.doGet(String.format(MANIFEST_REQUEST_FORMAT, movie)));
			
			this.manifest = MovieManifest.parse(rawManifest);


		}

		/**
		 * Calculates the average bandwidth in Kilobits per second
		 *
		 * @param byteCount The amount of data downloaded in bytes
		 * @param millisElapsed The amount of millisencond elapsed during the download
		 * @return the average bandwidth
		 */
		private static int avgBandwidth(long byteCount, long millisElapsed){
			long bitCount = byteCount * 8;
			double secondsElapsed = millisElapsed / 1000.0;
			return (int)(bitCount / secondsElapsed);
		}

		/**
		 * Puts a segment in the queue, waiting for space if required
		 * @param segment the segment to send
		 */
		private void send(SegmentContent segment){
			try {
				queue.put(segment);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Submits an http request for a specific segment of a given track
		 *
		 * @param track The track to get the segement from
		 * @param segment The segment to get
		 * @return the downloaded data
		 */
		private byte[] downloadSegmentData(Track track, Segment segment){
			String trackUrl = MEDIA_SERVER_BASE_URL + "/" + this.movie + "/" + track.filename();
			long start = segment.offset();
			long end = start + segment.length() - 1;
			return http.doGetRange(trackUrl, start, end);
		}

		/**
		 * Selects the best quality for a given bandwidth
		 *
		 * @param currentBandwidth the current bandwidth for downloading segments
		 * @return the index for the selected track
		 */
		private int pickBestQuality(int currentBandwidth){
			for (int i = this.manifest.tracks().size() - 1; i >= 0; i--) {
				Track track = this.manifest.tracks().get(i);
				if(track.avgBandwidth() <= currentBandwidth - SLACK){
					return i;
				}
			}
			return 0;
		}

		/**
		 * Downloads the first segment of each track, since these are required when switching
		 * between tracks
		 * @return the average bandwidth of the operation
		 */
		private int downloadTrackHeaders(){
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
			int cTrackSegments = this.manifest.tracks().get(0).segments().size();
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
			http.close();
		}
	}
}
