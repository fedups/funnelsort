package com.obdobion.funnel.parameters;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obdobion.algebrain.Equ;
import com.obdobion.argument.ByteCLA;
import com.obdobion.argument.CmdLine;
import com.obdobion.argument.CmdLineCLA;
import com.obdobion.argument.ICmdLine;
import com.obdobion.argument.WildFiles;
import com.obdobion.argument.input.CommandLineParser;
import com.obdobion.argument.input.IParserInput;
import com.obdobion.funnel.AppContext;
import com.obdobion.funnel.FunnelDataProvider;
import com.obdobion.funnel.FunnelDataPublisher;
import com.obdobion.funnel.aggregation.Aggregate;
import com.obdobion.funnel.aggregation.AggregateCount;
import com.obdobion.funnel.columns.ColumnHelper;
import com.obdobion.funnel.columns.FormatPart;
import com.obdobion.funnel.columns.HeaderHelper;
import com.obdobion.funnel.columns.HeaderOutHelper;
import com.obdobion.funnel.columns.OutputFormatHelper;
import com.obdobion.funnel.orderby.Filler;
import com.obdobion.funnel.orderby.KeyDirection;
import com.obdobion.funnel.orderby.KeyHelper;
import com.obdobion.funnel.orderby.KeyPart;
import com.obdobion.funnel.orderby.KeyType;
import com.obdobion.funnel.provider.AbstractInputCache;
import com.obdobion.funnel.provider.ProviderFactory;
import com.obdobion.funnel.publisher.PublisherFactory;

/**
 * @author Chris DeGreef
 *
 */
public class FunnelContext
{
    static final private Logger logger = LoggerFactory.getLogger(FunnelContext.class);

    static private void defineAggregateAverageSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k avg -m1 --var aggregates "
            + " --factorym " + Aggregate.class.getName() + ".newAvg"
            + " -h 'A list of columns that will be analyzed for their respective average values per unique sort key'");

        defineKeyNamePositional(def, false);
        defineAggregateName(def);
        defineAggregateEqu(def);

        def.add("-tEnd -k avg");
    }

    static private void defineAggregateCountSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k count -m1 --var aggregates "
            + " --factorym " + Aggregate.class.getName() + ".newCount"
            + " -h 'Count the number of records per unique sort key'");
        defineAggregateName(def);
        def.add("-tEnd -k count");
    }

    static private void defineAggregateEqu (
        final ArrayList<String> def)
    {
        def.add("-tequ -k e equation --var equation -h'Used instead of a column name.'");
    }

    static private void defineAggregateMaxSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k max -m1 --var aggregates "
            + " --factorym " + Aggregate.class.getName() + ".newMax"
            + " -h 'A list of columns that will be analyzed for their respective maximum values per unique sort key'");

        defineKeyNamePositional(def, false);
        defineAggregateName(def);
        defineAggregateEqu(def);

        def.add("-tEnd -k max");
    }

    static private void defineAggregateMinSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k min -m1 --var aggregates "
            + " --factorym " + Aggregate.class.getName() + ".newMin"
            + " -h 'A list of columns that will be analyzed for their respective minimum values per unique sort key'");

        defineKeyNamePositional(def, false);
        defineAggregateName(def);
        defineAggregateEqu(def);

        def.add("-tEnd -k min");
    }

    static private void defineAggregateName (
        final ArrayList<String> def)
    {
        def.add("-tString -k n name -r --var name -h'A name for this aggregate so that it can be referenced.'");
    }

    static private void defineAggregateSumSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k sum -m1 --var aggregates "
            + " --factorym " + Aggregate.class.getName() + ".newSum"
            + " -h 'A list of columns that will be individually summed per unique sort key'");

        defineKeyNamePositional(def, false);
        defineAggregateName(def);
        defineAggregateEqu(def);

        def.add("-tEnd -k sum");
    }

    static private void defineCacheInput (
        final List<String> def)
    {
        def.add("-tBoolean -k noCacheInput --camelCaps --var noCacheInput --def false -h 'Caching the input file into memory is faster.  This will turn off the feature.'");
    }

    static private void defineCacheWork (
        final List<String> def)
    {
        def.add("-tBoolean -k diskWork --camelCaps  --var diskWork --def false -h 'Work files are stored on disk.  The amount of memory required to hold work areas in memory is about (2 * (keySize + 24)).'");
    }

    static private void defineColumnCSVField (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k f field --var csvFieldNumber -h'If this is a CSV file then use this instead of offset and length.  The first field is field #1 (not zero).' --range 1");
    }

    static private void defineColumnFormat (
        final ArrayList<String> def)
    {
        def.add("-tString -k d format --var parseFormat --case -h'The parsing format for converting the contents of the key in the file to an internal representation. Use Java SimpleDateFormat rules for making the format.'");
    }

    static private void defineColumnLength (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k l length --var length --def "
            + KeyHelper.MAX_KEY_SIZE
            + " -h'The length of the key in bytes.' --range 1 "
            + KeyHelper.MAX_KEY_SIZE);
    }

    /*
     * The name will always be lower case.
     */
    static private void defineColumnName (
        final ArrayList<String> def)
    {
        def.add("-tString -k n name --var columnName -h'A name for this column / key so that it can be referenced.'");
    }

    static private void defineColumnOffset (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k o offset --var offset --def -1 -h'The zero relative offset from the beginning of a row.  This will be computed, if not specified, to be the location of the previous column plus the length of the previous column.  Most often this parameter is not needed.' --range 0");
    }

    static private void defineColumnsInSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k columnsIn --camelCaps  -m1 --var inputColumnDefs --factoryMethod "
            + KeyType.class.getName()
            + ".create --factoryA '--type' -h 'Column definitions defining the input file layout.'");
        defineColumnName(def);
        defineColumnType(def);
        defineColumnCSVField(def);
        defineColumnOffset(def);
        defineColumnLength(def);
        defineColumnFormat(def);
        def.add("-tEnd -k columnsIn");
    }

    static private void defineColumnType (
        final ArrayList<String> def)
    {
        def.add("-tEnum -k t type -p --var typeName -r --case -h'The data type of the key in the file.' --case --enumList "
            + KeyType.class.getName());
    }

    static private void defineCopyOrder (
        final ArrayList<String> def)
    {
        def.add("-tEnum -k c copy --var copyOrder --def "
            + CopyOrder.ByKey.name()
            + " -h 'Defines the process that will take place on the input.' --case --enumlist "
            + CopyOrder.class.getName());
    }

    static private void defineCsvCommentMarker (final ArrayList<String> def)
    {
        def.add("-tByte -k c commentMarker --camelCaps  --var commentMarker -h 'Sets the comment start marker of the format to the specified character. Note that the comment start character is only recognized at the start of a line.'");
    }

    static private void defineCsvEscape (final ArrayList<String> def)
    {
        def.add("-tByte -k x escape  --var escape -h 'Sets the escape character of the format to the specified character.'");
    }

    static private void defineCsvFieldDelimiter (final ArrayList<String> def)
    {
        def.add("-tByte -k d delimiter  --var delimiter -h 'Sets the delimiter of the format to the specified character.'");
    }

    static private void defineCsvHeader (
        final ArrayList<String> def)
    {
        def.add("-tBoolean -k h header  --var header -h'Skip over the first line for sorting and just write it to the beginning of the output file.'");
    }

    static private void defineCsvIgnoreEmptyLines (final ArrayList<String> def)
    {
        def.add("-tBoolean -k e ignoreEmptyLines --camelCaps  --var ignoreEmptyLines -h 'Sets the empty line skipping behavior of the format to true.'");
    }

    static private void defineCsvIgnoreSurroundingSpaces (final ArrayList<String> def)
    {
        def.add("-tBoolean -k s ignoreSurroundingSpaces --camelCaps  --var ignoreSurroundingSpaces -h 'Sets the trimming behavior of the format to true.'");
    }

    static private void defineCSVInSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k csv --var csv -h 'The definition of the CSV file being read as input.  Using this indicates that the input is in fact a CSV file and the columns parameter must use the --field arguments.'");

        defineCsvPredefinedFormat(def);
        defineCsvHeader(def);
        defineCsvCommentMarker(def);
        defineCsvFieldDelimiter(def);
        defineCsvEscape(def);
        defineCsvIgnoreEmptyLines(def);
        defineCsvIgnoreSurroundingSpaces(def);
        defineCsvNullString(def);
        defineCsvQuote(def);

        def.add("-tEnd -k csv");
    }

    static private void defineCsvNullString (final ArrayList<String> def)
    {
        def.add("-tString -k n nullString --camelCaps  --var nullString -h 'Converts strings equal to the given nullString to null when reading records.'");
    }

    static private void defineCsvPredefinedFormat (final ArrayList<String> def)
    {
        def.add("-tEnum -k f --var predefinedFormat -p --def "
            + CSVFormat.Predefined.Default.name()
            + " -h'A predefined way to parse the CSV input.  Other parameters may override the specifics of this definition.' "
            + " --case --enumList "
            + CSVFormat.Predefined.class.getName());
    }

    static private void defineCsvQuote (final ArrayList<String> def)
    {
        def.add("-tByte -k q quote --var quote -h 'Sets the quoteChar of the format to the specified character.'");
    }

    static private void defineDuplicateHandling (
        final ArrayList<String> def)
    {
        def.add("-tEnum -k d duplicate  --var duplicateDisposition --def "
            + DuplicateDisposition.Original.name()
            + " -h 'Special handling of duplicate keyed rows.' --case --enumlist "
            + DuplicateDisposition.class.getName());
    }

    static private void defineFixedLengthIn (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k fixedIn --camelCaps --var fixedRecordLengthIn -h 'The record length in a fixed record length file.' --ran 1 4096");
    }

    static private void defineFixedLengthOut (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k fixedOut --camelCaps --var fixedRecordLengthOut -h 'The record length in a fixed record length file.  This is used to change an output file into a fixed format.  It is not necessary if --fixedIn is specified.' --ran 1 4096");
    }

    static private void defineFormatEqu (
        final ArrayList<String> def)
    {
        def.add("-tequ -k e equation  --var equation -h'Used instead of a column name, this will be evaluated with the result written to the output.'");
    }

    static private void defineFormatFiller (
        final ArrayList<String> def)
    {
        def.add("-tByte -k f filler --var filler -h'The trailing filler character to use for a short field.'");
    }

    static private void defineFormatFormat (
        final ArrayList<String> def)
    {
        def.add("-tString -k d format --var format --case -h'The format for converting the contents of the data to be written. Use Java Formatter rules for making the format.  The format must match the type of the data.'");
    }

    static private void defineFormatOutSubParser (final ArrayList<String> def)
    {
        def.add("-tBegin -k formatOut --camelCaps  -m1 --var formatOutDefs --class com.obdobion.funnel.columns.FormatPart -h 'Column references defining the output file layout.'");
        defineKeyNamePositional(def, false);
        defineFormatEqu(def);
        defineFormatType(def);
        defineFormatFormat(def);
        defineColumnLength(def);
        defineFormatSize(def);
        defineColumnOffset(def);
        defineFormatFiller(def);
        def.add("-tEnd -k formatOut");
    }

    static private void defineFormatSize (
        final ArrayList<String> def)
    {
        def.add("-tInteger -k s size --var size --def "
            + KeyHelper.MAX_KEY_SIZE
            + " -h'The number of characters this field will use on output.' --range 1 "
            + KeyHelper.MAX_KEY_SIZE);
    }

    static private void defineFormatType (
        final ArrayList<String> def)
    {
        def.add("-tEnum -k t type -p --var typeName --case -h'The data type to be written.  Defaults to the columnIn data type.' --case --enumList "
            + KeyType.class.getName());
    }

    static private void defineHeaderInSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k headerIn --camelCaps  -m1 --var headerInDefs --factoryMethod "
            + KeyType.class.getName()
            + ".create --factoryA '--type' -h 'Column definitions defining the file header layout.'");
        defineColumnName(def);
        defineColumnType(def);
        defineColumnOffset(def);
        defineColumnLength(def);
        defineColumnFormat(def);
        def.add("-tEnd -k headerIn");
    }

    static private void defineHeaderOutSubParser (final ArrayList<String> def)
    {
        def.add("-tBegin -k headerOut --camelCaps  -m1 --var headerOutDefs --class com.obdobion.funnel.columns.FormatPart -h 'Column references defining the output file header layout.'");
        defineKeyNamePositional(def, false);
        defineFormatEqu(def);
        defineFormatType(def);
        defineFormatFormat(def);
        defineColumnLength(def);
        defineFormatSize(def);
        defineColumnOffset(def);
        defineFormatFiller(def);
        def.add("-tEnd -k headerOut");
    }

    /**
     * This only allows references to previously defined column names.
     *
     * @param def
     */
    static private void defineHexDumpSubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k hexDump --camelCaps  -m1 --var hexDumps -h 'Columns that will be shown in hex format.'");
        defineKeyNamePositional(def, false);
        def.add("-tEnd -k hexDump");
    }

    static private void defineInPlaceSort (
        final ArrayList<String> def)
    {
        def.add("-tBoolean -k r replace --var inPlaceSort -h 'Overwrite the input file with the results.  --outputFile is not required with this parameter.  --outputFile is assumed.'");
    }

    static private void defineInputFile (
        final ArrayList<String> def)
    {
        def.add("-tWildFile -k inputFileName --camelCaps  --var inputFiles -m1 -ph 'The input file or files to be processed.  Wild cards are allowed in the filename only and the path (** indicates multiple path segments).  Sysin is assumed if this parameter is not provided.' --case");
    }

    static private void defineKeyDirection (
        final ArrayList<String> def)
    {
        def.add("-tEnum -k d direction  --var direction -p --def "
            + KeyDirection.ASC.name()
            + " -h'The direction of the sort for this key. AASC and ADESC are absolute values of the key - the case of letters would not matter and the sign of numbers would not matter.' "
            + " --case --enumList "
            + KeyDirection.class.getName());
    }

    /*
     * The name will always be lower case.
     */
    static private void defineKeyNamePositional (
        final ArrayList<String> def, final boolean required)
    {
        def.add("-tString -k columnName --camelCaps  --var columnName --pos -h'A previously defined column name.'"
            + (required
                    ? " --req "
                    : ""));
    }

    static private void defineMaxRows (
        final ArrayList<String> def)
    {
        def.add("-tLong -krowMax --camelCaps  --var maximumNumberOfRows --range 2 --def "
            + Long.MAX_VALUE
            + " -h 'Used for variable length input, estimate the number of rows.  Too low could cause problems.'");
    }

    /**
     * This only allows references to previously defined column names.
     *
     * @param def
     */
    static private void defineOrderBySubParser (
        final ArrayList<String> def)
    {
        def.add("-tBegin -k orderBy --camelCaps  -m1 --var orderBys -h 'The sort keys defined from columns.'");
        defineKeyNamePositional(def, true);
        defineKeyDirection(def);
        def.add("-tEnd -k orderBy");
    }

    static private void defineOutputFile (
        final ArrayList<String> def)
    {
        def.add("-tFile -ko outputFileName --camelCaps  --var outputFile --case -h 'The output file to be written.  Sysout is assumed if this parameter is not provided.  The same name as the input file is allowed.'");
    }

    static private void definePower (
        final ArrayList<String> def)
    {
        def.add("-tInteger -kpower  --var depth --def 16 --range 2 16 -h 'The depth of the funnel.  The bigger this number is, the more memory will be used.  This is computed when --max or -f is specified.'");
    }

    static private void defineStopWhen (
        final ArrayList<String> def)
    {
        def.add("-tequ -k s stopWhen --camelCaps  --var stopEqu -m1 -h 'The sort will stop reading input when this equation returns TRUE.  See \"Algebrain\" for details.  Columns are used as variables in this Algebrain equation.'");
    }

    static private void defineSyntaxOnly (
        final ArrayList<String> def)
    {
        def.add("-tBoolean -k syntaxOnly --camelCaps  --var syntaxOnly -h 'Check the command - will not run'");
    }

    static private void defineVariableLengthIn (
        final ArrayList<String> def)
    {
        final StringBuilder bytes = new StringBuilder();
        for (int b = 0; b < System.getProperty("line.separator").getBytes().length; b++)
        {
            bytes.append(" ");
            bytes.append(ByteCLA.ByteLiteral[System.getProperty("line.separator").getBytes()[b]]);
        }

        def.add("-tByte -k variableInput --camelCaps --var endOfRecordDelimiterIn -m1 -h 'The byte(s) that end each line in a variable length record file.' --def "
            + bytes.toString());
    }

    static private void defineVariableLengthOut (
        final ArrayList<String> def)
    {
        def.add("-tByte -k variableOutput --camelCaps --var endOfRecordDelimiterOut -m1 -h 'The byte(s) that end each line in a variable length record file.  This will be used to write the output file as a variable length file.  If this is not specified then the --variableInput value will be used.'");
    }

    static private void defineVersion (
        final ArrayList<String> def)
    {
        def.add("-tBoolean -kv version  --var version -h 'Display the version of Funnel'");
    }

    static private void defineWhere (
        final ArrayList<String> def)
    {
        def.add("-tequ -k w where  --var whereEqu -m1 -h 'Rows that evaluate to TRUE are selected for Output.  See \"Algebrain\" for details.  Columns are used as variables in this Algebrain equation.'");
    }

    static private void defineWorkDirectory (
        final ArrayList<String> def)
    {
        def.add("-tFile -k workDirectory --camelCaps  --var workDirectory --case --def "
            + System.getProperty("java.io.tmpdir")
            + " -h 'The directory where temp files will be handled.'");
    }

    static private void showSystemParameters ()
    {
        @SuppressWarnings("unchecked")
        final Enumeration<String> pEnumerator = (Enumeration<String>) System.getProperties().propertyNames();
        while (pEnumerator.hasMoreElements())
        {
            final String name = pEnumerator.nextElement();
            // if ("java.library.path".equalsIgnoreCase(name)
            // || "java.endorsed.dirs".equalsIgnoreCase(name)
            // || "sun.boot.library.path".equalsIgnoreCase(name)
            // || "java.class.path".equalsIgnoreCase(name)
            // || "java.home".equalsIgnoreCase(name)
            // || "java.ext.dirs".equalsIgnoreCase(name)
            // || "sun.boot.class.path".equalsIgnoreCase(name))
            // continue;

            if ("line.separator".equalsIgnoreCase(name))
            {
                final byte[] ls = System.getProperties().getProperty(name).getBytes();
                if (ls.length == 1)
                    logger.debug("JVM: {}={}", name, ls[0]);
                else
                    logger.debug("JVM: {}={} {}", name, ls[0], ls[1]);
                continue;
            }
            if ("java.version".equalsIgnoreCase(name))
            {
                logger.debug("Java version: {}", System.getProperties().getProperty(name));
                continue;
            }
            logger.trace("JVM: {}={}", name, System.getProperties().getProperty(name));
        }
    }

    final public CmdLine        parser;
    String[]                    args;

    public List<KeyPart>        keys;
    public OrderBy[]            orderBys;

    public List<KeyPart>        inputColumnDefs;
    public List<FormatPart>     formatOutDefs;

    public List<KeyPart>        headerInDefs;
    public List<FormatPart>     headerOutDefs;

    public HexDump[]            hexDumps;

    public Equ[]                whereEqu;
    public Equ[]                stopEqu;

    private int                 inputFileIndex;
    public WildFiles            inputFiles;
    public File                 outputFile;
    public int                  fixedRecordLengthIn;
    public int                  fixedRecordLengthOut;
    public long                 maximumNumberOfRows;
    public int                  depth;
    public CopyOrder            copyOrder;
    public DuplicateDisposition duplicateDisposition;
    public File                 workDirectory;
    public String               specDirectory;
    public boolean              inPlaceSort;
    public boolean              version;
    public FunnelDataProvider   provider;
    public FunnelDataPublisher  publisher;
    public AbstractInputCache   inputCache;
    public boolean              noCacheInput;
    public boolean              diskWork;
    public KeyHelper            keyHelper;
    public OutputFormatHelper   formatOutHelper;
    public HeaderOutHelper      headerOutHelper;
    public ColumnHelper         columnHelper;
    public HeaderHelper         headerHelper;
    public byte[]               endOfRecordDelimiterIn;
    public byte[]               endOfRecordDelimiterOut;
    public CSVDef               csv;
    public boolean              syntaxOnly;

    public Aggregate[]          aggregates;

    public long                 comparisonCounter;
    private long                duplicateCount;
    private long                writeCount;
    private long                unselectedCount;
    private long                recordCount;

    public FunnelContext(final AppContext cfg, final String... _args) throws IOException, ParseException
    {
        logger.info("================ BEGIN ===================");
        logger.debug("Funnel {}", cfg.version);

        parser = new CmdLine(null,
            "Funnel is a sort / copy / merge utility.\n\nVersion "
                + cfg.version
                + ".  The log4j configuration file is " + cfg.log4jConfigFileName
                + ".");

        if (cfg.specPath != null)
        {
            for (int p = 0; p < cfg.specPath.length; p++)
            {
                parser.addDefaultIncludeDirectory(new File(cfg.specPath[p]));
            }
        }

        final ArrayList<String> def = new ArrayList<>();

        defineInputFile(def);
        defineOutputFile(def);
        defineHeaderInSubParser(def);
        defineHeaderOutSubParser(def);
        defineFixedLengthIn(def);
        defineFixedLengthOut(def);
        defineInPlaceSort(def);
        defineColumnsInSubParser(def);
        defineFormatOutSubParser(def);
        defineWhere(def);
        defineStopWhen(def);
        defineVariableLengthIn(def);
        defineVariableLengthOut(def);
        defineDuplicateHandling(def);
        defineCopyOrder(def);
        defineMaxRows(def);
        defineOrderBySubParser(def);
        defineHexDumpSubParser(def);
        defineAggregateCountSubParser(def);
        defineAggregateAverageSubParser(def);
        defineAggregateMaxSubParser(def);
        defineAggregateMinSubParser(def);
        defineAggregateSumSubParser(def);
        defineCSVInSubParser(def);
        defineWorkDirectory(def);
        defineCacheInput(def);
        defineCacheWork(def);
        definePower(def);
        defineSyntaxOnly(def);
        defineVersion(def);

        parser.compile(def);

        final IParserInput cmdline = CommandLineParser.getInstance('-', _args);
        parser.parse(cmdline, this);
        if (parser.isUsageRun())
            return;
        if (version)
        {
            logger.info("version {}", cfg.version);
            System.out.println("Funnel " + cfg.version);
            return;
        }

        try
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("commandline:");
            for (final String arg : _args)
                sb.append(" ").append(arg);
            logger.info(sb.toString());

            showSystemParameters();
            postParseAnalysis();
            showParameters();
            provider = ProviderFactory.create(this);
            publisher = PublisherFactory.create(this);
            logger.debug("============= INITIALIZED ================");
        } catch (final ParseException pe)
        {
            // logger.fatal(pe.getMessage());
            pe.fillInStackTrace();
            throw pe;
        }
    }

    public Aggregate getAggregateByName (final String name)
    {
        if (aggregates != null)
            for (final Aggregate agg : aggregates)
                if (agg.name.equalsIgnoreCase(name))
                    return agg;
        return null;
    }

    public long getDuplicateCount ()
    {
        return duplicateCount;
    }

    public File getInputFile (final int fileNumber) throws ParseException, IOException
    {
        return inputFiles.files().get(fileNumber);
    }

    public long getRecordCount ()
    {
        return recordCount;
    }

    public long getUnselectedCount ()
    {
        return unselectedCount;
    }

    public long getWriteCount ()
    {
        return writeCount;
    }

    public void inputCounters (final long p_unselectedCount, final long p_recordCount)
    {
        unselectedCount += p_unselectedCount;
        recordCount += p_recordCount;

    }

    public int inputFileCount () throws ParseException, IOException
    {
        if (inputFiles == null)
            return 0;
        return inputFiles.files().size();
    }

    public int inputFileIndex ()
    {
        return inputFileIndex;
    }

    public boolean isAggregating ()
    {
        return aggregates != null && aggregates.length > 0;
    }

    public boolean isCacheInput ()
    {
        return !noCacheInput;
    }

    public boolean isCacheWork ()
    {
        return !diskWork;
    }

    public boolean isHexDumping ()
    {
        return hexDumps != null;
    }

    public boolean isInPlaceSort ()
    {
        return inPlaceSort;
    }

    public boolean isMultisourceInput () throws ParseException, IOException
    {
        return inputFiles != null && inputFiles.files().size() > 1;
    }

    public boolean isSyntaxOnly ()
    {
        return syntaxOnly;
    }

    public boolean isSysin ()
    {
        return !(parser.arg("--inputfilename").isParsed());
    }

    public boolean isSysout () throws ParseException, IOException
    {
        if (isMultisourceInput() && isInPlaceSort())
            return false;
        return outputFile == null;
    }

    public boolean isUserSpecifiedOrder ()
    {
        return orderBys == null || orderBys.length == 0;
    }

    public boolean isVariableLengthInput ()
    {
        return parser.arg("--variableIn").isParsed() || !(parser.arg("--fixedIn").isParsed());
    }

    public boolean isVariableLengthOutput ()
    {
        return parser.arg("--variableOutput").isParsed();
    }

    public void outputCounters (final long p_duplicateCount, final long p_writeCount)
    {
        duplicateCount += p_duplicateCount;
        writeCount += p_writeCount;
    }

    private void postParseAggregation () throws ParseException
    {
        if (aggregates != null)
        {
            final List<String> aggregateNamesFoundSoFar = new ArrayList<>();

            for (final Aggregate agg : aggregates)
            {
                if (aggregateNamesFoundSoFar.contains(agg.name))
                    throw new ParseException("aggregate \"" + agg.name + "\" must have a unique name", 0);
                aggregateNamesFoundSoFar.add(agg.name);

                if (agg instanceof AggregateCount)
                    continue;

                if (columnHelper.exists(agg.name))
                    throw new ParseException("aggregate \"" + agg.name + "\" is already defined as a column", 0);

                if (agg.columnName != null)
                {
                    if (!columnHelper.exists(agg.columnName))
                        throw new ParseException("aggregate \""
                            + agg.name
                            + "\" must reference a defined column: "
                            + agg.columnName, 0);

                    final KeyPart col = columnHelper.get(agg.columnName);
                    if ((col.isNumeric() && !agg.supportsNumber())
                        || (col.isDate() && !agg.supportsDate())
                        || (!col.isNumeric() && !col.isDate()))
                        throw new ParseException("aggregate \""
                            + agg.name
                            + "\" must reference a numeric or date column: "
                            + agg.columnName
                            + " ("
                            + col.typeName
                            + ")", 0);

                    if (agg.equation != null)
                        throw new ParseException("aggregate \""
                            + agg.name
                            + "\" columnName and --equ are mutually exclusive", 0);
                }
            }
        }
    }

    private void postParseAnalysis () throws ParseException, IOException
    {
        columnHelper = new ColumnHelper();
        keyHelper = new KeyHelper();
        formatOutHelper = new OutputFormatHelper(columnHelper, headerHelper);
        headerHelper = new HeaderHelper();
        headerOutHelper = new HeaderOutHelper(headerHelper);

        postParseInputFile();
        postParseHeaderIn();
        postParseHeaderOut();
        postParseInputColumns();
        postParseOrderBy();
        postParseHexDumps();
        postParseAggregation();
        postParseFormatOut();
        postParseOutputFile();
        postParseEolOut();
        postParseCSV();
        postParseFixed();
    }

    private void postParseCSV ()
    {
        /*
         * Create a CSV parser if needed.
         */
        if (parser.arg("--csv").isParsed())
        {
            csv.format = csv.predefinedFormat.getFormat();
            logger.debug("defining the CSV parser based on \"{}\"", csv.predefinedFormat.name());
            final ICmdLine csvParser = ((CmdLineCLA) parser.arg("--csv")).templateCmdLine;

            if (csvParser.arg("--commentMarker").isParsed())
                csv.format = csv.format.withCommentMarker((char) csv.commentMarker);
            if (csvParser.arg("--delimiter").isParsed())
                csv.format = csv.format.withDelimiter((char) csv.delimiter);
            if (csvParser.arg("--escape").isParsed())
                csv.format = csv.format.withEscape((char) csv.escape);
            if (csvParser.arg("--ignoreEmptyLines").isParsed())
                csv.format = csv.format.withIgnoreEmptyLines(csv.ignoreEmptyLines);
            if (csvParser.arg("--ignoreSurroundingSpaces").isParsed())
                csv.format = csv.format.withIgnoreSurroundingSpaces(csv.ignoreSurroundingSpaces);
            if (csvParser.arg("--nullString").isParsed())
                csv.format = csv.format.withNullString(csv.nullString);
            if (csvParser.arg("--quote").isParsed())
                csv.format = csv.format.withQuote((char) csv.quote);
        }
    }

    private void postParseEolOut ()
    {
        if (endOfRecordDelimiterOut == null)
            endOfRecordDelimiterOut = endOfRecordDelimiterIn;
    }

    private void postParseFixed () throws ParseException
    {
        if (fixedRecordLengthOut > 0 && isVariableLengthOutput())
            throw new ParseException("--fixedOut and --variableOutput are mutually exclusive parameters", 0);
        if (isVariableLengthOutput())
            return;
        if (fixedRecordLengthOut == 0)
            fixedRecordLengthOut = fixedRecordLengthIn;

    }

    private void postParseFormatOut () throws ParseException
    {
        if (formatOutDefs != null)
        {
            if (csv != null)
            {
                throw new ParseException("--csv and --format are mutually exclusive parameters", 0);
            }

            for (final FormatPart kdef : formatOutDefs)
            {
                try
                {
                    if (kdef.offset == -1) // unspecified
                        kdef.offset = 0;

                    if (kdef.columnName != null)
                        if (!columnHelper.exists(kdef.columnName))
                        {
                            if (!headerHelper.exists(kdef.columnName))
                            {
                                if (getAggregateByName(kdef.columnName) == null)
                                    throw new ParseException(
                                        "--formatOut must be a defined column or header: "
                                            + kdef.columnName, 0);
                                throw new ParseException(
                                    "--formatOut must be a defined column, aggregates can only be used within --equ: "
                                        + kdef.columnName, 0);
                            }
                        }
                    if (kdef.columnName != null && kdef.equation != null)
                        throw new ParseException("--formatOut columnName and --equ are mutually exclusive", 0);
                    if (kdef.format != null && kdef.equation == null)
                        throw new ParseException("--formatOut --format is only valid with --equ", 0);

                    if (kdef.equation != null)
                    {
                        if (kdef.length == 255)
                            throw new ParseException("--formatOut --length is required when --equ is specified", 0);
                    }

                    formatOutHelper.add(kdef);
                } catch (final Exception e)
                {
                    throw new ParseException(e.getMessage(), 0);
                }
            }
        }
    }

    private void postParseHeaderIn () throws ParseException
    {
        headerHelper.setWaitingForInput(false);
        if (headerInDefs != null)
        {
            headerHelper.setWaitingForInput(true);

            // headerInDefs.size() > 1
            // || (headerInDefs.get(0).columnName != null ||
            // headerInDefs.get(0).equation != null)

            /*
             * This may be overridden in the postParseHeaderOut method.
             */
            headerOutHelper.setWaitingToWrite(true);

            KeyPart previousColDef = null;
            for (final KeyPart colDef : headerInDefs)
            {
                try
                {
                    /*
                     * Provide a default length when the format is specified and
                     * the length is not.
                     */
                    if (colDef.length == KeyHelper.MAX_KEY_SIZE && colDef.parseFormat != null
                        && colDef.parseFormat.length() > 0)
                    {
                        colDef.length = colDef.parseFormat.length();
                        logger.debug("column \"{}\" length set to {} because of format", colDef.columnName, colDef.length);
                    }
                    if (csv != null)
                        throw new ParseException("headerIn not supported for csv files", 0);

                    if (colDef.offset == -1) // unspecified
                    {
                        if (previousColDef != null)
                            colDef.offset = previousColDef.offset + previousColDef.length;
                        else
                            colDef.offset = 0;
                    }

                    if (!(colDef instanceof Filler))
                    {
                        if (headerHelper.exists(colDef.columnName))
                            throw new ParseException("headerIn must be unique: " + colDef.columnName, 0);
                        headerHelper.add(colDef);
                    }
                    previousColDef = colDef;

                } catch (final Exception e)
                {
                    throw new ParseException(e.getMessage(), 0);
                }
            }
        }
    }

    private void postParseHeaderOut () throws ParseException
    {
        if (headerOutDefs != null)
        {
            if (csv != null)
            {
                throw new ParseException("--csv and --headerOut are mutually exclusive parameters", 0);
            }
            /*
             * --headerOut(), no args, means to suppress the headerIn from being
             * written.
             */
            headerOutHelper.setWaitingToWrite(
                    headerOutDefs.size() > 1
                        || (headerOutDefs.get(0).columnName != null || headerOutDefs.get(0).equation != null));

            for (final FormatPart kdef : headerOutDefs)
            {
                try
                {
                    if (kdef.offset == -1) // unspecified
                        kdef.offset = 0;

                    if (kdef.columnName != null)
                        if (!headerHelper.exists(kdef.columnName))
                        {
                            throw new ParseException("--headerOut must be a defined headerIn: " + kdef.columnName, 0);
                        }
                    if (kdef.columnName != null && kdef.equation != null)
                        throw new ParseException("--headerOut columnName and --equ are mutually exclusive", 0);
                    if (kdef.format != null && kdef.equation == null)
                        throw new ParseException("--headerOut --format is only valid with --equ", 0);

                    if (kdef.equation != null)
                    {
                        if (kdef.length == 255)
                            throw new ParseException("--headerOut --length is required when --equ is specified", 0);
                    }

                    headerOutHelper.add(kdef);
                } catch (final Exception e)
                {
                    throw new ParseException(e.getMessage(), 0);
                }
            }
        }
    }

    private void postParseHexDumps () throws ParseException
    {
        /*
         * Convert OrderBys into sort keys
         */
        if (hexDumps != null && hexDumps.length > 0)
        {
            if (hexDumps.length == 1 && hexDumps[0].columnName == null)
                /*
                 * Full record dump
                 */
                return;

            if (aggregates != null)
                throw new ParseException("HexDump with aggregate processing is not supported", 0);
            if (!isVariableLengthOutput() && (fixedRecordLengthIn > 0 || fixedRecordLengthOut > 0))
                throw new ParseException("HexDump is only valid with variableOutput", 0);
            if (isInPlaceSort())
                throw new ParseException("HexDump is not valid with --replace", 0);

            for (final HexDump hexDump : hexDumps)
            {
                if (!columnHelper.exists(hexDump.columnName))
                    throw new ParseException("HexDump must be a defined column: " + hexDump.columnName, 0);
                final KeyPart column = columnHelper.get(hexDump.columnName);
                if (KeyType.String.name().equalsIgnoreCase(column.typeName)
                    || KeyType.Byte.name().equalsIgnoreCase(column.typeName))
                {
                    // ok
                } else
                    throw new ParseException("HexDump can only be on String or Byte columns: " + hexDump.columnName, 0);
            }
        }
    }

    private void postParseInputColumns () throws ParseException
    {
        if (inputColumnDefs != null)
        {
            KeyPart previousColDef = null;
            for (final KeyPart colDef : inputColumnDefs)
            {
                try
                {
                    /*
                     * Provide a default length when the format is specified and
                     * the length is not.
                     */
                    if (colDef.length == KeyHelper.MAX_KEY_SIZE && colDef.parseFormat != null
                        && colDef.parseFormat.length() > 0)
                    {
                        colDef.length = colDef.parseFormat.length();
                        logger.debug("column \"{}\" length set to {} because of format", colDef.columnName, colDef.length);
                    }
                    /*
                     * Compute an offset if one was not specified. But only for
                     * non-csv files since offset is not part of the csv
                     * specification.
                     */
                    if (csv == null)
                        if (colDef.offset == -1) // unspecified
                        {
                            if (previousColDef != null)
                                colDef.offset = previousColDef.offset + previousColDef.length;
                            else
                                colDef.offset = 0;
                        }
                    /*
                     * Since the parameter is 1-relative, an arbitrary decision,
                     * we have to subtract one from them before they can be
                     * used.
                     */
                    if (colDef.csvFieldNumber > 0)
                    {
                        colDef.csvFieldNumber--;
                        colDef.offset = 0;
                    }

                    if (!(colDef instanceof Filler))
                    {
                        if (headerHelper.exists(colDef.columnName))
                            throw new ParseException("columnsIn must be unique from headerIn: " + colDef.columnName, 0);
                        columnHelper.add(colDef);
                    }
                    previousColDef = colDef;

                } catch (final Exception e)
                {
                    throw new ParseException(e.getMessage(), 0);
                }
            }
        }
    }

    private void postParseInputFile () throws ParseException, IOException
    {
        if (!isSysin() && (inputFiles == null || inputFiles.files() == null || inputFiles.files().size() == 0))
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("file not found");
            if (inputFiles != null)
            {
                sb.append(": ");
                sb.append(inputFiles.toString());
            }
            throw new ParseException(sb.toString(), 0);
        }
    }

    private void postParseOrderBy () throws ParseException
    {
        /*
         * Convert OrderBys into sort keys
         */
        if (orderBys != null && orderBys.length > 0)
        {
            if (keys == null)
                keys = new ArrayList<>();
            for (final OrderBy orderBy : orderBys)
            {
                if (!columnHelper.exists(orderBy.columnName))
                    throw new ParseException("OrderBy must be a defined column: " + orderBy.columnName, 0);
                final KeyPart col = columnHelper.get(orderBy.columnName);
                final KeyPart newKey = col.newCopy();
                newKey.direction = orderBy.direction;
                keys.add(newKey);
            }
        }

        if (keys == null)
            keyHelper.setUpAsCopy(this);
        /*
         * Check for cvs keys on a non-cvs file
         */
        if (keys != null && csv == null)
            for (final KeyPart kdef : keys)
            {
                if (kdef.isCsv())
                {
                    throw new ParseException("unexpected CSV key (--field) on a non-CSV file", 0);
                }
            }
        /*
         * Check for non-cvs keys on a cvs file
         */
        if (keys != null && csv != null)
            for (final KeyPart kdef : keys)
            {
                if (!kdef.isCsv())
                {
                    throw new ParseException("only CSV keys (--field) allowed on a CSV file", 0);
                }
            }
        /*
         * Check for duplicate csv keys
         */
        if (keys != null && csv != null)
            for (final KeyPart k1 : keys)
            {
                for (final KeyPart k2 : keys)
                {
                    if (k1 != k2 && k1.csvFieldNumber == k2.csvFieldNumber)
                    {
                        throw new ParseException("sorting on the same field (--field "
                            + k2.csvFieldNumber
                            + ") is not allowed",
                            0);
                    }
                }
            }

        if (keys != null)
            for (final KeyPart kdef : keys)
            {
                try
                {
                    keyHelper.add(kdef, columnHelper);
                } catch (final Exception e)
                {
                    throw new ParseException(e.getMessage(), 0);
                }
            }
    }

    private void postParseOutputFile () throws ParseException, IOException
    {
        if (isInPlaceSort() && outputFile != null)
            throw new ParseException("--replace and --outputFile are mutually exclusive parameters", 0);

        if (isInPlaceSort() && isSysin())
            throw new ParseException("--replace requires --inputFile, redirection or piped input is not allowed", 0);

        /*
         * -r is how the input file is replaced. If we set the outputFile here
         * it then becomes impossible to sort to the command line (sysout).
         */
        if (isInPlaceSort())
            outputFile = getInputFile(0);
    }

    public void reset () throws IOException, ParseException
    {
        if (provider != null)
            provider.reset();
        if (publisher != null)
            publisher.reset();
    }

    /**
     *
     */
    void showParameters () throws IOException, ParseException
    {
        if (isSysin())
            showParametersLog(true, "input is SYSIN");
        else
            for (final File file : inputFiles.files())
                showParametersLog(true, "inputFilename = {}", file.getAbsolutePath());

        if (isCacheInput())
            showParametersLog(false, "input caching enabled");

        if (isSysout())
            showParametersLog(true, "output is SYSOUT");
        else if (isInPlaceSort())
            showParametersLog(true, "outputFilename= input file name");
        else
            showParametersLog(true, "outputFilename= {}", outputFile.getAbsolutePath());

        if (isCacheWork())
            showParametersLog(false, "work files are cached in memory");
        else
            showParametersLog(false, "work directory= {}", workDirectory.getAbsolutePath());

        if (specDirectory != null)
            showParametersLog(false, "specification include path is {}", specDirectory);

        if (fixedRecordLengthIn > 0)
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("FixedIn  = ").append(fixedRecordLengthIn);
            if (isVariableLengthOutput())
                sb.append(" adding VLR delimiters on output");
            showParametersLog(false, sb.toString());
        } else
        {
            if (maximumNumberOfRows != Long.MAX_VALUE)
                showParametersLog(false, "max rows= {}", maximumNumberOfRows);

            final StringBuilder bytes = new StringBuilder();
            bytes.append("in:");
            for (int b = 0; b < endOfRecordDelimiterIn.length; b++)
            {
                bytes.append(" ");
                bytes.append(ByteCLA.asLiteral(endOfRecordDelimiterIn[b]));
            }
            if (fixedRecordLengthOut == 0)
            {
                bytes.append(", out:");
                for (int b = 0; b < endOfRecordDelimiterOut.length; b++)
                {
                    bytes.append(" ");
                    bytes.append(ByteCLA.asLiteral(endOfRecordDelimiterOut[b]));
                }
            }

            showParametersLog(false, "End of line delimeter {}", bytes.toString());

            if (csv != null)
            {
                final StringBuilder csvMsg = new StringBuilder();
                csvMsg.append("csv: ");
                if (csv.header)
                    csvMsg.append("has header");
                else
                    csvMsg.append("no header");
                showParametersLog(false, csvMsg.toString());
            }
        }
        if (fixedRecordLengthOut > 0)
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("FixedOut = ").append(fixedRecordLengthOut);
            showParametersLog(false, sb.toString());
        }

        showParametersLog(false, "power   = {}", depth);

        if (duplicateDisposition != DuplicateDisposition.Original)
            showParametersLog(false, "dups    = {}", duplicateDisposition.name());

        for (final String colName : columnHelper.getNames())
        {
            final KeyPart col = columnHelper.get(colName);
            if (csv == null)
                showParametersLog(false, "col \"{}\" {} offset {} length {} {}",
                    col.columnName, col.typeName, col.offset, col.length,
                    (col.parseFormat == null
                            ? ""
                            : " format " + col.parseFormat));
            else
                showParametersLog(false, "col {} {} csvField {} {}",
                    col.columnName, col.typeName, col.csvFieldNumber,
                    (col.parseFormat == null
                            ? ""
                            : " format " + col.parseFormat));
        }

        for (final String colName : headerHelper.getNames())
        {
            final KeyPart col = headerHelper.get(colName);
            showParametersLog(false, "headerIn \"{}\" {} offset {} length {} {}",
                col.columnName, col.typeName, col.offset, col.length,
                (col.parseFormat == null
                        ? ""
                        : " format " + col.parseFormat));
        }

        if (aggregates != null)
            for (final Aggregate agg : aggregates)
            {
                if (agg instanceof AggregateCount)
                    showParametersLog(false, "aggregate \"count\"");
                else
                    showParametersLog(false, "aggregate \"{}\" {}",
                        agg.name,
                        (agg.columnName == null
                                ? agg.equation.toString()
                                : agg.columnName));
            }

        if (whereEqu != null)
        {
            for (final Equ equ : whereEqu)
            {
                showParametersLog(true, "where \"{}\"", equ.toString());
            }
        }

        if (stopEqu != null)
        {
            for (final Equ equ : stopEqu)
            {
                showParametersLog(true, "stopWhen \"{}\"", equ.toString());
            }
        }

        if (keys == null)
            logger.debug("process = {} order", copyOrder.name());
        else
            for (final KeyPart def : keys)
            {
                showParametersLog(false, "orderBy {} {}", def.columnName, def.direction.name());
            }

        if (formatOutDefs != null)
            for (final FormatPart outDef : formatOutDefs)
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("format ");
                if (outDef.columnName != null)
                    sb.append("\"").append(outDef.columnName).append("\"");
                if (outDef.equation != null)
                    sb.append("\"").append(outDef.equation.toString()).append("\"");
                if (outDef.typeName != null)
                    sb.append(" ").append(outDef.typeName.name());
                if (outDef.format != null)
                    sb.append(" format \"").append(outDef.format).append("\"");
                if (outDef.filler != 0x00)
                    sb.append(" fill=").append(ByteCLA.asLiteral(outDef.filler));
                if (outDef.length != 255)
                    sb.append(" length ").append(outDef.length);
                if (outDef.offset != 0)
                    sb.append(" offset ").append(outDef.offset);
                if (outDef.size != 255)
                    sb.append(" size ").append(outDef.size);

                showParametersLog(false, sb.toString());

                if (outDef.equation != null)
                {
                    try
                    {
                        logger.trace("\n{}", outDef.equation.showRPN());
                    } catch (final Exception e)
                    {
                        logger.warn("algebrain", e);
                    }
                }
            }

        if (headerOutDefs != null)
            for (final FormatPart outDef : headerOutDefs)
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("headerOut ");
                if (outDef.columnName != null)
                    sb.append("\"").append(outDef.columnName).append("\"");
                if (outDef.equation != null)
                    sb.append("\"").append(outDef.equation.toString()).append("\"");
                if (outDef.typeName != null)
                    sb.append(" ").append(outDef.typeName.name());
                if (outDef.format != null)
                    sb.append(" format \"").append(outDef.format).append("\"");
                if (outDef.filler != 0x00)
                    sb.append(" fill=").append(ByteCLA.asLiteral(outDef.filler));
                if (outDef.length != 255)
                    sb.append(" length ").append(outDef.length);
                if (outDef.offset != 0)
                    sb.append(" offset ").append(outDef.offset);
                if (outDef.size != 255)
                    sb.append(" size ").append(outDef.size);

                showParametersLog(false, sb.toString());

                if (outDef.equation != null)
                {
                    try
                    {
                        logger.trace("\n{}", outDef.equation.showRPN());
                    } catch (final Exception e)
                    {
                        logger.warn("algebrain", e);
                    }
                }
            }
    }

    private void showParametersLog (final boolean forceInfo, final String message, final Object... parms)
    {
        if (forceInfo || isSyntaxOnly())
            logger.info(message, parms);
        else
            logger.debug(message, parms);
    }

    public boolean startNextInput () throws ParseException, IOException
    {
        /*
         * Has the last input file been read? Then return false.
         */
        if (inputFileIndex() >= (inputFileCount() - 1))
            return false;
        inputFileIndex++;
        return true;
    }

    public boolean stopIsTrue () throws Exception
    {
        if (stopEqu == null)
            return false;

        for (final Equ equ : stopEqu)
        {
            /*
             * All of the stop equations must be true.
             */
            final Object result = equ.evaluate();
            if (result == null)
                return false;
            if (!(result instanceof Boolean))
                throw new Exception("--stopWhen clause must evaluate to true or false");
            if (!((Boolean) result).booleanValue())
                return false;
        }
        return true;
    }

    public boolean whereIsTrue () throws Exception
    {
        if (whereEqu == null)
            return true;

        for (final Equ equ : whereEqu)
        {
            /*
             * All of the where equations must be true.
             */
            final Object result = equ.evaluate();
            if (result == null)
                return false;
            if (!(result instanceof Boolean))
                throw new Exception("--where clause must evaluate to true or false");
            if (!((Boolean) result).booleanValue())
                return false;
        }
        return true;
    }

}