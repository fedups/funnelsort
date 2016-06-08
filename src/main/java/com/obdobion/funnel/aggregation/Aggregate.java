package com.obdobion.funnel.aggregation;

import com.obdobion.algebrain.Equ;
import com.obdobion.funnel.orderby.KeyPart;
import com.obdobion.funnel.parameters.FunnelContext;

abstract public class Aggregate
{
    public static void aggregate (final FunnelContext context, final int originalRecordSize,
        final long originalRecordNumber) throws Exception
    {
        if (context.isAggregating())
        {
            loadColumnsIntoAggregateEquations(context, originalRecordSize, originalRecordNumber);
            for (final Aggregate agg : context.aggregates)
            {
                agg.update(context);
            }
        }
    }

    private static void loadColumnsIntoAggregateEquations (final FunnelContext context, final int originalRecordSize,
        final long originalRecordNumber) throws Exception
    {
        for (final Aggregate agg : context.aggregates)
        {
            if (agg.equation != null)
            {
                for (final KeyPart col : context.columnHelper.getColumns())
                    agg.equation.getSupport().assignVariable(col.columnName, col.getContents());

                agg.equation.getSupport().assignVariable("recordnumber", new Long(originalRecordNumber));
                agg.equation.getSupport().assignVariable("recordsize", new Long(originalRecordSize));
            }
        }
    }

    public static void loadValues (final FunnelContext context, final Equ[] referencesToAllOutputFormatEquations)
        throws Exception
    {
        if (context.isAggregating())
            for (final Aggregate agg : context.aggregates)
            {
                for (final Equ equ : referencesToAllOutputFormatEquations)
                {
                    equ.getSupport().assignVariable(agg.name, agg.getValueForEquations());
                }
            }
    }

    static public Aggregate newAvg ()
    {
        return new AggregateAvg();
    }

    static public Aggregate newCount ()
    {
        return new AggregateCount();
    }

    static public Aggregate newMax ()
    {
        return new AggregateMax();
    }

    static public Aggregate newMin ()
    {
        return new AggregateMin();
    }

    static public Aggregate newSum ()
    {
        return new AggregateSum();
    }

    public static void reset (final FunnelContext context)
    {
        if (context.isAggregating())
            for (final Aggregate agg : context.aggregates)
            {
                agg.reset();
            }
    }

    public String name;
    public String columnName;
    public Equ    equation;
    AggType       aggType;

    abstract Object getValueForEquations ();

    abstract void reset ();

    public boolean supportsDate ()
    {
        return true;
    }

    public boolean supportsNumber ()
    {
        return true;
    }

    abstract void update (FunnelContext context) throws Exception;
}
