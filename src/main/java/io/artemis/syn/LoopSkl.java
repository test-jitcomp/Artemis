package io.artemis.syn;

import io.artemis.Artemis;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.template.Local;

/**
 * AbsLoopSkl is the abstract loop skeleton that every loop skeleton needs to implement. A loop
 * skeleton should only leave names and blocks for our synthesizer LoopSyn to synthesize. Any other
 * are disallowed. The subclass should tell synthesizer how many blocks and names should be
 * synthesized and give the exactly same number of names and blocks when instantiating the skeleton.
 */
public interface LoopSkl {

    @Local
    int getBlockCount();

    @Local
    int getNamesCount();

    @Local
    CtStatement instantiate(Artemis ax, int start, int step, int trip, String[] names,
            CtBlock<?>[] blocks);
}
