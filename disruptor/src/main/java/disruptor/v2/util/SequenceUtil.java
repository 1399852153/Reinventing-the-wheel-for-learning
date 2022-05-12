package disruptor.v2.util;

import disruptor.v2.SequenceV2;

import java.util.List;

public class SequenceUtil {

    public static long getMinimumSequence(long minimumSequence, List<SequenceV2> dependentSequence){
        for (SequenceV2 sequence : dependentSequence) {
            long value = sequence.getRealValue();
            minimumSequence = Math.min(minimumSequence, value);
        }

        return minimumSequence;
    }

    public static long getMinimumSequence(List<SequenceV2> dependentSequence){
        return getMinimumSequence(Long.MAX_VALUE,dependentSequence);
    }
}
