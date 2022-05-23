package disruptor.v5.util;

import disruptor.v5.SequenceV5;

import java.util.List;

public class SequenceUtilV5 {

    public static long getMinimumSequence(long minimumSequence, List<SequenceV5> dependentSequence){
        if(dependentSequence.isEmpty()){
            return -1;
        }

        for (SequenceV5 sequence : dependentSequence) {
            long value = sequence.getRealValue();
            minimumSequence = Math.min(minimumSequence, value);
        }

        return minimumSequence;
    }

    public static long getMinimumSequence(List<SequenceV5> dependentSequence){
        return getMinimumSequence(Long.MAX_VALUE,dependentSequence);
    }
}
