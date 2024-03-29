/**
 * MIT License
 * 
 * Copyright (c) 2021 Cong Li (congli@smail.nju.edu.cn, cong.li@inf.ethz.ch)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.artemis.syn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.artemis.Artemis;
import io.artemis.AxLog;
import io.artemis.AxNames;
import io.artemis.AxRandom;
import io.artemis.skl.ExHandleSkl;
import io.artemis.skl.RedirectSkl;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * A synthesizer which aims to synthesize code, especially (neutral) loops at a given program point.
 * Currently, the synthesizer prefers to synthesize a loop by using many program skeletons (the
 * so-called code brick: class CodeBrick), synthesizing a declaration for each input of the brick,
 * and finally connecting the brick with a loop header. To ensure neutral, it captures all potential
 * exceptions likely to be thrown by the brick and redirect the output (stdout, stderr) to null.
 * 
 * TODO What if the declarations throw any explicit or even implicit RuntimeExceptions?
 */
public class CodeSyn {

    private static final String SYNTHETIC_CODE_KEY = "AX_SYNTHETIC";

    private final Artemis mAx;
    private final AxRandom mRand;
    private final CbManager mCbManager;
    private final NewInstance mNewIns;
    private final Set<Integer> mUsedCb;

    public CodeSyn(Artemis ax, File cbFolder) throws IOException {
        mAx = ax;
        mRand = AxRandom.getInstance();
        mCbManager = new CbManager(cbFolder);
        mCbManager.init();
        mNewIns = new NewInstance();
        mUsedCb = new HashSet<>();
    }

    /**
     * Return whether the given element is synthetic or not
     * 
     * @param ele Element to test
     * @return True if the given element is synthetic; or false.
     */
    public boolean isSyn(CtElement ele) {
        SourcePosition pos = ele.getPosition();
        return !pos.isValidPosition() || mCbManager.isCodeBrick(pos.getFile());
    }

    /**
     * Synthesize a loop at the given program point using the given skeleton.
     * 
     * @param pp Program point
     * @param skl Loop skeleton used to do synthesis
     * @param imp Types that are required to be imported when using the synthetic loop
     * @return The synthetic loop
     */
    public CtStatement synLoop(PPoint pp, LoopSkl skl, List<CtImport> imp) {
        Factory fact = mAx.getSpoon().getFactory();

        // Synthesize our main loop using cb and save reused variables
        Set<CtVariable<?>> reusedSet = new HashSet<>();
        CtBlock<?> mainLoop = synMainLoop(pp, skl, imp, reusedSet);

        // Transfer reused set to a list to enable a 1-1 mapping
        List<CtVariable<?>> reusedList = new ArrayList<>(reusedSet);
        // Create backups for our reused variables
        List<CtLocalVariable<?>> backupList = synBackups(reusedList);
        // Create restores for our reused variables
        List<CtStatement> restoreList = synRestores(reusedList, backupList);

        // Our final loop should be ``backups; try { mainLoop; } finally { restores; }``
        CtBlock<?> finLoop = fact.createBlock();
        backupList.forEach(finLoop::addStatement);
        {
            CtTry loopRestore = fact.createTry();
            loopRestore.setBody(mainLoop);

            CtBlock<?> finalizer = fact.createBlock();
            restoreList.forEach(finalizer::addStatement);
            loopRestore.setFinalizer(finalizer);

            finLoop.addStatement(loopRestore);
        }

        return finLoop;
    }

    /**
     * Synthesize a piece of code segment at given program point
     * 
     * @param pp Program point
     * @param imp Types that are required to be imported when using the synthetic code
     * @return The synthetic code segment
     */
    public CtBlock<?> synCodeSeg(PPoint pp, List<CtImport> imp) {
        CtBlock<?> seg = mAx.getSpoon().getFactory().createBlock();

        // Choose a random code brick to instantiate
        CodeBrick cb = ensureGetUnusedCb();
        AxLog.v("Using CodeBrick#" + cb.getId(), (out, ignoreUnused) -> {
            out.println(cb);
        });

        // Create a declaration for every code brick input
        Set<CtVariable<?>> reusedSet = new HashSet<>();
        synForCbInputs(pp, cb, reusedSet).forEach(seg::addStatement);

        // Add the code brick as body
        synForCbStmts(cb).forEach(seg::addStatement);

        // Add backups and restores
        List<CtVariable<?>> reusedList = new ArrayList<>(reusedSet);
        List<CtLocalVariable<?>> backupList = synBackups(reusedList);
        backupList.forEach(seg::insertBegin);
        List<CtStatement> restoreList = synRestores(reusedList, backupList);
        restoreList.forEach(seg::addStatement);

        // Append imports to imp
        cb.unsafeGetImports().forEach(e -> imp.add(e.clone()));

        return seg;
    }

    /**
     * Synthesize an expression of the given type
     * 
     * @param type Type of the expression
     * @return The synthetic expression
     */
    public CtExpression<?> synExpr(CtTypeReference<?> type) {
        return mNewIns.newInstance(mAx.getSpoon().getFactory(), type);
    }

    private CtBlock<?> synMainLoop(PPoint pp, LoopSkl skl, List<CtImport> typesToImport,
            Set<CtVariable<?>> reusedSet) {
        CtBlock<?> loop = mAx.getSpoon().getFactory().createBlock();

        // For each name of the skeleton, we give it a random one.
        String[] names = new String[skl.getNamesCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = AxNames.getInstance().nextName();
        }

        // For each block of the skeleton, we fill it by instantiating a code brick.
        CtBlock<?>[] blocks = new CtBlock[skl.getBlockCount()];
        for (int i = 0; i < blocks.length; i++) {
            // Choose a random code brick to instantiate
            CodeBrick cb = ensureGetUnusedCb();
            AxLog.v("Using CodeBrick#" + cb.getId(), (out, ignoreUnused) -> {
                out.println(cb);
            });

            // Create a declaration for every code brick input
            synForCbInputs(pp, cb, reusedSet).forEach(loop::addStatement);

            // Append the loop with the code brick as body
            blocks[i] = mAx.getSpoon().getFactory().createBlock();
            synForCbStmts(cb).forEach(blocks[i]::addStatement);

            // Append required imports
            cb.unsafeGetImports().forEach(e -> typesToImport.add(e.clone()));
        }

        // Instantiate the loop skeleton with above names and blocks
        // TODO Reuse existing variables and initializers on start/step/trip
        Spoons.flat(skl.instantiate(mAx, /* start= */ -mRand.nextInt(mAx.getMinLoopTrips()),
                /* step= */ mRand.nextInt(1, 2),
                /* trip= */ mRand.nextInt(mAx.getMinLoopTrips(), mAx.getMaxLoopTrips()),
                /* names= */ names, /* blocks= */ blocks)).forEach(loop::addStatement);

        return loop;
    }

    private List<CtLocalVariable<?>> synBackups(List<CtVariable<?>> reusedList) {
        Factory fact = mAx.getSpoon().getFactory();;
        List<CtLocalVariable<?>> backupList = new ArrayList<>(reusedList.size());
        // Create a backup for each var in reusedList, 1-1 mapping
        for (CtVariable<?> reusedVar : reusedList) {
            CtLocalVariable<?> local = fact.createLocalVariable(reusedVar.getType().clone(),
                    AxNames.getInstance().nextName(), (CtExpression) fact
                            .createVariableRead(reusedVar.getReference(), reusedVar.isStatic()));
            local.addModifier(ModifierKind.FINAL);
            backupList.add(local);
        }
        return backupList;
    }

    private List<CtStatement> synRestores(List<CtVariable<?>> reusedList,
            List<CtLocalVariable<?>> backupList) {
        Factory fact = mAx.getSpoon().getFactory();
        List<CtStatement> restoreList = new ArrayList<>(reusedList.size());
        for (int i = 0; i < reusedList.size(); i++) {
            // reusedList and backupList maintains a 1-1 mapping
            CtVariable<?> var = reusedList.get(i);
            CtLocalVariable<?> backup = backupList.get(i);
            restoreList.add(fact.createVariableAssignment(var.getReference(), var.isStatic(),
                    (CtExpression) fact.createVariableRead(backup.getReference(),
                            backup.isStatic())));
        }
        return restoreList;
    }

    private List<CtStatement> synForCbInputs(PPoint pp, CodeBrick cb,
            Set<CtVariable<?>> reusedSet) {
        Factory fact = mAx.getSpoon().getFactory();
        CtMethod<?> meth = pp.getMethod();
        CtParameter<?>[] inputs = cb.unsafeGetInputs();

        List<CtStatement> decls = new ArrayList<>();
        for (CtVariable<?> inp : inputs) {
            CtTypeReference<?> inpType = inp.getType().clone();
            String inpName = inp.getSimpleName();
            CtExpression<?> inpInit = null;

            // Let's find a reusable variables and replace all occurrences with that variable.
            // We only consider reuse primitive types since reusing references (incl. array) is
            // risky to be implicitly modified by our code brick. Just be careful.
            // We always prefer to reuse existing variables than synthesize a new declaration.
            if (Spoons.isPrimitiveAlikeType(inpType)) {
                // We never use variables that are accessed by current program point's statements
                // because it is likely that we change the semantics. For example, when wrapping a
                // statement a = b + c, if our brick assigns b a new value, then a's is changed.
                Set<CtVariable<?>> stmtUsingVarSet = pp.getStatement()
                        .getElements(new TypeFilter<>(CtVariableAccess.class)).stream()
                        .map(vacc -> (CtVariable<?>) vacc.getVariable().getDeclaration())
                        .collect(Collectors.toSet());
                // TODO Cache and don't reuse again if already reused
                List<CtVariable<?>> reusableSet = new ArrayList<>();
                pp.forEachAccVariable(inpType, var -> {
                    // Ensure the same final; cannot use non-static in static environments
                    if (var.isFinal() == inp.isFinal()
                            && (!(var instanceof CtField) || var.isStatic() || !meth.isStatic())
                            && !stmtUsingVarSet.contains(var)) {
                        reusableSet.add(var);
                    }
                });
                if (reusableSet.size() > 0) {
                    // Randomly select a variable, and rename all input occurrences
                    CtVariable<?> reusedVar =
                            reusableSet.get(AxRandom.getInstance().nextInt(reusableSet.size()));
                    AxLog.v("Reuse existing variable " + reusedVar + " to fill input " + inp);
                    Spoons.renameVariable(inp, reusedVar.getSimpleName());
                    reusedSet.add(reusedVar);
                    continue;
                }
            }

            // If there's no reusable variables, let's try to find an existing initializer.
            // We don't always use initializers, let's flip a coin to introduce some randomness.
            if (AxRandom.getInstance().nextFloat() > 0.5f) {
                List<CtExpression<?>> reusableInitzSet = new ArrayList<>();
                mCbManager.forEachInitz(inpType, reusableInitzSet::add);
                if (reusableInitzSet.size() > 0) {
                    inpInit = reusableInitzSet
                            .get(AxRandom.getInstance().nextInt(reusableInitzSet.size())).clone();
                    AxLog.v("Reuse existing initializer " + inpInit + " to fill input " + inp);
                }
            }

            // There's no initializers, either. Let's compromise to decl synthesis.
            if (inpInit == null) {
                inpInit = synExpr(inpType);
                AxLog.v("Synthesized an initializer " + inpInit + " to fill input " + inp);
            }

            // It's okay if inpInit is still null.
            decls.add(fact.createLocalVariable(inpType, inpName, (CtExpression) inpInit));
        }

        return decls;
    }

    private List<CtStatement> synForCbStmts(CodeBrick cb) {
        // The very raw block is the code brick
        CtBlock<?> blk = cb.unsafeGetStatements().clone();

        // Wrap it with a try-catch to avoid unexpected exceptions from the brick
        blk = ExHandleSkl.instantiate(mAx, /* exName= */ AxNames.getInstance().nextName(),
                /* tryBlock= */ blk);

        // Redirect stdout and stderr to avoid unexpected outputs and recover afterwards
        CtClass<?> rhClass = ensureRhSynOnce();
        blk.insertBegin(RedirectSkl.callRedirect(mAx, rhClass));
        blk.insertEnd(RedirectSkl.callRecover(mAx, rhClass));

        // Let's peel every statement from the block and return parent-uninitialized ones
        return Spoons.flat(blk);
    }

    private CtClass<?> ensureRhSynOnce() {
        CtClass<?> rhClass = mAx.getSpoon().getFactory().Class().get("AxOutputRedirectionHelper");
        if (rhClass == null) {
            rhClass = RedirectSkl.instantiate(mAx, "AxOutputRedirectionHelper");
            rhClass.addModifier(ModifierKind.PUBLIC);
            rhClass.addModifier(ModifierKind.STATIC);
            rhClass.addModifier(ModifierKind.FINAL);
            mAx.getTestClass().addNestedType(rhClass);
        }
        return rhClass;
    }

    private CodeBrick ensureGetUnusedCb() {
        int cbCount = mCbManager.getCbCount();
        CodeBrick brick = null;
        while (brick == null || mUsedCb.contains(brick.getId())) {
            brick = mCbManager.getCodeBrick(mRand.nextInt(cbCount));
        }
        mUsedCb.add(brick.getId());
        return brick;
    }
}
