package disruptor.v3.util;

import disruptor.v3.SequenceV3;

import java.util.List;

public class SequenceUtilV3 {

    public static long getMinimumSequence(long minimumSequence, List<SequenceV3> dependentSequence){
        if(dependentSequence.isEmpty()){
            return -1;
        }

        for (SequenceV3 sequence : dependentSequence) {
            long value = sequence.getRealValue();
            minimumSequence = Math.min(minimumSequence, value);
        }

        return minimumSequence;
    }

    public static long getMinimumSequence(List<SequenceV3> dependentSequence){
        return getMinimumSequence(Long.MAX_VALUE,dependentSequence);
    }
}
