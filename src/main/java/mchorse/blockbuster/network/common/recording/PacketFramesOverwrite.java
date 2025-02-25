package mchorse.blockbuster.network.common.recording;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.data.Frame;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.network.mclib.common.IAnswerRequest;
import mchorse.mclib.network.mclib.common.PacketAnswer;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Packet of frame ranges, split up in chunks to avoid max packet size error
 */
public class PacketFramesOverwrite extends PacketFrames implements IAnswerRequest<SimpleEntry<IKey, Boolean>>
{
    /**
     * overwrite frames from tick.
     * This is separate from frame chunk splitting.
     */
    private int from;
    /**
     * overwrite frames to tick.
     * This is separate from frame chunk splitting.
     */
    private int to;
    /**
     * The start index of this frame chunk, relative to {@link #from}
     * This is needed to split it into chunks.
     */
    private int index;
    private int callbackID = -1;

    public PacketFramesOverwrite()
    {

    }

    public PacketFramesOverwrite(int from, int to, int index, String filename, List<Frame> frames)
    {
        super(filename, 0, 0, frames);

        this.from = from;
        this.to = to;
        this.index = index;
    }

    public PacketFramesOverwrite(int from, int to, int index, String filename, List<Frame> frames, int callbackID)
    {
        this(from, to, index, filename, frames);

        this.callbackID = callbackID;
    }

    public int getFrom()
    {
        return this.from;
    }

    public int getTo()
    {
        return this.to;
    }

    public int getIndex()
    {
        return this.index;
    }

    @Override
    public void setCallbackID(int callbackID)
    {
        this.callbackID = callbackID;
    }

    public Optional<Integer> getCallbackID()
    {
        return Optional.of(this.callbackID == -1 ? null : this.callbackID);
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        this.from = buf.readInt();
        this.to = buf.readInt();
        this.index = buf.readInt();
        this.callbackID = buf.readInt();

        super.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(this.from);
        buf.writeInt(this.to);
        buf.writeInt(this.index);
        buf.writeInt(this.callbackID);

        super.toBytes(buf);
    }

    /**
     *
     * @param value expects {String, boolean}
     * @return the packet answer or null if no callback has been registered
     */
    @Override
    public PacketAnswer<SimpleEntry<IKey, Boolean>> getAnswer(SimpleEntry<IKey, Boolean> value) throws NoSuchElementException
    {
        return new PacketAnswer<>(this.getCallbackID().get(), value);
    }
}
