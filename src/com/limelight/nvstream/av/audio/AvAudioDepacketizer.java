package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.AvShortBufferDescriptor;
import com.limelight.nvstream.av.AvShortBufferPool;

public class AvAudioDepacketizer {
	private LinkedBlockingQueue<AvShortBufferDescriptor> decodedUnits =
			new LinkedBlockingQueue<AvShortBufferDescriptor>(15);
	
	private AvShortBufferPool pool = new AvShortBufferPool(OpusDecoder.getMaxOutputShorts());

	public void trim()
	{
		pool.purge();
	}
	
	private void decodeData(byte[] data, int off, int len)
	{
		// Submit this data to the decoder
		short[] pcmData = pool.allocate();
		int decodeLen = OpusDecoder.decode(data, off, len, pcmData);
		
		if (decodeLen > 0) {
			// Return value of decode is frames decoded per channel
			decodeLen *= OpusDecoder.getChannelCount();
			
			// Put it on the decoded queue
			if (!decodedUnits.offer(new AvShortBufferDescriptor(pcmData, 0, decodeLen)))
			{
				pool.free(pcmData);
			}
		}
		else {
			pool.free(pcmData);
		}
	}
	
	public void decodeInputData(AvRtpPacket packet)
	{
		// If the packet is null, this indicates a packet loss
		if (packet == null) {
			decodeData(null, 0, 0);
			return;
		}
		
		// This is all the depacketizing we need to do
		AvByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		decodeData(rtpPayload.data, rtpPayload.offset, rtpPayload.length);
	}
	
	public void releaseBuffer(AvShortBufferDescriptor decodedData)
	{
		pool.free(decodedData.data);
	}
	
	public AvShortBufferDescriptor getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
