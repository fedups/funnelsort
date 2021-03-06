package com.obdobion.funnel.orderby;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obdobion.funnel.columns.ColumnHelper;
import com.obdobion.funnel.parameters.FunnelContext;

/**
 * <p>
 * KeyHelper class.
 * </p>
 *
 * @author Chris DeGreef fedupforone@gmail.com
 */
public class KeyHelper
{
    final private static Logger logger       = LoggerFactory.getLogger(KeyHelper.class);

    /** Constant <code>MAX_KEY_SIZE=255</code> */
    public static final int     MAX_KEY_SIZE = 255;
    final KeyContext            context;
    final int                   maxKeyBytes;
    KeyPart                     formatter;

    /**
     * <p>
     * Constructor for KeyHelper.
     * </p>
     */
    public KeyHelper()
    {
        this(MAX_KEY_SIZE);
    }

    /**
     * <p>
     * Constructor for KeyHelper.
     * </p>
     *
     * @param maxsize a int.
     */
    public KeyHelper(final int maxsize)
    {
        logger.debug("maximum string key length is " + MAX_KEY_SIZE);

        maxKeyBytes = maxsize;
        context = new KeyContext();
    }

    /**
     * Add the key in sequence after all other keys that have already been
     * defined. This is done through a linked list of keys. Use the column
     * helper to find the definition of the key if a column name was specified.
     *
     * @param _formatter a {@link com.obdobion.funnel.orderby.KeyPart} object.
     * @param columnHelper a {@link com.obdobion.funnel.columns.ColumnHelper}
     *            object.
     */
    public void add(final KeyPart _formatter, final ColumnHelper columnHelper)
    {
        if (columnHelper != null && columnHelper.exists(_formatter.columnName))
        {
            final KeyPart colDef = columnHelper.get(_formatter.columnName);
            _formatter.defineFrom(colDef);
        }

        if (this.formatter == null)
            this.formatter = _formatter;
        else
            this.formatter.add(_formatter);
    }

    /**
     * It is likely that the provided data is a reusable buffer of bytes. So we
     * can't just store these bytes for later use.
     *
     * @param data an array of byte.
     * @throws java.lang.Exception if any.
     * @param recordNumber a long.
     * @return a {@link com.obdobion.funnel.orderby.KeyContext} object.
     */
    public KeyContext extractKey(final byte[] data, final long recordNumber) throws Exception
    {
        /*
         * The extra byte is for a 0x00 character to be placed at the end of
         * String keys. This is important in order to handle keys where the user
         * specified the maximum length for a String key. Or took the default
         * sort, which is the maximum key.
         */
        context.key = new byte[maxKeyBytes + 1];
        context.keyLength = 0;
        context.rawRecordBytes = new byte[1][];
        context.rawRecordBytes[0] = data;
        context.recordNumber = recordNumber;

        formatter.pack(context);

        context.rawRecordBytes = null;
        return context;
    }

    /**
     * Call this method for csv files that break each row up into fields (byte
     * arrays). [][].
     *
     * @param data an array of byte.
     * @param recordNumber a long.
     * @throws java.lang.Exception if any.
     * @return a {@link com.obdobion.funnel.orderby.KeyContext} object.
     */
    public KeyContext extractKey(final byte[][] data, final long recordNumber) throws Exception
    {
        /*
         * The extra byte is for a 0x00 character to be placed at the end of
         * String keys. This is important in order to handle keys where the user
         * specified the maximum length for a String key. Or took the default
         * sort, which is the maximum key.
         */
        context.key = new byte[maxKeyBytes + 1];
        context.keyLength = 0;
        context.rawRecordBytes = data;
        context.recordNumber = recordNumber;

        formatter.pack(context);

        context.rawRecordBytes = null;
        return context;
    }

    /**
     * <p>
     * extractKey.
     * </p>
     *
     * @param data a {@link java.lang.String} object.
     * @param recordNumber a long.
     * @return a {@link com.obdobion.funnel.orderby.KeyContext} object.
     * @throws java.lang.Exception if any.
     */
    public KeyContext extractKey(final String data, final long recordNumber) throws Exception
    {
        context.key = new byte[maxKeyBytes];
        context.keyLength = 0;
        context.rawRecordBytes = new byte[1][];
        context.rawRecordBytes[0] = data.getBytes();
        context.recordNumber = recordNumber;

        formatter.pack(context);

        context.rawRecordBytes = null;
        return context;
    }

    /**
     * <p>
     * setUpAsCopy.
     * </p>
     *
     * @param funnelContext a
     *            {@link com.obdobion.funnel.parameters.FunnelContext} object.
     */
    public void setUpAsCopy(final FunnelContext funnelContext)
    {
        switch (funnelContext.getCopyOrder())
        {
            case ByKey:
                AlphaKey ak;
                if (funnelContext.getCsv() == null)
                {
                    add(ak = new AlphaKey(), null);
                    ak.offset = 0;
                    ak.length = MAX_KEY_SIZE;
                    ak.direction = KeyDirection.ASC;
                } else
                {
                    add(ak = new AlphaKey(), null);
                    ak.csvFieldNumber = 0;
                    ak.offset = 0;
                    ak.length = MAX_KEY_SIZE;
                    ak.direction = KeyDirection.ASC;
                }
                break;
            case Original:
                add(new RecordNumberKey(KeyDirection.ASC, null), null);
                break;
            case Reverse:
                add(new RecordNumberKey(KeyDirection.DESC, null), null);
                break;
        }
    }

}
