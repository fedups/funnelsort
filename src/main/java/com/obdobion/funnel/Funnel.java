package com.obdobion.funnel;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obdobion.argument.type.WildFiles;
import com.obdobion.funnel.parameters.FunnelContext;
import com.obdobion.funnel.provider.FunnelInternalNodeProvider;
import com.obdobion.funnel.segment.SegmentedPublisherAndProvider;
import com.obdobion.funnel.segment.SourceProxyRecord;

/**
 * This class provides the controller of the funnel (sort / merge) processing.
 * The main loop (Funnel#sort ) is located in this class but most of the
 * processing is encapsulated in the classes related to the providers and
 * publishers.
 * <p>
 * A {@link com.obdobion.funnel.FunnelDataProvider} provides rows in an unsorted
 * order. These rows are deposited into the top of the funnel. The funnel is
 * funnel#shake and the rows drip out the bottom, one at a time, in sorted
 * order. As they exit the bottom of the funnel the rows are handed off to a
 * {@link com.obdobion.funnel.FunnelDataPublisher} . The publisher is
 * responsible for writing the sorted rows to the output destination.
 * <p>
 * The funnel tip is index 0 in the array. The largest row has the largest
 * indexes decreasing from left to right.
 *
 * <pre>
 * 6   5   4   3
 *   2       1
 *       0
 * </pre>
 * <p>
 * The entire funnel is populated by instances of
 * {@link com.obdobion.funnel.FunnelItem}. These are static and hold the current
 * state of the specific node in the funnel. Data is assigned to these nodes
 * throughout the process. Every FunnelItem has a FunnelDataProvider associated
 * with it. The top level of the funnel is assign a provider that typically gets
 * its rows from an original data source if in the first pass or a segment on
 * every other pass.
 * <p>
 * The maximum size of the funnel top is specified in a power of 2. For
 * instance, 16 would yield a top level of 65536 rows. The input provider will
 * lay down data rows across this top level and then pause until all of them
 * exit the bottom. This "phase" produces a single segment during the first
 * pass. If there are more than 65k rows then the input provider will lay down
 * another top row for sorting. This is done in phases like this so that it is
 * guaranteed to produce a full top row of sorted items in each phase. (A phase
 * is known to be a population of the top rows and the resulting segment that is
 * created. A pass is known to be an iteration over the entire data source that
 * may produce multiple segments.)
 * <p>
 * Funnel is also a tag sort. Tags are ripped off of the row when the input is
 * provided. The sort is then on this
 * {@link com.obdobion.funnel.segment.SourceProxyRecord} in order to reduce
 * memory requirements and disk work file size (space and IO time). *
 *
 * @author Chris DeGreef fedupforone@gmail.com
 */
public class Funnel
{
    /** Constant <code>SYS_RECORDSIZE="recordsize"</code> */
    public static final String        SYS_RECORDSIZE   = "recordsize";
    /** Constant <code>SYS_RECORDNUMBER="recordnumber"</code> */
    public static final String        SYS_RECORDNUMBER = "recordnumber";

    static Logger                     logger           = LoggerFactory.getLogger(Funnel.class.getName());
    /** Constant <code>ByteFormatter</code> */
    static final public DecimalFormat ByteFormatter    = new DecimalFormat("###,###,###,###");
    /**
     * The maximum depth is the highest (number of levels) the funnel can be.
     * This is somewhat arbitrary and can be increased if there is sufficient
     * memory.
     */
    static final public int           MAXIMUM_DEPTH    = 16;
    /**
     * the depth that will be used if none is provided on the constructor.
     */
    static final public int           DEFAULT_DEPTH    = 6;

    /**
     * Sort an input stream into an output stream according to the command line
     * arguments. A {@link com.obdobion.funnel.parameters.FunnelContext} handles
     * the parsing of this command line and encapsulates all of the sorting
     * requirements. A {@link com.obdobion.funnel.orderby.KeyHelper} is created
     * for handling the creation of keys from each row that is provided.
     * Finally, a set of {@link com.obdobion.funnel.FunnelDataProvider} /
     * {@link com.obdobion.funnel.FunnelDataPublisher} instances are created and
     * the Funnel#sort process is started.
     *
     * @param args a {@link java.lang.String} object.
     * @throws Exception if any.
     * @param cfg a {@link com.obdobion.funnel.AppContext} object.
     * @return a {@link com.obdobion.funnel.parameters.FunnelContext} object.
     * @throws java.lang.Throwable if any.
     */
    static public FunnelContext sort(final AppContext cfg, final String... args)
            throws Throwable
    {
        FunnelContext context = null;

        try
        {
            context = new FunnelContext(cfg, args);
            if (context.isUsageRun())
            {
                logger.info("usage only");
                return context;
            }
            if (context.isVersion())
            {
                logger.info("version check only");
                return context;
            }
            if (context.isSyntaxOnly())
            {
                logger.info("syntax check complete, processing stopped");
                System.out.println("Syntax only run - OK");
                return context;
            }

            final Funnel funnel = new Funnel(context);

            if (context.isMultisourceInput() && context.isInPlaceSort())
            {
                /*
                 * The replace option with multiple input files causes each file
                 * to be sorted on its own.
                 */
                final WildFiles inputFiles = context.getInputFiles();
                for (int fIdx = 0; fIdx < inputFiles.files().size(); fIdx++)
                {
                    context.setInputFiles(new WildFiles(inputFiles.files().get(fIdx)));
                    if (fIdx != 0)
                        /*
                         * All subsequent processing after the first must issue
                         * a reset after the file has been swapped out to the
                         * next one.
                         */
                        funnel.reset();
                    funnel.process();
                }
            } else
                /*
                 * All other combinations of options can be handled with a
                 * single call to the process.
                 */
                funnel.process();

            logger.info("Counters input({}) selected({}) duplicates({}) output({})", context
                    .getRecordCount(), context.getRecordCount() - context.getUnselectedCount(), context
                            .getDuplicateCount(),
                    context.getWriteCount());

            logger.debug("{} funnel nodes, {} rows per phase", funnel.items.length, funnel.maxSorted);
            logger.debug("{} source proxies cached in core", SourceProxyRecord.AvailableInstances.size());
            logger.debug("{} available processors", Runtime.getRuntime().availableProcessors());
            logger.debug("memory used({}) free({}) total({}) max({})", ByteFormatter
                    .format(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                    .trim(), ByteFormatter.format(Runtime.getRuntime().freeMemory()).trim(), ByteFormatter
                            .format(Runtime.getRuntime().totalMemory())
                            .trim(),
                    ByteFormatter.format(Runtime.getRuntime().maxMemory()).trim());

        } catch (final Exception e)
        {
            if (logger.isDebugEnabled())
                logger.error(e.getMessage(), e);
            else
                logger.error(e.getMessage());
            throw e;
        } finally
        {
            logger.info("================= END ====================");
        }
        return context;
    }

    final FunnelContext        context;

    /**
     * The entry row (where data enters the funnel) is the top row. The indexing
     * of the funnel is in a 1 dimensional array where the 0th element is the
     * exit of the funnel. entryRowStart is the index of the first FunnelItem in
     * the top row.
     */
    final int                  entryRowStart;

    /**
     * The entry row (where data enters the funnel) is the top row. The indexing
     * of the funnel is in a 1 dimensional array where the 0th element is the
     * exit of the funnel. entryRowEnd is the index of the last FunnelItem in
     * the top row.
     */
    final int                  entryRowEnd;
    /**
     * Items is the funnel. Index 0 is the exit point.
     */
    private final FunnelItem[] items;
    /**
     * This is the size of the top row. It is the maximum number of rows that
     * can be sorted into a segment during the 1st pass of the data.
     */
    final int                  maxSorted;

    /**
     * Create the memory layout for the sort. Compute the important indexes once
     * and store them so that time is not wasted in powerful math functions
     * while the sort is in progress.
     *
     * @param _context a {@link com.obdobion.funnel.parameters.FunnelContext}
     *            object.
     */
    public Funnel(final FunnelContext _context)
    {
        assert _context.getDepth() <= MAXIMUM_DEPTH : "depth can not exceed " + MAXIMUM_DEPTH;
        assert _context.getDepth() > 0 : "depth must be > 0";

        context = _context;
        items = new FunnelItem[(1 << _context.getDepth()) - 1];
        entryRowStart = (1 << _context.getDepth()) - 2;
        entryRowEnd = (1 << (_context.getDepth() - 1)) - 1;
        maxSorted = 1 << (_context.getDepth() - 1);
    }

    /**
     * Computes the left of two nodes that provide input for the provided node's
     * index.
     *
     * @param winnersCircle
     * @return
     */
    FunnelItem contestantOne(final int winnersCircle)
    {
        final int c = winnersCircle * 2 + 2;
        if (c > entryRowStart)
            return null;
        return getItems()[c];
    }

    /**
     * Computes the right of two nodes that provide input for the provided
     * node's index.
     *
     * @param winnersCircle
     * @return
     */
    FunnelItem contestantTwo(
            final int winnersCircle)
    {
        final int c = winnersCircle * 2 + 1;
        if (c > entryRowStart)
            return null;
        return getItems()[c];
    }

    /**
     * <p>
     * entryRowEnd.
     * </p>
     *
     * @return a int.
     */
    public int entryRowEnd()
    {
        return entryRowEnd;
    }

    /**
     * <p>
     * entryRowStart.
     * </p>
     *
     * @return a int.
     */
    public int entryRowStart()
    {
        return entryRowStart;
    }

    FunnelItem[] getItems()
    {
        return items;
    }

    /**
     * Set each node in the funnel to initial values. This is done between
     * passes.
     *
     * @param phase
     */
    void initializePhase(
            final long phase)
    {
        for (final FunnelItem item : getItems())
        {
            item.setEndOfData(false);
            item.setData(null);
            /*
             * This should cause any provider to give up a row.
             */
            item.setPhase(-1);
        }
    }

    /**
     * The maximum is the size of the entry row in the funnel. And this just
     * applies to the first pass.
     *
     * @return a int.
     */
    public int maximumGuaranteedNumberOfSortableItems()
    {
        return maxSorted;
    }

    /**
     * The funnel is made up of only instances of {@link FunnelItem}. The
     * parameter {@link FunnelDataProvider} is attached to each of the top row
     * (entry row) nodes. When the node needs a data row it will ask the
     * provider for one. The funnel is populated with new instances of
     * FunnelItem on the first time only. Every other pass only resets the
     * FunnelItem.
     * <p>
     * Other than the entry row, all other nodes in the funnel are attached with
     * a {@link FunnelInternalNodeProvider}. This queries the two nodes above it
     * to determine which one is next in sorted order. This provider is the
     * essence of the sorting process.
     *
     * @param provider
     */
    void populateFunnel(
            final FunnelDataProvider provider)
    {
        /*
         * Apply data provider to the top of the funnel.
         */
        for (int tr = entryRowStart(); tr >= entryRowEnd(); tr--)
        {
            if (getItems()[tr] != null)
                getItems()[tr].reset();
            else
                getItems()[tr] = new FunnelItem();
            provider.attachTo(getItems()[tr]);
        }
        /*
         * Apply the specific node provider for all other funnel nodes. They are
         * the same in sorting and in merging. So only create them once.
         */
        for (int tr = entryRowEnd() - 1; tr >= 0; tr--)
            if (getItems()[tr] != null)
                getItems()[tr].reset();
            else
            {
                getItems()[tr] = new FunnelItem();
                new FunnelInternalNodeProvider(this, contestantOne(tr), contestantTwo(tr)).attachTo(getItems()[tr]);
            }
    }

    /**
     * The top row is primed when the funnel is empty. These are the next data
     * rows that will be sorted. The top row is pre-filled and then allowed to
     * be completely sorted before any more data is allowed in the funnel. At
     * which time the top row will be primed again. This keeps a predictable
     * size to each segment as it is created.
     *
     * @throws ParseException
     */
    void primeTopRow(final long phase) throws IOException, ParseException
    {
        for (int tr = entryRowStart(); tr >= entryRowEnd(); tr--)
            getItems()[tr].next(phase);
    }

    /**
     * This is the main loop of Funnel sorting. It creates a funnel. Attaches
     * the provider to the top row and a publisher to the exit point. It
     * iterates over the data one pass at a time. The first pass pulls in all of
     * the data from the original source. The last pass moves all of the sorted
     * data to the specified output by using the publisher. If the sort is more
     * than a one pass sort (the number of rows exceeds the size of the entry
     * level of the funnel) a segmenting provider / publisher keeps work files
     * for the interim passes.
     *
     * @throws Exception
     */
    void process() throws Exception
    {
        assert context.provider != null : "provider must not be null";
        assert context.publisher != null : "publisher must not be null";

        int passCount = 0;

        SegmentedPublisherAndProvider segmentationHandler = null;
        FunnelDataProvider passProvider;
        FunnelDataPublisher passPublisher = null;

        long passOneRowCount = 0;

        long passStartMS = 0;
        long passInitializedMS = 0;
        long passEndMS = 0;

        long passStartNano = 0;
        long passEndNano = 0;

        while (passPublisher != context.publisher)
        {
            passStartMS = System.currentTimeMillis();
            passStartNano = System.nanoTime();

            passCount++;

            if (segmentationHandler != null)
            {
                /*
                 * every time except the first pass
                 */
                segmentationHandler.actAsProvider();
                segmentationHandler.openInput();
                passProvider = segmentationHandler;
            } else
                /*
                 * the first pass only
                 */
                passProvider = context.provider;

            if (passProvider.maximumNumberOfRows() > maximumGuaranteedNumberOfSortableItems())
            {
                /*
                 * Every time except the last pass
                 */
                segmentationHandler = new SegmentedPublisherAndProvider(context);
                // segmentationHandler.open();
                passPublisher = segmentationHandler;
            } else
                /*
                 * Last pass only
                 */
                passPublisher = context.publisher;

            populateFunnel(passProvider);
            passPublisher.openInput();

            passInitializedMS = System.currentTimeMillis();

            long phase = 1;
            FunnelItem item;
            while (true)
            {
                item = shake(phase);
                if (item == null)
                {
                    phase++;
                    initializePhase(phase);
                    primeTopRow(phase);
                    item = shake(phase);
                    if (item == null)
                        break;
                }
                final boolean inorder = passPublisher.publish(item.getData(), phase);
                if (!inorder && passPublisher == context.publisher)
                    throw new Exception("Sort failure. Check provider max rows ("
                            + context.provider.maximumNumberOfRows()
                            + ") and power ("
                            + context.getDepth()
                            + ").");
            }
            passProvider.close();
            passPublisher.close();

            passEndMS = System.currentTimeMillis();
            passEndNano = System.nanoTime();

            if (passOneRowCount == 0)
                passOneRowCount = passProvider.actualNumberOfRows();

            logger.debug("pass({}) init({}ms) io({}ms) {}({}) phases({})", passCount, passInitializedMS
                    - passStartMS, passEndMS - passInitializedMS, (passCount == 1
                            ? "rows"
                            : "segments"),
                    passProvider.actualNumberOfRows(), phase - 1);
        }
        if (passOneRowCount > 0)
        {
            final long perRowMS = (passEndMS - passStartMS) / passOneRowCount;
            final long perRowNano = (passEndNano - passStartNano) / passOneRowCount;

            if (perRowMS > 0)
                logger.debug("perRow({}ms)  rowsPerSecond({})", perRowMS, 1000L / perRowNano);
            else
                logger.debug("perRow({} nano)  rowsPerSecond({})", perRowNano, 1000000000L / perRowNano);

            if (context.comparisonCounter > 0)
                logger.debug("Average comparison count per input row: {}.  Total comparisons = {}",
                        context.comparisonCounter
                                / passOneRowCount,
                        context.comparisonCounter);
        }
    }

    private void reset() throws IOException, ParseException
    {
        for (final FunnelItem item : getItems())
            item.reset();
        /*
         * If reset is being called it is because of multiple input files with
         * the --replace option. As of this time that is the only reason so
         * there is no checking done here.
         */
        context.setOutputFile(context.getInputFile(context.inputFileIndex()));
        context.reset();
    }

    /**
     * Shake the funnel so another data row drops down into the exit point of
     * the funnel. Obviously, {@link FunnelItem#next()} is a recursive call. It
     * starts at the exit point and works its way up to the top level of the
     * funnel.
     *
     * @return the FunnelItem at the exit point of the funnel. Or null if the
     *         funnel is currently empty.
     * @throws ParseException
     */
    FunnelItem shake(final long phase) throws IOException, ParseException
    {
        if (!getItems()[0].next(phase))
            return null;
        return getItems()[0];
    }
}
