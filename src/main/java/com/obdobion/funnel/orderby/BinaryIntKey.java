package com.obdobion.funnel.orderby;

import java.nio.ByteBuffer;

/**
 * <p>
 * BinaryIntKey class.
 * </p>
 *
 * @author Chris DeGreef fedupforone@gmail.com
 */
public class BinaryIntKey extends KeyPart
{
    Long contents;

    /**
     * <p>
     * Constructor for BinaryIntKey.
     * </p>
     */
    public BinaryIntKey()
    {
        super();
        // assert parseFormat == null : "\"--format " + parseFormat +
        // "\" is not expected for \"BInteger\"";
        // assert length == 1 || length == 2 || length == 4 || length == 8 :
        // "Binary Integer lengths must be 1, 2, 4, or 8.";
    }

    private void formatObjectIntoKey(final KeyContext context, final Long _longValue)
    {
        Long longValue = _longValue;
        if (longValue < 0)
            if (direction == KeyDirection.AASC || direction == KeyDirection.ADESC)
                longValue = 0 - longValue;

        if (direction == KeyDirection.DESC || direction == KeyDirection.ADESC)
            longValue = 0 - longValue;

        final ByteBuffer bb = ByteBuffer.wrap(context.key, context.keyLength, 8);
        unformattedContents = bb.array();

        /*
         * Flip the sign bit so negatives are before positives in ascending
         * sorts.
         */
        switch (length)
        {
            case 1:
                bb.put((byte) ((longValue.byteValue()) ^ 0x80));
                break;
            case 2:
                bb.putShort((short) ((longValue.shortValue()) ^ 0x8000));
                break;
            case 4:
                bb.putInt(((longValue.intValue()) ^ 0x80000000));
                break;
            case 8:
                bb.putLong(longValue ^ 0x8000000000000000L);
                break;
        }
        context.keyLength += length;
    }

    /** {@inheritDoc} */
    @Override
    public Object getContents()
    {
        return contents;
    }

    /** {@inheritDoc} */
    @Override
    public double getContentsAsDouble()
    {
        return contents;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInteger()
    {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isNumeric()
    {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void pack(final KeyContext context) throws Exception
    {
        parseObject(context);
        formatObjectIntoKey(context, contents);

        if (nextPart != null)
            nextPart.pack(context);
    }

    /** {@inheritDoc} */
    @Override
    public void parseObjectFromRawData(final byte[] rawBytes) throws Exception
    {
        if (rawBytes.length < offset + length)
            throw new Exception("index out of bounds: " + (offset + length));

        final ByteBuffer bb = ByteBuffer.wrap(rawBytes, offset, 8);
        switch (length)
        {
            case 1:
                contents = new Long(bb.get());
                return;
            case 2:
                contents = new Long(bb.getShort());
                return;
            case 4:
                contents = new Long(bb.getInt());
                return;
            case 8:
                contents = new Long(bb.getLong());
                return;
        }
        throw new Exception("invalid length for a binary integer field: " + length);
    }
}
