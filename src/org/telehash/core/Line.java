package org.telehash.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Line {
    
    private static final int SHA256_DIGEST_SIZE = 32;
    
    public enum State {
        PENDING,
        ESTABLISHED,
        CLOSED
    };
    private State mState = State.CLOSED;
    
    private static class Completion<T> {
        public CompletionHandler<T> mHandler;
        public Object mAttachment;
        public Completion(CompletionHandler<T> handler, Object attachment) {
            this.mHandler = handler;
            this.mAttachment = attachment;
        }
    }
    private List<Completion<Line>> mOpenCompletionHandlers = new ArrayList<Completion<Line>>();

    private LineIdentifier mIncomingLineIdentifier;
    private LineIdentifier mOutgoingLineIdentifier;
    private Node mRemoteNode;
    private OpenPacket mLocalOpenPacket;
    private OpenPacket mRemoteOpenPacket;
    private byte[] mSharedSecret;
    private byte[] mEncryptionKey;
    private byte[] mDecryptionKey;

    private Telehash mTelehash;
    private Map<ChannelIdentifier,Channel> mChannels = new HashMap<ChannelIdentifier,Channel>();
    
    public Line(Telehash telehash) {
        mTelehash = telehash;
    }
    
    public void setState(State state) {
        mState = state;
    }
    public State getState() {
        return mState;
    }
    
    public void setIncomingLineIdentifier(LineIdentifier lineIdentifier) {
        mIncomingLineIdentifier = lineIdentifier;
    }
    
    public LineIdentifier getIncomingLineIdentifier() {
        return mIncomingLineIdentifier;
    }

    public void setOutgoingLineIdentifier(LineIdentifier lineIdentifier) {
        mOutgoingLineIdentifier = lineIdentifier;
    }
    
    public LineIdentifier getOutgoingLineIdentifier() {
        return mOutgoingLineIdentifier;
    }

    public void setRemoteNode(Node remoteNode) {
        mRemoteNode = remoteNode;
    }
    
    public Node getRemoteNode() {
        return mRemoteNode;
    }
    
    public void setLocalOpenPacket(OpenPacket localOpenPacket) {
        mLocalOpenPacket = localOpenPacket;
    }
    
    public OpenPacket getLocalOpenPacket() {
        return mLocalOpenPacket;
    }
    
    public void setRemoteOpenPacket(OpenPacket remoteOpenPacket) {
        mRemoteOpenPacket = remoteOpenPacket;
    }
    
    public OpenPacket getRemoteOpenPacket() {
        return mRemoteOpenPacket;
    }
    
    public void setSharedSecret(byte[] sharedSecret) {
        if (sharedSecret == null || sharedSecret.length == 0) {
            throw new IllegalArgumentException("invalid shared secret");
        }
        mSharedSecret = sharedSecret;
    }
    
    public byte[] getSharedSecret() {
        return mSharedSecret;
    }

    public void setEncryptionKey(byte[] encryptionKey) {
        if (encryptionKey == null || encryptionKey.length != SHA256_DIGEST_SIZE) {
            throw new IllegalArgumentException("invalid encryption key");
        }
        mEncryptionKey = encryptionKey;
    }
    
    public byte[] getEncryptionKey() {
        return mEncryptionKey;
    }

    public void setDecryptionKey(byte[] decryptionKey) {
        if (decryptionKey == null || decryptionKey.length != SHA256_DIGEST_SIZE) {
            throw new IllegalArgumentException("invalid encryption key");
        }
        mDecryptionKey = decryptionKey;
    }
    
    public byte[] getDecryptionKey() {
        return mDecryptionKey;
    }
    
    public void addOpenCompletionHandler(
            CompletionHandler<Line> openCompletionHandler,
            Object openCompletionAttachment
    ) {
        if (mState == State.ESTABLISHED) {
            // line is already established, so complete immediately.
            openCompletionHandler.completed(this, openCompletionAttachment);
        } else {
            mOpenCompletionHandlers.add(new Completion<Line>(openCompletionHandler, openCompletionAttachment));
        }
    }
    
    public void callOpenCompletionHandlers() {
        for (Completion<Line> completion : mOpenCompletionHandlers) {
            completion.mHandler.completed(this, completion.mAttachment);
        }
    }
    
    public Telehash getTelehash() {
        return mTelehash;
    }
    
    public long getOpenTime() {
        if (mLocalOpenPacket != null) {
            return mLocalOpenPacket.getOpenTime();
        } else {
            return 0L;
        }
    }
    
    public Channel openChannel(String type, ChannelHandler channelHandler) {
        // create a channel object and establish a callback
        Channel channel = new Channel(mTelehash, this, type);

        // record channel handler
        channel.setChannelHandler(channelHandler);
        
        // track channel
        mChannels.put(channel.getChannelIdentifier(), channel);
        
        // consider the channel to be "open" even though we don't know
        // if the remote side will be happy with this channel type.
        channelHandler.handleOpen(channel);

        return channel;
    }
    
    public void handleIncoming(LinePacket linePacket) {
        ChannelPacket channelPacket = linePacket.getChannelPacket();
        Channel channel = mChannels.get(channelPacket.getChannelIdentifier());
        if (channel == null) {
            // is this the first communication of a new channel?
            // (it will have a type field)
            String type = channelPacket.getType();
            if (type == null) {
                Log.i("dropping packet for unknown channel without type");
                return;
            }
            // is anyone interested in channels of this type?
            ChannelHandler channelHandler = mTelehash.getSwitch().getChannelHandler(type);
            if (channelHandler == null) {
                Log.i("no channel handler for type");
                return;
            }
            
            // create channel
            channel = new Channel(mTelehash, this, type);
            channel.setChannelHandler(channelHandler);
            mChannels.put(channel.getChannelIdentifier(), channel);
            
            // invoke callback
            channelHandler.handleIncoming(channel, channelPacket);
            return;
        }
        // is this the end?
        if (channelPacket.isEnd()) {
            mChannels.remove(channel.getChannelIdentifier());
        }
        // dispatch to channel handler
        channel.getChannelHandler().handleIncoming(channel, channelPacket);
    }
    
    public static Set<Line> sortByOpenTime(Collection<Line> lines) {
        TreeSet<Line> set = new TreeSet<Line>(new Comparator<Line>() {
            public int compare(Line a, Line b) {
                return (int)(a.getOpenTime() - b.getOpenTime());
            }
        });
        set.addAll(lines);
        return set;
    }
    
    public String toString() {
        return "Line["+mIncomingLineIdentifier+"->"+mOutgoingLineIdentifier+"@"+getOpenTime()+"]";
    }
}
