package com.obdobion.funnel.provider;

import java.io.IOException;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obdobion.algebrain.Equ;
import com.obdobion.funnel.Funnel;
import com.obdobion.funnel.FunnelDataProvider;
import com.obdobion.funnel.FunnelItem;
import com.obdobion.funnel.orderby.KeyContext;
import com.obdobion.funnel.parameters.DuplicateDisposition;
import com.obdobion.funnel.parameters.FunnelContext;
import com.obdobion.funnel.segment.SourceProxyRecord;

public abstract class AbstractProvider implements FunnelDataProvider
{
    static final Logger logger = LoggerFactory.getLogger(AbstractProvider.class);

    final FunnelContext context;
    InputReader         reader;
    private long        thisFileRecordNumber;
    private long        continuousRecordNumber;
    byte                row[];
    int                 unselectedCount;

    private Equ[]       cachedEquations;

    public AbstractProvider(final FunnelContext _context) throws IOException, ParseException
    {
        this.context = _context;
        /*
         * This is really to handle test cases. It should never be null.
         */
        if (_context == null)
        {
            row = null;
            return;
        }
        initialize();
    }

    public void attachTo (
        final FunnelItem item)
    {
        item.setProvider(this);
    }

    public void close () throws IOException, ParseException
    {
        if (reader == null)
            return;
        reader.close();
        reader = null;
    }

    Equ[] getCachedEquations ()
    {
        if (cachedEquations == null)
        {
            int ceSize = 0;
            if (context.whereEqu != null)
                ceSize += context.whereEqu.length;
            if (context.stopEqu != null)
                ceSize += context.stopEqu.length;
            cachedEquations = new Equ[ceSize];

            int ce = 0;
            if (context.whereEqu != null)
                for (final Equ equ : context.whereEqu)
                    cachedEquations[ce++] = equ;
            if (context.stopEqu != null)
                for (final Equ equ : context.stopEqu)
                    cachedEquations[ce++] = equ;
        }
        return cachedEquations;
    }

    long getContinuousRecordNumber ()
    {
        return continuousRecordNumber;
    }

    long getThisFileRecordNumber ()
    {
        return thisFileRecordNumber;
    }

    abstract void initialize () throws IOException, ParseException;

    boolean isRowSelected (@SuppressWarnings("unused")
    final int byteCount)
    {
        return true;
    }

    void logStatistics (final int fileIndex) throws ParseException, IOException
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(Funnel.ByteFormatter.format(getThisFileRecordNumber()));
        sb.append(" rows obtained from ");
        if (context.isSysin())
            sb.append("SYSIN");
        else
            sb.append(context.getInputFile(fileIndex).getName());
        if (unselectedCount > 0)
        {
            sb.append(", ");
            sb.append(Funnel.ByteFormatter.format(unselectedCount));
            sb.append(" filtered out by where clause");
        }

        context.inputCounters(unselectedCount, getThisFileRecordNumber());

        logger.debug(sb.toString());
    }

    public boolean next (final FunnelItem item, final long phase) throws IOException, ParseException
    {
        /*
         * Only return 1 row per phase per item.
         */
        if (item.getPhase() == phase || reader == null)
        {
            item.setEndOfData(true);
            return false;
        }
        item.setPhase(phase);

        boolean earlyEnd = false;
        int byteCount = 0;
        long startPosition = 0;
        try
        {
            while (true)
            {
                startPosition = reader.position();
                byteCount = reader.read(row);

                if (byteCount == -1)
                {
                    /*
                     * See if there are more files to be read.
                     */
                    if (context.startNextInput())
                    {
                        logStatistics(context.inputFileIndex() - 1);
                        if (context.inPlaceSort)
                            setContinuousRecordNumber(0);
                        setThisFileRecordNumber(0);
                        unselectedCount = 0;
                        reader.close();
                        reader.open(context.getInputFile(context.inputFileIndex()));
                        continue;
                    }
                    break;
                }
                if (context.headerHelper.isWaitingForInput())
                {
                    context.headerHelper
                            .extract(context, row, getContinuousRecordNumber(), byteCount, getCachedEquations());
                    continue;
                }
                /*
                 * Putting this incrementer here causes the record number to be
                 * 1 relative.
                 */
                setThisFileRecordNumber(getThisFileRecordNumber() + 1);
                setContinuousRecordNumber(getContinuousRecordNumber() + 1);

                if (!recordLengthOK(byteCount))
                    continue;

                if (!isRowSelected(byteCount))
                {
                    continue;
                }

                preSelectionExtract(byteCount);

                if (context.stopIsTrue())
                {
                    earlyEnd = true;
                    /*
                     * Uncount the termination record.
                     */
                    setThisFileRecordNumber(getThisFileRecordNumber() - 1);
                    setContinuousRecordNumber(getContinuousRecordNumber() - 1);

                    logger.debug("stopWhen triggered at row " + getContinuousRecordNumber());
                    break;
                }

                if (!context.whereIsTrue())
                {
                    unselectedCount++;
                    continue;
                }
                break;
            }

        } catch (final Exception e)
        {
            logger.error(e.getMessage(), e);
            throw new IOException(e.getMessage(), e);
        }
        if (byteCount == -1 || earlyEnd)
        {
            item.setEndOfData(true);
            try
            {
                logStatistics(context.inputFileIndex());
                close();
            } catch (final IOException e)
            {
                e.printStackTrace();
            }
            return false;
        }

        final KeyContext kContext = postReadKeyProcessing(byteCount);

        final SourceProxyRecord wrapped = SourceProxyRecord.getInstance(context);
        wrapped.originalInputFileIndex = context.inputFileIndex();

        wrapped.size = kContext.keyLength;
        wrapped.sortKey = kContext.key;
        wrapped.originalSize = byteCount;
        wrapped.originalLocation = startPosition;

        if (DuplicateDisposition.LastOnly == context.duplicateDisposition
            || DuplicateDisposition.Reverse == context.duplicateDisposition)
            wrapped.setOriginalRecordNumber(-getContinuousRecordNumber());
        else
            wrapped.setOriginalRecordNumber(getContinuousRecordNumber());

        item.setData(wrapped);
        return true;
    }

    /**
     * @param byteCount
     * @return
     * @throws IOException
     */
    KeyContext postReadKeyProcessing (
        final int byteCount)
        throws IOException
    {
        KeyContext kContext = null;
        try
        {
            kContext = context.keyHelper.extractKey(row, getContinuousRecordNumber());
        } catch (final Exception e)
        {
            throw new IOException(e);
        }
        return kContext;
    }

    void preSelectionExtract (final int byteCount) throws Exception
    {
        context.columnHelper.extract(context, row, getContinuousRecordNumber(), byteCount, getCachedEquations());
    }

    boolean recordLengthOK (@SuppressWarnings("unused")
    final int byteCount)
    {
        return true;
    }

    public void reset () throws IOException, ParseException
    {
        initialize();
    }

    void setContinuousRecordNumber (final long p_continuousRecordNumber)
    {
        this.continuousRecordNumber = p_continuousRecordNumber;
    }

    void setThisFileRecordNumber (final long p_thisFileRecordNumber)
    {
        this.thisFileRecordNumber = p_thisFileRecordNumber;
    }
}
