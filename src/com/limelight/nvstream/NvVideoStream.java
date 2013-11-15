package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpOrderedQueue;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.video.AvVideoDepacketizer;
import com.limelight.nvstream.av.video.AvVideoPacket;

import jlibrtp.Participant;
import jlibrtp.RTPSession;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.view.Surface;

public class NvVideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	public static final String[] blacklistedDecoders = new String[]
		{
		"OMX.google", // Android software decoder
		"OMX.TI.DUCATI1" // TI Ducati hardware decoder
		};
	
	
	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	
	// Video is RTP packet type 96
	private AvRtpOrderedQueue packets = new AvRtpOrderedQueue((byte)96);
	
	private RTPSession session;
	private DatagramSocket rtp, rtcp;
	private Socket firstFrameSocket;

	private boolean videoOff = false;
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();

	private AvVideoDepacketizer depacketizer = new AvVideoDepacketizer();
	
	private boolean aborting = false;
	
	public void abort()
	{
		if (aborting) {
			return;
		}
		
		aborting = true;
		
		// Interrupt threads
		for (Thread t : threads) {
			t.interrupt();
		}
		
		// Close the socket to interrupt the receive thread
		if (rtp != null) {
			rtp.close();
		}
		if (rtcp != null) {
			rtcp.close();
		}
		if (firstFrameSocket != null) {
			try {
				firstFrameSocket.close();
			} catch (IOException e) {}
		}
		
		// Wait for threads to terminate
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) { }
		}
		
		if (session != null) {
			//session.endSession();
		}
		if (videoDecoder != null) {
			videoDecoder.release();
		}
		
		threads.clear();
	}
	
	public boolean isVideoOff()
	{
		return videoOff;
	}
	
	public void trim()
	{
		depacketizer.trim();
	}

	private void readFirstFrame(String host) throws IOException
	{
		byte[] firstFrame = depacketizer.allocatePacketBuffer();
		
		System.out.println("VID: Waiting for first frame");
		firstFrameSocket = new Socket(host, FIRST_FRAME_PORT);

		try {
			InputStream firstFrameStream = firstFrameSocket.getInputStream();
			
			int offset = 0;
			for (;;)
			{
				int bytesRead = firstFrameStream.read(firstFrame, offset, firstFrame.length-offset);
				
				if (bytesRead == -1)
					break;
				
				offset += bytesRead;
			}
			
			System.out.println("VID: First frame read ("+offset+" bytes)");
			depacketizer.addInputData(new AvVideoPacket(new AvByteBufferDescriptor(firstFrame, 0, offset)));
		} finally {
			firstFrameSocket.close();
			firstFrameSocket = null;	
		}
	}
	
	public void setupRtpSession(String host) throws SocketException
	{
		rtp = new DatagramSocket(RTP_PORT);
		rtcp = new DatagramSocket(RTCP_PORT);
		
		rtp.setReceiveBufferSize(1024*1024*16);
		System.out.println("RECV: "+rtp.getReceiveBufferSize());
		
		session = new RTPSession(rtp, rtcp);
		session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
	}
	
	private MediaCodecInfo findAvcHighProfileCodec()
	{
		// Find a suitable hardware decoder
		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
			
			// We need a decoder
			if (mci.isEncoder()) {
				mci = null;
				continue;
			}
			
			// Make sure this isn't blacklisted
			for (String badCodec : blacklistedDecoders) {
				if (mci.getName().startsWith(badCodec)) {
					mci = null;
					break;
				}
			}
			
			if (mci != null) {
				// It needs to decode what we want
				for (String type : mci.getSupportedTypes()) {
					if (type.equals("video/avc")) {
						return mci;
					}
				}
			}
		}
		
		return null;
	}
	
	public void setupDecoders(Surface surface)
	{
		MediaCodecInfo mci = findAvcHighProfileCodec();
		
		// Check if a good video codec was found
		if (mci == null) {
			videoOff = true;
			return;
		}
		else {
			System.out.println("Selected decoder: "+mci.getName());
		}
		
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);
		videoDecoder = MediaCodec.createByCodecName(mci.getName());
		videoDecoder.configure(videoFormat, surface, null, 0);
		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		videoDecoder.start();
		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
	}
	
	public void beginVideoStream(final String host)
	{
		new Thread() {
			@Override
			public void run() {
				// Read the first frame to start the UDP video stream
				try {
					readFirstFrame(host);
				} catch (IOException e2) {
					abort();
					return;
				}
			}
		}.start();
	}

	public void readyVideoStream(final String host, final Surface surface)
	{
		// This thread becomes the output display thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// Setup the decoder context
				setupDecoders(surface);
				
				if (!videoOff) {
					// Open RTP sockets and start session
					try {
						setupRtpSession(host);
					} catch (SocketException e1) {
						e1.printStackTrace();
						return;
					}

					// Start pinging before reading the first frame
					// so Shield Proxy knows we're here and sends us
					// the reference frame
					startUdpPingThread();
					
					// Start the receive thread early to avoid missing
					// early packets
					startReceiveThread();
					
					// Start the depacketizer thread to deal with the RTP data
					startDepacketizerThread();
					
					// Start decoding the data we're receiving
					startDecoderThread();
					
					// Render the frames that are coming out of the decoder
					outputDisplayLoop(this);	
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startDecoderThread()
	{
		// Decoder thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// Read the decode units generated from the RTP stream
				while (!isInterrupted())
				{
					AvDecodeUnit du;
					try {
						du = depacketizer.getNextDecodeUnit();
					} catch (InterruptedException e) {
						abort();
						return;
					}
					
					switch (du.getType())
					{
						case AvDecodeUnit.TYPE_H264:
						{
							// Wait for an input buffer or thread termination
							while (!isInterrupted())
							{
								int inputIndex = videoDecoder.dequeueInputBuffer(100);
								if (inputIndex >= 0)
								{
									ByteBuffer buf = videoDecoderInputBuffers[inputIndex];
									
									// Clear old input data
									buf.clear();
									
									// Copy data from our buffer list into the input buffer
									for (AvByteBufferDescriptor desc : du.getBufferList())
									{
										buf.put(desc.data, desc.offset, desc.length);
									}
									
									depacketizer.releaseDecodeUnit(du);

									videoDecoder.queueInputBuffer(inputIndex,
												0, du.getDataLength(),
												0, du.getFlags());
									
									break;
								}
							}
						}
						break;
					
						default:
						{
							System.err.println("Unknown decode unit type");
							abort();
							return;
						}
					}
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startDepacketizerThread()
	{
		// This thread lessens the work on the receive thread
		// so it can spend more time waiting for data
		Thread t = new Thread() {
			@Override
			public void run() {
				AvRtpPacket packet;
				
				while (!isInterrupted())
				{
					try {
						// Blocks for a maximum of 50ms
						packet = packets.removeNext(50);
					} catch (InterruptedException e) {
						abort();
						return;
					}
					
					// !!! We no longer own the data buffer at this point !!!
					depacketizer.addInputData(packet);
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				DatagramPacket packet = new DatagramPacket(depacketizer.allocatePacketBuffer(), 1500);
				AvByteBufferDescriptor desc = new AvByteBufferDescriptor(null, 0, 0);
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						abort();
						return;
					}
					
					desc.length = packet.getLength();
					desc.offset = packet.getOffset();
					desc.data = packet.getData();
					
					// Give the packet to the depacketizer thread
					packets.addPacket(new AvRtpPacket(desc));
					
					// Get a new buffer from the buffer pool
					packet.setData(depacketizer.allocatePacketBuffer(), 0, 1500);
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startUdpPingThread()
	{
		// Ping thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// PING in ASCII
				final byte[] pingPacket = new byte[] {0x50, 0x49, 0x4E, 0x47};
				
				// RTP payload type is 127 (dynamic)
				session.payloadType(127);
				
				// Send PING every 100 ms
				while (!isInterrupted())
				{
					session.sendData(pingPacket);
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						abort();
						return;
					}
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void outputDisplayLoop(Thread t)
	{
		while (!t.isInterrupted())
		{
			BufferInfo info = new BufferInfo();
			int outIndex = videoDecoder.dequeueOutputBuffer(info, 100);
		    switch (outIndex) {
		    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
		    	System.out.println("Output buffers changed");
			    break;
		    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
		    	System.out.println("Output format changed");
		    	System.out.println("New output Format: " + videoDecoder.getOutputFormat());
		    	break;
		    default:
		      break;
		    }
		    if (outIndex >= 0) {
		    	videoDecoder.releaseOutputBuffer(outIndex, true);
		    }
		}
	}
}
