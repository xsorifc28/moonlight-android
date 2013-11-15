package com.limelight.nvstream.av;

import java.nio.ByteBuffer;

public class AvRtpPacket implements Comparable<AvRtpPacket> {
	
	private byte packetType;
	private short seqNum;
	private AvByteBufferDescriptor buffer;
	
	public AvRtpPacket(AvByteBufferDescriptor buffer)
	{
		this.buffer = new AvByteBufferDescriptor(buffer);
		
		ByteBuffer bb = ByteBuffer.wrap(buffer.data, buffer.offset, buffer.length);
		
		// Discard the first byte
		bb.position(bb.position()+1);
		
		// Get the packet type
		packetType = bb.get();
		
		// Get the sequence number
		seqNum = bb.getShort();
	}
	
	public int getUnsignedSequenceNumber()
	{
		if (seqNum < 0) {
			return seqNum + 65536;
		}
		else {
			return seqNum;
		}
	}
	
	public byte getPacketType()
	{
		return packetType;
	}
	
	public short getSequenceNumber()
	{
		return seqNum;
	}
	
	public byte[] getBackingBuffer()
	{
		return buffer.data;
	}
	
	public AvByteBufferDescriptor getNewPayloadDescriptor()
	{
		return new AvByteBufferDescriptor(buffer.data, buffer.offset+12, buffer.length-12);
	}

	@Override
	public int compareTo(AvRtpPacket another) {
		return this.getUnsignedSequenceNumber() -
				another.getUnsignedSequenceNumber();
	}
}
