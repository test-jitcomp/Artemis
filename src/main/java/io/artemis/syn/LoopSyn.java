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
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * A synthesizer which aims to synthesize a (neutral) loop at a given program point. Currently, the
 * synthesizer prefers to synthesize a loop by using many program skeletons (the so-called code
 * brick: class CodeBrick), synthesizing a declaration for each input of the brick, and finally
 * connecting the brick with a loop header. To ensure neutral, it captures all potential exceptions
 * likely to be thrown by the brick and redirect the output (stdout, stderr) to /dev/null.
 */
public class LoopSyn {

    private final Artemis mAx;
    private final AxRandom mRand;
    private final CbManager mCbManager;
    private final NewInstance mNewIns;

    public LoopSyn(Artemis ax, File cbFolder) throws IOException {
        mAx = ax;
        mRand = AxRandom.getInstance();
        mCbManager = new CbManager(cbFolder);
        mCbManager.init();
        mNewIns = new NewInstance();
    }

    /**
     * Synthesize a loop at the given program point.
     * 
     * @param pp Program point
     * @return The synthetic loop
     */
    public CtStatement synLoop(PPoint pp, LoopSkl skl) {
        Factory fact = mAx.getSpoon().getFactory();

        // Synthesize our raw loop using cb and save reused variables
        Set<CtVariable<?>> reusedSet = new HashSet<>();
        CtBlock<?> rawLoop = synMainLoop(pp, skl, reusedSet);

        // Create backups for our reused variables
        List<CtLocalVariable<?>> backupList = new ArrayList<>(reusedSet.size());
        List<CtVariable<?>> reusedList = new ArrayList<>(reusedSet);
        for (CtVariable<?> reusedVar : reusedList) {
            CtLocalVariable<?> local = fact.createLocalVariable(reusedVar.getType().clone(),
                    AxNames.getInstance().nextName(), (CtExpression) fact
                            .createVariableRead(reusedVar.getReference(), reusedVar.isStatic()));
            local.addModifier(ModifierKind.FINAL);
            backupList.add(local);
        }

        // Create restores for our reused variables
        CtBlock<?> restoreList = fact.createBlock();
        for (int i = 0; i < reusedList.size(); i++) {
            CtVariable<?> var = reusedList.get(i);
            CtLocalVariable<?> backup = backupList.get(i);
            restoreList.addStatement(fact.createVariableAssignment(var.getReference(),
                    var.isStatic(), (CtExpression) fact.createVariableRead(backup.getReference(),
                            backup.isStatic())));
        }

        CtBlock<?> finLoop = fact.createBlock();

        // Backup every reused variable
        backupList.forEach(finLoop::addStatement);

        // Catch every possible exceptions to avoid unexpected behavior,
        // and restore values of every reused variables
        finLoop.addStatement(
                ExHandleSkl.instantiate(mAx, /* exName= */ AxNames.getInstance().nextName(),
                        /* tryBlock= */ rawLoop, /* finallyBlock= */ restoreList));

        // Redirect stdout and stderr to avoid unexpected outputs
        return RedirectSkl.instantiate(mAx, /* outBkName= */ AxNames.getInstance().nextName(),
                /* errBkName= */ AxNames.getInstance().nextName(),
                /* newName= */ AxNames.getInstance().nextName(), finLoop);
    }

    private CtBlock<?> synMainLoop(PPoint pp, LoopSkl skl, Set<CtVariable<?>> reusedSet) {
        CtBlock<?> loop = mAx.getSpoon().getFactory().createBlock();

        // Every loop skeleton has many names and blocks that we need to fill.
        // For each name, we give it a random one.
        // For each block, we fill it by instantiate a code brick.
        String[] names = new String[skl.getNamesCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = AxNames.getInstance().nextName();
        }

        CtBlock<?>[] blocks = new CtBlock[skl.getBlockCount()];
        for (int i = 0; i < blocks.length; i++) {
            // Choose a random code brick to instantiate
            CodeBrick cb = mCbManager.getCodeBrick(mRand.nextInt(mCbManager.getCbCount()));
            AxLog.v("Using code brick: ", (out, ignoreUnused) -> {
                out.println(cb);
            });

            // Create declarations for reset inputs that are not to be replaced, of currently cb
            synDecls(pp, cb.unsafeGetInputs(), reusedSet).forEach(loop::addStatement);

            // Append the loop with the code brick as body
            blocks[i] = cb.unsafeGetStatements().clone();
        }

        loop.addStatement(skl.instantiate(mAx, /* start= */ -mRand.nextInt(mAx.getMinLoopTrips()),
                /* step= */ mRand.nextInt(1, 2),
                /* trip= */ mRand.nextInt(mAx.getMinLoopTrips(), mAx.getMaxLoopTrips()),
                /* names= */ names, /* blocks= */ blocks));

        return loop;
    }

    private List<CtStatement> synDecls(PPoint pp, CtParameter<?>[] inputs,
            Set<CtVariable<?>> reusedSet) {
        Factory fact = mAx.getSpoon().getFactory();
        CtMethod<?> meth = pp.getMethod();

        List<CtStatement> decls = new ArrayList<>();
        for (CtVariable<?> inp : inputs) {
            CtTypeReference<?> inpType = inp.getType().clone();
            String inpName = inp.getSimpleName();
            CtExpression<?> inpInit = null;

            // Let's find a reusable variables and replace all occurrences with that variable.
            // We only consider reuse primitive types since reusing references (incl. array) is
            // risky to be implicitly modified by our code brick. Just be careful.
            // We always prefer to reuse existing variables than synthesize a new declaration.
            if (Spoons.isPrimitiveType(inpType)) {
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

    private CtExpression<?> synExpr(CtTypeReference<?> type) {
        return mNewIns.newInstance(mAx.getSpoon().getFactory(), type);
    }
}
