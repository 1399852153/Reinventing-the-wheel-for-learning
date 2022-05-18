package disruptor.v4.util;

import disruptor.v4.SequenceV4;

import java.util.List;

public class SequenceUtilV4 {

    public static long getMinimumSequence(long minimumSequence, List<SequenceV4> dependentSequence){
        if(dependentSequence.isEmpty()){
            return -1;
        }

        for (SequenceV4 sequence : dependentSequence) {
            long value = sequence.getRealValue();
            minimumSequence = Math.min(minimumSequence, value);
        }

        return minimumSequence;
    }

    public static long getMinimumSequence(List<SequenceV4> dependentSequence){
        return getMinimumSequence(Long.MAX_VALUE,dependentSequence);
    }
}
