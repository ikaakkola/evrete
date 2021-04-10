package org.evrete.runtime;

import org.evrete.api.ActiveField;
import org.evrete.api.FactHandleVersioned;
import org.evrete.api.KeyedFactStorage;
import org.evrete.api.ValueHandle;
import org.evrete.runtime.evaluation.AlphaBucketMeta;

import java.util.Collection;
import java.util.LinkedList;

abstract class KeyMemoryBucket extends MemoryComponent {
    // A convenience fact instance that is never equal to others
    static final RuntimeFact DUMMY_FACT = new RuntimeFact() {
        @Override
        boolean sameValues(RuntimeFact other) {
            return false;
        }
    };
    final KeyedFactStorage fieldData;
    final ActiveField[] activeFields;
    final Collection<FactHandleVersioned> insertData = new LinkedList<>();
    RuntimeFact current = null;

    KeyMemoryBucket(MemoryComponent runtime, FieldsKey typeFields) {
        super(runtime);
        this.fieldData = memoryFactory.newBetaStorage(typeFields.getFields());
        this.activeFields = typeFields.getFields();
    }

    static KeyMemoryBucket factory(MemoryComponent runtime, FieldsKey typeFields, AlphaBucketMeta alphaMask) {
        if (alphaMask.isEmpty()) {
            switch (typeFields.size()) {
                case 0:
                    return new KeyMemoryBucketNoAlpha0(runtime, typeFields);
                case 1:
                    return new KeyMemoryBucketNoAlpha1(runtime, typeFields);
                default:
                    return new KeyMemoryBucketNoAlphaN(runtime, typeFields);
            }
        } else {
            switch (typeFields.size()) {
                case 0:
                    return new KeyMemoryBucketAlpha0(runtime, typeFields, alphaMask);
                case 1:
                    return new KeyMemoryBucketAlpha1(runtime, typeFields, alphaMask);
                default:
                    return new KeyMemoryBucketAlphaN(runtime, typeFields, alphaMask);
            }
        }
    }

    ValueHandle currentFactField(ActiveField field) {
        return current.getValue(field);
    }

    abstract void flushBuffer();

    abstract void insert(Iterable<RuntimeFact> facts);

    @Override
    protected final void clearLocalData() {
        fieldData.clear();
    }

    final KeyedFactStorage getFieldData() {
        return fieldData;
    }

    @Override
    final public void commitChanges() {
        fieldData.commitChanges();
    }

    @Override
    public final String toString() {
        return fieldData.toString();
    }
}
