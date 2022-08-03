package io.artemis.syn;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.artemis.Artemis;
import io.artemis.AxLog;
import io.artemis.AxNames;
import io.artemis.AxRandom;
import io.artemis.skl.ExHandleSkl;
import io.artemis.skl.ForLoopSkl;
import io.artemis.skl.RedirectSkl;
import io.artemis.util.Pair;
import io.artemis.util.Spoons;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.CtAbstractVisitor;
import spoon.reflect.visitor.CtScanner;

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
    public CtStatement synLoop(PPoint pp) {
        Factory fact = mAx.getSpoon().getFactory();

        // Synthesize our raw loop using cb and save reused variables
        Set<CtVariable<?>> reusedSet = new HashSet<>();
        CtBlock<?> rawLoop = synLoopOnly(pp, reusedSet);

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

    private CtBlock<?> synLoopOnly(PPoint pp, Set<CtVariable<?>> reusedSet) {
        CtBlock<?> loop = mAx.getSpoon().getFactory().createBlock();
        CtMethod<?> meth = pp.getMethod();

        // Choose a random code brick to instantiate
        CodeBrick cb = mCbManager.getCodeBrick(mRand.nextInt(mCbManager.getCbCount()));
        AxLog.v("Using code brick: ", (out, ignoreUnused) -> {
            out.println(cb);
        });

        // Find reusable variables and save which inputs should reuse which variable in a map
        CtParameter<?>[] inputs = cb.getInputs();
        List<Pair<CtParameter<?>, CtVariable<?>>> reusedMap = new ArrayList<>(inputs.length);
        for (int i = 0; i < inputs.length; i++) {
            CtParameter<?> inp = inputs[i];
            CtTypeReference<?> inpType = inp.getType();
            if (Spoons.isPrimitiveType(inpType)) {
                List<CtVariable<?>> reusableSet = new ArrayList<>();
                pp.forEachAccVariable(inp.getType(), var -> {
                    // Ensure the same final; cannot use non-static in static environments
                    if (var.isFinal() == inp.isFinal()
                            && (!(var instanceof CtField) || var.isStatic() || !meth.isStatic())) {
                        reusableSet.add(var);
                    }
                });
                if (reusableSet.size() > 0) {
                    CtVariable<?> reusedVar =
                            reusableSet.get(AxRandom.getInstance().nextInt(reusableSet.size()));
                    reusedMap.add(new Pair<>(inp, reusedVar));
                    reusedSet.add(reusedVar);
                    inputs[i] = null;
                }
            }
        }

        // Create declarations for reset inputs that are not to be replaced
        synDecls(inputs).forEach(loop::addStatement);

        // Append the loop with the code brick as body
        loop.addStatement(ForLoopSkl.instantiate(mAx,
                /* iVarName= */ AxNames.getInstance().nextName(),
                /* start= */ -mRand.nextInt(mAx.getMinLoopTrips()), /* step= */ mRand.nextInt(1, 2),
                /* trip= */ mRand.nextInt(mAx.getMinLoopTrips(), mAx.getMaxLoopTrips()),
                /* body= */ cb.getStatements()));

        // Rename variables in the loop by the reused variable. Since the loop hasn't been added
        // to any tree, we cannot use any reliable way (like CtRenameGenericVariableRefactoring) to
        // refactor. Here, let's apply some heuristic rules.
        loop.accept(new CtScanner() {
            @Override
            protected void enter(CtElement e) {
                e.accept(new CtAbstractVisitor() {
                    @Override
                    public <T> void visitCtVariableRead(CtVariableRead<T> vread) {
                        perhapsReplace(vread);
                    }

                    @Override
                    public <T> void visitCtVariableWrite(CtVariableWrite<T> vwrite) {
                        perhapsReplace(vwrite);
                    }

                    private void perhapsReplace(CtVariableAccess<?> vacc) {
                        CtVariableReference<?> var = vacc.getVariable();
                        for (Pair<CtParameter<?>, CtVariable<?>> pair : reusedMap) {
                            // Since the loop is not added, variable read/writes whose declarations
                            // cannot be found are very likely to be our input
                            if (pair.a.getSimpleName().equals(var.getSimpleName())
                                    && var.getDeclaration() == null) {
                                vacc.setVariable((CtVariableReference) pair.b.getReference());
                            }
                        }
                    }
                });
            }
        });

        return loop;
    }

    private List<CtStatement> synDecls(CtVariable<?>[] inputs) {
        Factory fact = mAx.getSpoon().getFactory();
        List<CtStatement> decls = new ArrayList<>();
        for (CtVariable<?> inp : inputs) {
            // Null means this input has been synthesized
            if (inp == null) {
                continue;
            }

            CtTypeReference<?> inpType = inp.getType();
            String inpName = inp.getSimpleName();
            // TODO Call CbManager to reuse initializers (split initializers to many smaller ones)
            CtExpression<?> inpInit = synExpr(inp.getType());

            // TODO Add imports of the used initializer

            decls.add(fact.createLocalVariable(inpType, inpName, (CtExpression) inpInit));
        }

        return decls;
    }

    private CtExpression<?> synExpr(CtTypeReference<?> type) {
        return mNewIns.newInstance(mAx.getSpoon().getFactory(), type);
    }
}
