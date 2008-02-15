package de.dfki.lt.mary.unitselection.adaptation.prosody;

import de.dfki.lt.mary.util.MaryUtils;
import de.dfki.lt.signalproc.util.MathUtils;
import de.dfki.lt.signalproc.util.SignalProcUtils;

public class PitchTransformer {    
    public PitchTransformer()
    {
        
    }
    
    public double[] transform(ProsodyTransformerParams param, 
                              PitchStatisticsMapping mapping, 
                              PitchStatistics inputLocalStatistics,
                              double[] f0s,
                              double[] pscales //Possibly time-varying pitch scaling values (these are applied after all modifications as an additinal step!)
                              )
    {
        double[] targetF0s = null;

        if (f0s!=null)
        {
            int i;
            int bestIndex = -1;
            targetF0s = new double[f0s.length];
            
            PitchStatistics inputStatistics = null;
            PitchStatistics outputStatistics = null;
            
            if (param.pitchTransformationMethod<ProsodyTransformerParams.SENTENCE_MEAN)
            {
                if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_HERTZ)
                    inputStatistics = new PitchStatistics(mapping.sourceGlobalStatisticsHz);
                else if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
                    inputStatistics = new PitchStatistics(mapping.sourceGlobalStatisticsLogHz);
                else
                    System.out.println("Error! Statistics type unknown...");
                
                assert inputStatistics!=null;
                
                if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_HERTZ)
                    outputStatistics = new PitchStatistics(mapping.targetGlobalStatisticsHz);
                else if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
                    outputStatistics = new PitchStatistics(mapping.targetGlobalStatisticsLogHz);
                else
                    System.out.println("Error! Statistics type unknown...");
                
                assert outputStatistics!=null;      
            }
            else
            {
                if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_HERTZ)
                    bestIndex = findBestMatch(inputLocalStatistics, mapping.sourceLocalStatisticsHz, mapping.sourceVariancesHz);
                else if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
                    bestIndex = findBestMatch(inputLocalStatistics, mapping.sourceLocalStatisticsLogHz, mapping.sourceVariancesLogHz);
                else
                    System.out.println("Error! Statistics type unknown...");

                if (bestIndex<0)
                {
                    System.out.println("Error! No best match found...");
                    assert bestIndex>-1;
                }

                if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_HERTZ)
                {
                    inputStatistics = new PitchStatistics(mapping.sourceLocalStatisticsHz.entries[bestIndex]);
                    outputStatistics = new PitchStatistics(mapping.targetLocalStatisticsHz.entries[bestIndex]);
                }
                else if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
                {
                    inputStatistics = new PitchStatistics(mapping.sourceLocalStatisticsLogHz.entries[bestIndex]);
                    outputStatistics = new PitchStatistics(mapping.targetLocalStatisticsLogHz.entries[bestIndex]);
                }
                else
                    System.out.println("Error! Statistics type unknown...");
                
                assert inputStatistics!=null;
                assert outputStatistics!=null;
            }
            
            if (param.isUseInputMean)
                inputStatistics.mean = inputLocalStatistics.mean;
            
            if (param.isUseInputStdDev)
                inputStatistics.standardDeviation = inputLocalStatistics.standardDeviation;
            
            if (param.isUseInputRange)
                inputStatistics.range = inputLocalStatistics.range;
            
            if (param.isUseInputIntercept)
                inputStatistics.intercept = inputLocalStatistics.intercept;
            
            if (param.isUseInputSlope)
                inputStatistics.slope = inputLocalStatistics.slope;
            
            System.arraycopy(f0s, 0, targetF0s, 0, f0s.length);
            
            double minF0ScaleAllowed = -0.5;
            double maxF0ScaleAllowed = 1.5;
            double minF0ShiftAllowed = -200.0;
            double maxF0ShiftAllowed = 200.0;
            if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
            {
                targetF0s = SignalProcUtils.getLogF0s(targetF0s);
                minF0ScaleAllowed = Math.log(0.5);
                maxF0ScaleAllowed = Math.log(maxF0ScaleAllowed);
                minF0ShiftAllowed = Math.log(0.1);
                maxF0ShiftAllowed = Math.log(maxF0ShiftAllowed);
            }

            //Now, transform inputStatistics to outputStatistics
            if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_MEAN || 
                param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_MEAN)
            {
                double meanShift = outputStatistics.mean-inputStatistics.mean;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = targetF0s[i] + meanShift;
                
                System.out.println("Mean transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_STDDEV ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_STDDEV)
            {
                double scale = outputStatistics.standardDeviation/inputStatistics.standardDeviation;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = inputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                System.out.println("Std.dev. transformed -> (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_RANGE ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_RANGE)
            {
                double scale = outputStatistics.range/inputStatistics.range;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = inputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                System.out.println("Range transformed -> (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_SLOPE ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_SLOPE)
            {
                double scale = outputStatistics.slope/inputStatistics.slope;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = inputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                System.out.println("Slope transformed -> (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_INTERCEPT ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_INTERCEPT)
            {
                double newMean = (inputStatistics.mean-inputStatistics.intercept)/inputStatistics.slope;
                newMean =  outputStatistics.slope*newMean +  outputStatistics.intercept;
                
                double meanShift = newMean - inputStatistics.mean;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = targetF0s[i] + meanShift;
                
                System.out.println("Intercept transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_MEAN_STDDEV ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_MEAN_STDDEV)
            {
                double scale = outputStatistics.standardDeviation/inputStatistics.standardDeviation;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = outputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                System.out.println("Mean & Std.dev. transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift) + " (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_MEAN_SLOPE ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_MEAN_SLOPE)
            {
                double scale = outputStatistics.slope/inputStatistics.slope;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = outputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                System.out.println("Mean & Slope transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift) + " (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_INTERCEPT_STDDEV ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_INTERCEPT_STDDEV)
            {
                //First STDDEV
                double scale = outputStatistics.standardDeviation/inputStatistics.standardDeviation;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = inputStatistics.mean - inputStatistics.mean*scale;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                //Then INTERCEPT
                double newMean = (inputStatistics.mean-inputStatistics.intercept)/inputStatistics.slope;
                newMean =  outputStatistics.slope*newMean +  outputStatistics.intercept;
                
                meanShift = newMean-inputStatistics.mean;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = targetF0s[i] + meanShift;
                
                System.out.println("Intercept & Std. dev. transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift) + " (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            else if (param.pitchTransformationMethod==ProsodyTransformerParams.GLOBAL_INTERCEPT_SLOPE ||
                     param.pitchTransformationMethod==ProsodyTransformerParams.SENTENCE_INTERCEPT_SLOPE)
            {
                //First SLOPE
                double scale = outputStatistics.slope/inputStatistics.slope;
                scale = Math.max(minF0ScaleAllowed, scale);
                scale = Math.min(maxF0ScaleAllowed, scale);
                
                double meanShift = inputStatistics.mean - inputStatistics.mean*scale;
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = scale*targetF0s[i] + meanShift;
                
                //Then INTERCEPT
                double newMean = (inputStatistics.mean-inputStatistics.intercept)/inputStatistics.slope;
                newMean =  outputStatistics.slope*newMean + outputStatistics.intercept;
                
                meanShift = newMean - inputStatistics.mean;
                meanShift = Math.max(minF0ShiftAllowed, meanShift);
                meanShift = Math.min(maxF0ShiftAllowed, meanShift);
                
                for (i=0; i<targetF0s.length; i++)
                    targetF0s[i] = targetF0s[i] + meanShift;
                
                System.out.println("Intercept & Slope transformed -> (Output mean f0)-(Input mean f0)=" + String.valueOf(meanShift) + " (Output f0)/(Input f0)=" + String.valueOf(scale));
            }
            
            if (param.pitchStatisticsType==PitchStatistics.STATISTICS_IN_LOGHERTZ)
                targetF0s = SignalProcUtils.getExpF0s(targetF0s);
            
            for (i=0; i<targetF0s.length; i++)
                targetF0s[i] *= pscales[i]; 
            
            for (i=0; i<targetF0s.length; i++)
            {
                if (f0s[i]<10.0)
                    targetF0s[i] = 0.0;
                
                if (targetF0s[i]<10.0)
                    targetF0s[i] = 0.0;
            }
        }
        
        if (param.pitchTransformationMethod!=ProsodyTransformerParams.NO_TRANSFORMATION)
        {
            MaryUtils.plotZoomed(f0s, "Input", 50.0);
            MaryUtils.plotZoomed(targetF0s, "Tranformed", 50.0);
        }
        
        return targetF0s;
    }
    
    public int findBestMatch(PitchStatistics oneStatistics, PitchStatisticsCollection multipleStatistics, double[] variances)
    {
        int bestIndex = -1;
        
        if (multipleStatistics!=null && oneStatistics!=null && multipleStatistics.entries!=null)
        {
            if (multipleStatistics.entries.length==1)
                bestIndex = 0;
            else
            {
                double[] dists = new double[multipleStatistics.entries.length];
                for (int i=0; i<multipleStatistics.entries.length; i++)
                {
                    dists[i] = 0.0;
                    dists[i] += (oneStatistics.mean-multipleStatistics.entries[i].mean)*
                                (oneStatistics.mean-multipleStatistics.entries[i].mean)/variances[0];
                    dists[i] += (oneStatistics.standardDeviation-multipleStatistics.entries[i].standardDeviation)*
                                (oneStatistics.standardDeviation-multipleStatistics.entries[i].standardDeviation)/variances[1];
                    dists[i] += (oneStatistics.range-multipleStatistics.entries[i].range)*
                                (oneStatistics.range-multipleStatistics.entries[i].range)/variances[2];
                    dists[i] += (oneStatistics.intercept-multipleStatistics.entries[i].intercept)*
                                (oneStatistics.intercept-multipleStatistics.entries[i].intercept)/variances[3];
                    dists[i] += (oneStatistics.slope-multipleStatistics.entries[i].slope)*
                                (oneStatistics.slope-multipleStatistics.entries[i].slope)/variances[4];

                    dists[i] = Math.sqrt(dists[i]);
                }

                bestIndex = MathUtils.getMinIndex(dists);
            }
        }
        
        return bestIndex;
    }
}
