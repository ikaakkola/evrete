package org.evrete.runtime.memory;

import org.evrete.api.*;
import org.evrete.collections.ArrayOf;
import org.evrete.runtime.PlainMemory;
import org.evrete.runtime.RuntimeFactImpl;
import org.evrete.runtime.evaluation.AlphaBucketMeta;
import org.evrete.runtime.evaluation.AlphaConditions;
import org.evrete.runtime.evaluation.AlphaDelta;
import org.evrete.runtime.evaluation.AlphaEvaluator;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class TypeMemory extends TypeMemoryBase {
    private static final Logger LOGGER = Logger.getLogger(TypeMemory.class.getName());
    private final AlphaConditions alphaConditions;
    private final Map<FieldsKey, FieldsMemory> betaMemories = new HashMap<>();
    private final ArrayOf<TypeMemoryBucket> alphaBuckets;

    //private final ActionQueue<RuntimeFact> inputBuffer = new ActionQueue<>();
    private final EnumMap<Action, SharedPlainFactStorage> inputBuffer = new EnumMap<>(Action.class);
    //private final SharedPlainFactStorage existingObjects1;

    TypeMemory(SessionMemory runtime, Type<?> type) {
        super(runtime, type);
        //this.existingObjects1 = runtime.newSharedPlainStorage();
        for (Action action : Action.values()) {
            this.inputBuffer.put(action, runtime.newSharedPlainStorage());
        }
        this.alphaConditions = runtime.getAlphaConditions();
        this.alphaBuckets = new ArrayOf<>(new TypeMemoryBucket[]{new TypeMemoryBucket(runtime, AlphaBucketMeta.NO_FIELDS_NO_CONDITIONS)});
    }

    public final Set<FieldsKey> knownFieldSets() {
        return Collections.unmodifiableSet(betaMemories.keySet());
    }

    public boolean hasMemoryChanges() {
        assert inputBuffer.get(Action.UPDATE).size() == 0;
        return hasMemoryChanges(Action.INSERT) || hasMemoryChanges(Action.RETRACT);
    }

    public boolean hasMemoryChanges(Action action) {
        return inputBuffer.get(action).size() > 0;
    }

    public void propagateBetaDeltas() {
        SharedPlainFactStorage inserts = inputBuffer.get(Action.INSERT);

        for (TypeMemoryBucket bucket : alphaBuckets.data) {
            bucket.insert(inserts);
        }

        for (FieldsMemory fm : betaMemories.values()) {
            fm.insert(inputBuffer.get(Action.INSERT));
        }

        inserts.clear();
    }

    public RuntimeFact doAction(Action action, Object o) {
        switch (action) {
            case INSERT:
                return doInsert(o);
            case RETRACT:
                return doDelete(o);
            case UPDATE:
                // Update is a sequence of delete and insert operation
                RuntimeFact fact = doDelete(o);
                if (fact == null) {
                    LOGGER.warning("Unknown object: " + o + ", update skipped....");
                    return null;
                } else {
                    return doInsert(o);
                }
            default:
                throw new IllegalStateException();

        }
    }

    private RuntimeFact doInsert(Object o) {


        RuntimeFact fact = create(o);

        inputBuffer.get(Action.INSERT).insert(fact);
        //System.out.println("\tnew handle created: " + fact + ", total: " + inputBuffer.get(Action.INSERT));
        return fact;

/*
        fact = main0().find(o);
        if (fact != null && !fact.isDeleted()) {
            LOGGER.warning("Object " + o + " has been already inserted, skipping...");
        } else {
            // No such fact in main data, checking insert buffer
            SharedPlainFactStorage collection = inputBuffer.get(Action.INSERT);
            fact = collection.find(o);
            if (fact != null && !fact.isDeleted()) {
                LOGGER.warning("Object " + o + " has been already inserted, skipping...");
            } else {
                // The fact is new to the memory, it needs to be saved in the INSERT buffer
                fact = create(o);
                System.out.println("\tnew handle created: " + fact);
                collection.insert(fact);
            }
        }
        return fact;
*/
    }

    private RuntimeFact doDelete(Object o) {
        // Checking the main storage
        //RuntimeFact fact = main0().find(o);
        RuntimeFact fact = find(o);
        if (fact == null) {
            // TODO do we really need to look in the insert buffer?
            // Not found, looking in the insert buffer
/*
            fact = inputBuffer.get(Action.INSERT).find(o);
            if (fact != null && !fact.isDeleted()) {
                inputBuffer.get(Action.INSERT).delete(fact);
                return fact;
            }
*/
        } else {
            inputBuffer.get(Action.RETRACT).insert(fact);
            return fact;
        }
        LOGGER.warning("Object " + o + " hasn't been previously inserted");
        return null;
    }


    RuntimeFact find(Object o) {
        RuntimeFact fact = main0().find(o);
        if (fact == null) {
            fact = delta0().find(o);
        }
        return fact;
    }


    void clear() {
        //main1().clear(); // TODO remove!!!!
        for (TypeMemoryBucket bucket : alphaBuckets.data) {
            bucket.clear();
        }

        for (FieldsMemory fm : betaMemories.values()) {
            fm.clear();
        }
        inputBuffer.clear();
    }

    public final FieldsMemory get(FieldsKey fields) {
        FieldsMemory fm = betaMemories.get(fields);
        if (fm == null) {
            throw new IllegalArgumentException("No key memory exists for " + fields);
        } else {
            return fm;
        }
    }

    public void commitDeltas() {
        for (TypeMemoryBucket bucket : this.alphaBuckets.data) {
            bucket.commitDelta();
        }

        for (FieldsMemory fm : betaMemories.values()) {
            fm.commitDeltas();
        }
    }

    public String reportStatus() {

        String s = "\n\t\t" + type.getJavaType().getSimpleName() + "\n";
        s += "\t\t\tbuffer: " + inputBuffer;
        s += "\n\t\t\tmain:   " + main0();
        s += "\n\t\t\tdelta:  " + delta0();
        s += "\n\t\t\tkey memory:";
        for (Map.Entry<FieldsKey, FieldsMemory> e : betaMemories.entrySet()) {
            s += "\n\t\t\t\tkey:" + e.getKey();
            s += "\n\t\t\t\tval:" + e.getValue();

        }

        return s;
    }


    //@Override
    public void commitChanges() {
        // Append insert buffer to main storage
        main0().insert(inputBuffer.get(Action.INSERT));

        for (TypeMemoryBucket bucket : alphaBuckets.data) {
            bucket.commitChanges();
        }

        for (SharedPlainFactStorage actions : inputBuffer.values()) {
            actions.clear();
        }
    }

    public void performDelete() {
        SharedPlainFactStorage deleteSubject = inputBuffer.get(Action.RETRACT);

        // Step 1: Marking facts as deleted
        ReIterator<RuntimeFact> it = deleteSubject.iterator();
        while (it.hasNext()) {
            RuntimeFactImpl impl = (RuntimeFactImpl) it.next();
            impl.setDeleted(true);
        }

        for (TypeMemoryBucket bucket : alphaBuckets.data) {
            bucket.retract(deleteSubject);
        }

        for (FieldsMemory fm : betaMemories.values()) {
            fm.retract(deleteSubject);
        }
        // Step 3: clear the delete buffer
        deleteSubject.clear();
    }

    public PlainMemory get(AlphaBucketMeta alphaMask) {
        return alphaBuckets.getChecked(alphaMask.getBucketIndex());
    }

    void touchMemory(FieldsKey key, AlphaBucketMeta alphaMeta) {
        if (key.size() == 0) {
            touchAlphaMemory(alphaMeta);
        } else {
            betaMemories
                    .computeIfAbsent(key, k -> new FieldsMemory(getRuntime(), key))
                    .touchMemory(alphaMeta);
        }
    }

    private TypeMemoryBucket touchAlphaMemory(AlphaBucketMeta alphaMeta) {
        // Alpha storage
        if (!alphaMeta.isEmpty()) {
            int bucketIndex = alphaMeta.getBucketIndex();
            if (alphaBuckets.isEmptyAt(bucketIndex)) {
                TypeMemoryBucket newBucket = new TypeMemoryBucket(getRuntime(), alphaMeta);
                alphaBuckets.set(bucketIndex, newBucket);
                return newBucket;
            }
        }
        return null;
    }

    void onNewAlphaBucket(AlphaDelta delta) {

        if (inputBuffer.get(Action.INSERT).size() > 0) {
            //TODO develop a strategy
            throw new UnsupportedOperationException("A new condition was created in an uncommitted memory.");
        }

        ReIterator<RuntimeFact> existingFacts = main0().iterator();
        // 1. Update all the facts by applying new alpha flags
        AlphaEvaluator[] newEvaluators = delta.getNewEvaluators();
        if (newEvaluators.length > 0 && existingFacts.reset() > 0) {
            while (existingFacts.hasNext()) {
                RuntimeFactImpl fact = (RuntimeFactImpl) existingFacts.next();

                fact.appendAlphaTest(newEvaluators);
            }
        }


        // 2. Create and fill buckets
        FieldsKey key = delta.getKey();
        AlphaBucketMeta alphaMeta = delta.getNewAlphaMeta();
        if (key.size() == 0) {
            // 3. Create new alpha data bucket
            TypeMemoryBucket newBucket = touchAlphaMemory(alphaMeta);
            assert newBucket != null;
            // Fill data
            newBucket.fillMainStorage(existingFacts);
        } else {
            // 3. Process keyed/beta-memory
            betaMemories
                    .computeIfAbsent(key, k -> new FieldsMemory(getRuntime(), key))
                    .onNewAlphaBucket(alphaMeta, existingFacts);
        }

        this.cachedAlphaEvaluators = alphaConditions.getPredicates(type).data;
    }

    final <T> void forEachMemoryObject(Consumer<T> consumer) {
        main0().iterator().forEachRemaining(fact -> {
            if (!fact.isDeleted()) {
                consumer.accept(fact.getDelegate());
            } else {
                //TODO !!!! clear
                //System.out.println("*** " + fact);
            }
        });
    }

    final void forEachObjectUnchecked(Consumer<Object> consumer) {
        main0().iterator().forEachRemaining(fact -> {
            if (!fact.isDeleted()) {
                consumer.accept(fact.getDelegate());
            } else {
                //TODO !!!! clear
                //System.out.println("*** " + fact);
            }
        });
    }


    private SharedPlainFactStorage main0() {
        return alphaBuckets.data[0].getData();
    }

    private SharedPlainFactStorage delta0() {
        return alphaBuckets.data[0].getDelta();
    }

    private RuntimeFactImpl create(Object o) {
        // Read values
        Object[] values = new Object[cachedActiveFields.length];
        for (int i = 0; i < cachedActiveFields.length; i++) {
            values[i] = cachedActiveFields[i].readValue(o);
        }

        // Evaluate alpha conditions if necessary
        if (cachedAlphaEvaluators.length > 0) {
            boolean[] alphaTests = new boolean[cachedAlphaEvaluators.length];
            for (AlphaEvaluator alpha : cachedAlphaEvaluators) {
                int fieldInUseIndex = alpha.getValueIndex();
                alphaTests[alpha.getUniqueId()] = alpha.test(values[fieldInUseIndex]);
            }
            return RuntimeFactImpl.factory(o, values, alphaTests);
        } else {
            return RuntimeFactImpl.factory(o, values);
        }
    }

    /**
     * <p>
     * Modifies existing facts by appending value of the newly
     * created field
     * </p>
     *
     * @param newField newly created field
     */
    final void onNewActiveField(ActiveField newField) {
        for (SharedPlainFactStorage storage : new SharedPlainFactStorage[]{main0(), delta0()}) {
            ReIterator<RuntimeFact> it = storage.iterator();
            while (it.hasNext()) {
                RuntimeFactImpl rto = (RuntimeFactImpl) it.next();
                Object fieldValue = newField.readValue(rto.getDelegate());
                rto.appendValue(newField, fieldValue);
            }

        }
        this.cachedActiveFields = getRuntime().getActiveFields(type);
    }


    @Override
    public String toString() {
        return "TypeMemory{" +
                "alphaBuckets=" + alphaBuckets +
                '}';
    }
}
